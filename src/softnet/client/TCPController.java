package softnet.client;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.UUID;

import softnet.*;
import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;
import softnet.utils.ByteConverter;

class TCPController 
{
	private ClientEndpoint clientEndpoint;
	private ThreadPool threadPool;
	private Scheduler scheduler;
	private Object mutex;
	private Channel channel;
	private LinkedList<TcpRequest> requestList;

	private enum StatusEnum
	{ 
		Disconnected, Connected, Online
	}
	private StatusEnum clientStatus = StatusEnum.Disconnected;

	public TCPController(ClientEndpoint clientEndpoint)
	{
		this.clientEndpoint = clientEndpoint;
		this.threadPool = clientEndpoint.threadPool;
		this.scheduler = clientEndpoint.scheduler;
		requestList = new LinkedList<TcpRequest>();
		mutex = new Object();
	}

	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Client.TcpController.ModuleId, 
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
			
			for(TcpRequest request: requestList)
			{
				request.timeoutControlTask.cancel();
				
				if(request.tcpConnector != null)
					request.tcpConnector.abort();
				
				final TcpRequest f_request = request;				
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

			for(TcpRequest request: requestList)
			{
				request.timeoutControlTask.cancel();				
				if(request.tcpConnector != null)
					request.tcpConnector.abort();
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
				TcpRequest request = requestList.get(i);
				if(request.remoteService.getId() == serviceId)
				{
					requestList.remove(i);
					
					request.timeoutControlTask.cancel();
					
					if(request.tcpConnector != null)
						request.tcpConnector.abort();
						
					final TcpRequest f_request = request;
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
	
	public void connect(RemoteService remoteService, int virtualPort, TCPOptions tcpOptions, TCPResponseHandler responseHandler, Object attachment)
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
				
				TcpRequest request = new TcpRequest(requestUid);
				request.remoteService = remoteService;
				request.virtualPort = virtualPort;
				request.tcpOptions = tcpOptions;
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
				SoftnetMessage message = MsgBuilder.Create(Constants.Client.TcpController.ModuleId, Constants.Client.TcpController.REQUEST, asnEncoder);
				
				channel.send(message);
				scheduler.add(request.timeoutControlTask, Constants.TcpConnectingWaitSeconds);
			}
		}
		catch(SoftnetException ex)
		{
			responseHandler.onError(new ResponseContext(clientEndpoint, remoteService, attachment), ex);
		}
	}
	
