package softnet.client;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.UUID;

import softnet.*;
import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;
import softnet.utils.ByteConverter;

class UDPController
{
	private ClientEndpoint clientEndpoint;
	private ThreadPool threadPool;
	private Scheduler scheduler;
	private Object mutex;
	private Channel channel;
	private LinkedList<UdpRequest> requestList;

	private enum StatusEnum
	{ 
		Disconnected, Connected, Online
	}
	private StatusEnum clientStatus = StatusEnum.Disconnected;
	
	public UDPController(ClientEndpoint clientEndpoint)
	{
		this.clientEndpoint = clientEndpoint;
		this.threadPool = clientEndpoint.threadPool;
		this.scheduler = clientEndpoint.scheduler;
		requestList = new LinkedList<UdpRequest>();
		mutex = new Object();
	}
	
	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Client.UdpController.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					onMessageReceived(message, _channel);
				}
			});		
		
		synchronized(mutex)
		{
			clientStatus = StatusEnum.Connected;
			this.channel = channel;
		}
	}
	
	public void onClientOnline()
	{
		synchronized(mutex)
		{
			clientStatus = StatusEnum.Online;
		}
	}
	
	public void onEndpointDisconnected()
	{
		synchronized(mutex)
		{
			clientStatus = StatusEnum.Disconnected;
			channel = null;
			
			for(UdpRequest request: requestList)
			{
				request.timeoutControlTask.cancel();
				
				if(request.udpConnector != null)
					request.udpConnector.abort();
				
				final UdpRequest f_request = request;				
				Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						f_request.responseHandler.onError(new ResponseContext(clientEndpoint, f_request.remoteService, f_request.attachment), new ClientOfflineSoftnetException());
					}
				};
				threadPool.execute(runnable);				
			}
			requestList.clear();
		}	
	}
	
	public void onEndpointClosed()
	{
		synchronized(mutex)
		{
			clientStatus = StatusEnum.Disconnected;

			for(UdpRequest request: requestList)
			{
				request.timeoutControlTask.cancel();				
				if(request.udpConnector != null)
					request.udpConnector.abort();
			}
			requestList.clear();
		}	
	}

	public void onRemoteServiceOffline(long serviceId, Channel channel)
	{
		synchronized(mutex)
		{
			if(channel.closed())
				return;

			for(int i = requestList.size()-1; i >= 0; i--)
			{
				UdpRequest request = requestList.get(i);
				if(request.remoteService.getId() == serviceId)
				{
					requestList.remove(i);
					
					request.timeoutControlTask.cancel();
					if(request.udpConnector != null)
						request.udpConnector.abort();
						
					final UdpRequest f_request = request;
					Runnable runnable = new Runnable()
					{
						@Override
						public void run()
						{
							f_request.responseHandler.onError(new ResponseContext(clientEndpoint, f_request.remoteService, f_request.attachment), new ServiceOfflineSoftnetException());
						}
					};
					threadPool.execute(runnable);
				}
			}			
		}
	}
	
	public void connect(RemoteService remoteService, int virtualPort, UDPResponseHandler responseHandler, Object attachment)
	{
		try
		{			
			if(remoteService.isOnline() == false)
				throw new ServiceOfflineSoftnetException();
			
			synchronized(mutex)
			{
				if(clientStatus != StatusEnum.Online)
					throw new ClientOfflineSoftnetException();
				
				UUID requestUid = UUID.randomUUID();
				
				UdpRequest request = new UdpRequest(requestUid);
				request.remoteService = remoteService;
				request.virtualPort = virtualPort;
				request.responseHandler = responseHandler;
				request.attachment = attachment;								
				Acceptor<Object> acceptor = new Acceptor<Object>()
				{
					public void accept(Object state) { onConnectionAttemptTimedOut(state); }
				};
				request.timeoutControlTask = new ScheduledTask(acceptor, request);
				requestList.add(request);
				
				ASNEncoder asnEncoder = new ASNEncoder();
				SequenceEncoder rootSequence = asnEncoder.Sequence();
				rootSequence.OctetString(requestUid);
				rootSequence.Int64(remoteService.getId());
				rootSequence.Int32(virtualPort);
				SoftnetMessage message = MsgBuilder.Create(Constants.Client.UdpController.ModuleId, Constants.Client.UdpController.REQUEST, asnEncoder);
				
				channel.send(message);
				scheduler.add(request.timeoutControlTask, Constants.UdpConnectingWaitSeconds);
			}
		}
		catch(SoftnetException ex)
		{
			responseHandler.onError(new ResponseContext(clientEndpoint, remoteService, attachment), ex);
		}
	}
	
	private void onConnectionAttemptTimedOut(Object state)
	{
		UdpRequest request = (UdpRequest)state;
		synchronized(mutex)
		{
			if(requestList.remove(request) == false)
				return;
		}
		
		if(request.udpConnector != null)
			request.udpConnector.abort();

		request.responseHandler.onError(new ResponseContext(clientEndpoint, request.remoteService, request.attachment), new TimeoutExpiredSoftnetException("The connection attempt timed out."));		
	}
	
	private void processMessage_RzvData(byte[] message, Channel channel) throws AsnException, FormatException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
		UUID requestUid = asnRootSequence.OctetStringToUUID();
		byte[] connectionUid = asnRootSequence.OctetString(16);
		int serverId = asnRootSequence.Int32();
		byte[] serverIpBytes = asnRootSequence.OctetString();
		asnRootSequence.end();
		
		InetAddress serverIp = ByteConverter.toInetAddress(serverIpBytes);
		
		UdpRequest request = null;
		synchronized(mutex)
		{
			if(channel.closed())
				return;
			
			request = findRequest(requestUid);		
			if(request == null)
				return;
			
			request.serverId = serverId;
			if(serverIp instanceof Inet6Address)
			{
				request.udpConnector = new UDPConnectorV6(connectionUid, serverIp, scheduler);
			}
			else
			{
				request.udpConnector = new UDPConnectorV4(connectionUid, serverIp, scheduler);
			}
		}
		
		request.udpConnector.connect(new UDPResponseHandler()
		{
			@Override
			public void onSuccess(ResponseContext context, java.net.DatagramSocket datagramSocket, java.net.InetSocketAddress remoteSocketAddress, ConnectionMode mode)
			{
				onUdpConnectorSuccess(datagramSocket, remoteSocketAddress, mode, (UdpRequest)context.attachment);
			}

			@Override
			public void onError(ResponseContext context, SoftnetException exception)
			{
				onUdpConnectorError(exception, (UdpRequest)context.attachment);
			}
		},
		new BiAcceptor<byte[], Object>()
		{
			@Override
			public void accept(byte[] authKey, Object attachment)
			{
				sendAuthenticationKey(authKey, attachment);
			}
		}, request);
	}	

	private void sendAuthenticationKey(byte[] authKey, Object attachment)
	{
		UdpRequest request = (UdpRequest)attachment;
		synchronized(mutex)
		{
			if(clientStatus != StatusEnum.Online)
				return;
			channel.send(encodeMessage_AuthKey(request.requestUid, request.serverId, authKey));
		}
	}
	
	private void onUdpConnectorSuccess(java.net.DatagramSocket datagramSocket, java.net.InetSocketAddress remoteSocketAddress, ConnectionMode mode, final UdpRequest request)
	{
		synchronized(mutex)
		{
			if(requestList.remove(request) == false)
				return;
		}				
		request.timeoutControlTask.cancel();
		request.responseHandler.onSuccess(new ResponseContext(clientEndpoint, request.remoteService, request.attachment), datagramSocket, remoteSocketAddress, mode);		
	}
	
	private void onUdpConnectorError(SoftnetException exception, final UdpRequest request)
	{
		synchronized(mutex)
		{
			if(requestList.remove(request) == false)
				return;
		}						
		request.timeoutControlTask.cancel();
		request.responseHandler.onError(new ResponseContext(clientEndpoint, request.remoteService, request.attachment), exception);
	}
	
	private void processMessage_RequestError(byte[] message, Channel channel) throws AsnException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
		UUID requestUid = asnRootSequence.OctetStringToUUID();
		int errorCode = asnRootSequence.Int32();
		asnRootSequence.end();
		
		UdpRequest request = null;
		synchronized(mutex)
		{
			if(channel.closed())
				return;
			request = removeRequest(requestUid);			
			if(request == null)
				return;
		}				
		
		request.timeoutControlTask.cancel();
		
		final SoftnetException f_exception = resolveError(request, errorCode);
		final UdpRequest f_request = request;
		Runnable runnable = new Runnable()
		{
			@Override
			public void run()
			{
				f_request.responseHandler.onError(new ResponseContext(clientEndpoint, f_request.remoteService, f_request.attachment), f_exception);
			}
		};
		threadPool.execute(runnable);			
	}

	private void processMessage_AuthHash(byte[] message, Channel channel) throws AsnException
	{
		SequenceDecoder asnSequence = ASNDecoder.Sequence(message, 2);
		UUID requestUid = asnSequence.OctetStringToUUID();
		byte[] authHash = asnSequence.OctetString(20);
		byte[] authKey2 = asnSequence.OctetString(20);
		asnSequence.end();
		
		UdpRequest request = null;
		synchronized(mutex)
		{
			if(channel.closed())
				return;
			request = findRequest(requestUid);		
			if(request == null)
				return;
		}		
		request.udpConnector.onAuthenticationHash(authHash, authKey2);
	}
	
	private void processMessage_AuthError(byte[] message, Channel channel) throws AsnException
	{
		SequenceDecoder asnSequence = ASNDecoder.Sequence(message, 2);
		UUID requestUid = asnSequence.OctetStringToUUID();
		asnSequence.end();
		
		UdpRequest request = null;
		synchronized(mutex)
		{
			if(channel.closed())
				return;
			request = removeRequest(requestUid);			
			if(request == null)
				return;
		}
		
		request.timeoutControlTask.cancel();
		
		if(request.udpConnector != null)
			request.udpConnector.abort();

		final UdpRequest f_request = request;
		Runnable runnable = new Runnable()
		{
			@Override
			public void run()
			{
				f_request.responseHandler.onError(new ResponseContext(clientEndpoint, f_request.remoteService, f_request.attachment), new ConnectionAttemptFailedSoftnetException());
			}
		};
		threadPool.execute(runnable);			
	}

	private SoftnetMessage encodeMessage_AuthKey(UUID requestUid, int serverId, byte[] authKey)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.OctetString(requestUid);
        asnSequence.Int32(serverId);
        asnSequence.OctetString(authKey);
        return MsgBuilder.Create(Constants.Client.UdpController.ModuleId, Constants.Client.UdpController.AUTH_KEY, asnEncoder);
	}
	
	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException
	{
		byte messageTag = message[1];
		if(messageTag == Constants.Client.UdpController.RZV_DATA)
		{
			processMessage_RzvData(message, channel);
		}
		else if(messageTag == Constants.Client.UdpController.REQUEST_ERROR)
		{
			processMessage_RequestError(message, channel);
		}
		else if(messageTag == Constants.Client.UdpController.AUTH_HASH)
		{
			processMessage_AuthHash(message, channel);
		}
		else if(messageTag == Constants.Client.UdpController.AUTH_ERROR)
		{
			processMessage_AuthError(message, channel);
		}
		else
			throw new FormatException();
	}
	
	private SoftnetException resolveError(UdpRequest udpRequest, int errorCode)
	{
		if(errorCode == ErrorCodes.CONNECTION_ATTEMPT_FAILED)
			return new ConnectionAttemptFailedSoftnetException();	
		if(errorCode == ErrorCodes.SERVICE_OFFLINE)
			return new ServiceOfflineSoftnetException();			
		if(errorCode == ErrorCodes.PORT_UNREACHABLE)
			return new PortUnreachableSoftnetException(udpRequest.virtualPort);
		if(errorCode == ErrorCodes.ACCESS_DENIED)
			return new AccessDeniedSoftnetException();	
		if(errorCode == ErrorCodes.SERVICE_BUSY)
			return new ServiceBusySoftnetException();	
		return new UnexpectedErrorSoftnetException(errorCode);
	}
	
	private UdpRequest findRequest(UUID requestUid)
	{
		for(UdpRequest request: requestList)
		{
			if(request.requestUid.equals(requestUid))
				return request;
		}
		return null;
	}
	
	private UdpRequest removeRequest(UUID requestUid)
	{
		for(UdpRequest request: requestList)
		{
			if(request.requestUid.equals(requestUid))
			{
				requestList.remove(request);
				return request;
			}
		}
		return null;
	}
	
	private class UdpRequest
	{
		public final UUID requestUid;
		public RemoteService remoteService;
		public int virtualPort;
		public UDPResponseHandler responseHandler;
		public Object attachment;
		public int serverId;
		public UDPConnector udpConnector;
		public ScheduledTask timeoutControlTask;
		
		public UdpRequest(UUID requestUid)
		{
			this.requestUid = requestUid;
			this.udpConnector = null;
		}
	}
}