	private void onConnectionAttemptTimedOut(Object state)
	{
		TcpRequest request = (TcpRequest)state;
		synchronized(mutex)
		{
			if(requestList.remove(request) == false)
				return;
		}

		if(request.tcpConnector != null)
			request.tcpConnector.abort();

		request.responseHandler.onError(new ResponseContext(clientEndpoint, request.remoteService, request.attachment), new ConnectionAttemptFailedSoftnetException("The connection attempt timed out."));		
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
		
		TcpRequest request = null;
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
				request.tcpConnector = new TCPConnectorV6(connectionUid, serverIp, request.tcpOptions, scheduler);
			}
			else
			{
				request.tcpConnector = new TCPConnectorV4(connectionUid, serverIp, request.tcpOptions, scheduler);
			}
		}
		
		request.tcpConnector.connect(new TCPResponseHandler()
		{
			@Override
			public void onSuccess(ResponseContext context, SocketChannel socketChannel, ConnectionMode mode)
			{
				onTcpConnectorSuccess(socketChannel, mode, (TcpRequest)context.attachment);
			}

			@Override
			public void onError(ResponseContext context, SoftnetException exception)
			{
				onTcpConnectorError(exception, (TcpRequest)context.attachment);
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
		TcpRequest request = (TcpRequest)attachment;
		synchronized(mutex)
		{
			if(clientStatus != StatusEnum.Online)
				return;
			channel.send(encodeMessage_AuthKey(request.requestUid, request.serverId, authKey));
		}
	}
	
	private void onTcpConnectorSuccess(SocketChannel socketChannel, ConnectionMode mode, final TcpRequest request)
	{
		synchronized(mutex)
		{
			if(requestList.remove(request) == false)
			{
				closeChannel(socketChannel); 
				return;
			}
		}				
		request.timeoutControlTask.cancel();
		request.responseHandler.onSuccess(new ResponseContext(clientEndpoint, request.remoteService, request.attachment), socketChannel, mode);
	}
	
	private void onTcpConnectorError(SoftnetException exception, final TcpRequest request)
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
		
		TcpRequest request = null;
		synchronized(mutex)
		{
			if(channel.closed())
				return;
			request = removeRequest(requestUid);			
			if(request == null)
				return;
		}				
		
		request.timeoutControlTask.cancel();		
		
		if(request.tcpConnector != null)
			request.tcpConnector.abort();
		
		final SoftnetException f_exception = resolveError(request, errorCode);
		final TcpRequest f_request = request;
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
		
		TcpRequest request = null;
		synchronized(mutex)
		{
			if(channel.closed())
				return;
			request = findRequest(requestUid);		
			if(request == null)
				return;
		}		
		request.tcpConnector.onAuthenticationHash(authHash, authKey2);
	}
	
	private void processMessage_AuthError(byte[] message, Channel channel) throws AsnException
	{
		SequenceDecoder asnSequence = ASNDecoder.Sequence(message, 2);
		UUID requestUid = asnSequence.OctetStringToUUID();
		asnSequence.end();
		
		TcpRequest request = null;
		synchronized(mutex)
		{
			if(channel.closed())
				return;
			request = removeRequest(requestUid);			
			if(request == null)
				return;
		}
		
		request.timeoutControlTask.cancel();
		
		if(request.tcpConnector != null)
			request.tcpConnector.abort();

		final TcpRequest f_request = request;
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
        return MsgBuilder.Create(Constants.Client.TcpController.ModuleId, Constants.Client.TcpController.AUTH_KEY, asnEncoder);
	}

	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException
	{
		byte messageTag = message[1];
		if(messageTag == Constants.Client.TcpController.RZV_DATA)
		{
			processMessage_RzvData(message, channel);
		}
		else if(messageTag == Constants.Client.TcpController.REQUEST_ERROR)
		{
			processMessage_RequestError(message, channel);
		}
		else if(messageTag == Constants.Client.TcpController.AUTH_HASH)
		{
			processMessage_AuthHash(message, channel);
		}
		else if(messageTag == Constants.Client.TcpController.AUTH_ERROR)
		{
			processMessage_AuthError(message, channel);
		}
		else		
			throw new FormatException();
	}
	
	private SoftnetException resolveError(TcpRequest tcpRequest, int errorCode)
	{
		if(errorCode == ErrorCodes.CONNECTION_ATTEMPT_FAILED)
			return new ConnectionAttemptFailedSoftnetException();	
		if(errorCode == ErrorCodes.SERVICE_OFFLINE)
			return new ServiceOfflineSoftnetException();			
		if(errorCode == ErrorCodes.PORT_UNREACHABLE)
			return new PortUnreachableSoftnetException(tcpRequest.virtualPort);
		if(errorCode == ErrorCodes.ACCESS_DENIED)
			return new AccessDeniedSoftnetException();	
		if(errorCode == ErrorCodes.SERVICE_BUSY)
			return new ServiceBusySoftnetException();	
		return new UnexpectedErrorSoftnetException(errorCode);
	}
	
	private TcpRequest findRequest(UUID requestUid)
	{
		for(TcpRequest request: requestList)
		{
			if(request.requestUid.equals(requestUid))
				return request;
		}
		return null;
	}
	
	private TcpRequest removeRequest(UUID requestUid)
	{
		for(TcpRequest request: requestList)
		{
			if(request.requestUid.equals(requestUid))
			{
				requestList.remove(request);
				return request;
			}
		}
		return null;
	}
	
	private void closeChannel(SocketChannel channel)
	{
		try
		{
			channel.close();
		}
		catch(IOException e) {}
	}
	
	private class TcpRequest
	{
		public final UUID requestUid;
		public RemoteService remoteService;
		public int virtualPort;
		public TCPOptions tcpOptions;
		public TCPResponseHandler responseHandler;
		public Object attachment;
		public int serverId;
		public TCPConnector tcpConnector;
		public ScheduledTask timeoutControlTask;
		
		public TcpRequest(UUID requestUid)
		{
			this.requestUid = requestUid;
			this.tcpConnector = null;
		}
	}
}
