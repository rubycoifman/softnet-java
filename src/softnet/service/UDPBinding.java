package softnet.service;

import java.net.*;
import java.util.LinkedList;
import java.util.UUID;

import softnet.*;
import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;

class UDPBinding 
{
	public final int virtualPort;
	public final GuestAccess guestAccess;
	public final String[] roles;
	
	private Object mutex;
	private int backlog;
	private LinkedList<UdpRequest> pendingRequests;
	private LinkedList<UdpRequest> completedRequests;
	private ServiceEndpoint serviceEndpoint;
	
	private UDPAcceptHandler m_acceptHandler;	
	
	public UDPBinding(ServiceEndpoint serviceEndpoint, int virtualPort, int backlog)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.virtualPort = virtualPort;
		this.guestAccess = null;
		this.roles = null;
		this.backlog = backlog;	
		init();
	}
	
	public UDPBinding(ServiceEndpoint serviceEndpoint, int virtualPort, int backlog, String[] roles)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.virtualPort = virtualPort;
		this.guestAccess = null;
		this.roles = roles;
		this.backlog = backlog;
		init();
	}
	
	public UDPBinding(ServiceEndpoint serviceEndpoint, int virtualPort, int backlog, GuestAccess guestAccess)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.virtualPort = virtualPort;
		this.guestAccess = guestAccess;
		this.roles = null;
		this.backlog = backlog;
		init();
	}
	
	private void init()
	{
		pendingRequests = new LinkedList<UdpRequest>();
		completedRequests = new LinkedList<UdpRequest>();
		mutex = new Object();
		m_acceptHandler = null;
	}
	
	public void close()
	{
		synchronized(mutex)
		{
			if(pendingRequests.size() > 0)
			{
				for(UdpRequest request: pendingRequests)
					request.udpConnector.abort();
				pendingRequests.clear();
			}
			
			if(completedRequests.size() > 0)
			{
				for(UdpRequest request: completedRequests)
					request.datagramSocket.close();
				completedRequests.clear();
			}
		}
	}
	
	public void onEndpointDisconnected()
	{
		synchronized(mutex)
		{
			if(pendingRequests.size() > 0)
			{
				for(UdpRequest request: pendingRequests)
					request.udpConnector.abort();
				pendingRequests.clear();
			}
		}
	}
	
	public void onEndpointClosed()
	{
		synchronized(mutex)
		{
			if(pendingRequests.size() > 0)
			{
				for(UdpRequest request: pendingRequests)
					request.udpConnector.abort();			
			}
			
			if(completedRequests.size() > 0)
			{
				for(UdpRequest request: completedRequests)
					request.datagramSocket.close();
			}
		}		
	}
	
	public void accept(final UDPAcceptHandler acceptHandler)
	{
		synchronized(mutex)
		{
			if(completedRequests.size() == 0)
			{
				if(m_acceptHandler != null)
					throw new IllegalStateException("There is another pending asynchronous accept operation.");
				m_acceptHandler = acceptHandler;
			}
			else
			{
				final UdpRequest request = completedRequests.removeFirst();
				Thread thread = new Thread()
				{
				    public void run(){
				    	acceptHandler.accept(new RequestContext(serviceEndpoint, request.user, request.clientId), request.datagramSocket, request.remoteSocketAddress, request.mode);
				    }
				};
				thread.start();
			}
		}
	}
	
	public boolean isBusy()
	{
		synchronized(mutex)
		{
			if(backlog > 0)
			{
				if((pendingRequests.size() + completedRequests.size()) >= backlog)
					return true;
			}
			else
			{
				if(m_acceptHandler == null)
					return true;
			}
		}
		return false;
	}

	public void createConnection(Channel channel, byte[] requestUid, UUID connectionUid, int serverId, InetAddress serverIp, int userKind, MembershipUser user, long clientId)
	{
		UdpRequest request = new UdpRequest();
		request.channel = channel;
		request.requestUid = requestUid;
		request.connectionUid = connectionUid;
		request.serverId = serverId;
		request.userKind = userKind;
		request.user = user;
		request.clientId = clientId;
		Acceptor<Object> acceptor = new Acceptor<Object>()
		{
			public void accept(Object state) { onConnectionAttemptTimedOut(state); }
		};
		request.timeoutControlTask = new ScheduledTask(acceptor, request);
		
		if(serverIp instanceof Inet6Address)
		{
			request.udpConnector = new UDPConnectorV6(connectionUid, serverIp, serviceEndpoint.scheduler);
		}
		else
		{
			request.udpConnector = new UDPConnectorV4(connectionUid, serverIp, serviceEndpoint.scheduler);
		}
		
		synchronized(mutex)
		{
			pendingRequests.add(request);
		}

		request.udpConnector.connect(new UDPResponseHandler()
		{
			@Override
			public void onSuccess(DatagramSocket datagramSocket, InetSocketAddress remoteSocketAddress, ConnectionMode mode, Object attachment)
			{
				onUdpConnectorSuccess(datagramSocket, remoteSocketAddress, mode, attachment);
			}

			@Override
			public void onError(int errorCode, Object attachment)
			{
				onUdpConnectorError(errorCode, attachment);
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
	
		serviceEndpoint.scheduler.add(request.timeoutControlTask, Constants.UdpConnectingWaitSeconds);
	}
	
	private void onConnectionAttemptTimedOut(Object state)
	{
		UdpRequest request = (UdpRequest)state;
		synchronized(mutex)
		{
			if(pendingRequests.remove(request) == false)
				return;
		}
		request.udpConnector.abort();
	}
	
	public void onAuthenticationHash(UUID connectionUid, byte[] authHash, byte[] authKey2)
	{
		UdpRequest request = null;
		synchronized(mutex)
		{
			request = findPendingRequest(connectionUid);
		}
		
		if(request != null)
		{
			request.udpConnector.onAuthenticationHash(authHash, authKey2);
		}
	}
	
	public void onAuthenticationError(UUID connectionUid)
	{
		UdpRequest request = null;
		synchronized(mutex)
		{
			request = removePendingRequest(connectionUid);
		}
		
		if(request != null)
		{
			request.timeoutControlTask.cancel();
			request.udpConnector.abort();
			request.channel.send(EncodeMessage_RequestError(request.requestUid, ErrorCodes.CONNECTION_ATTEMPT_FAILED, request.userKind, request.clientId));
		}
	}
	
	private void sendAuthenticationKey(byte[] authKey, Object attachment)
	{
		UdpRequest request = (UdpRequest)attachment;
		request.channel.send(EncodeMessage_AuthKey(this.virtualPort, request.connectionUid, request.serverId, authKey));
	}
	
	private void onUdpConnectorSuccess(DatagramSocket datagramSocket, InetSocketAddress remoteSocketAddress, ConnectionMode mode, Object attachment)
	{
		final UdpRequest request = (UdpRequest)attachment;
		UDPAcceptHandler acceptHandler = null;
		synchronized(mutex)
		{
			if(pendingRequests.remove(request) == false)
			{
				datagramSocket.close();
				return;
			}
			
			request.timeoutControlTask.cancel();
			
			if(m_acceptHandler != null)
			{
				acceptHandler = m_acceptHandler;
				m_acceptHandler = null;
			}
			else
			{
				request.datagramSocket = datagramSocket;
				request.remoteSocketAddress = remoteSocketAddress;
				request.mode = mode;
				completedRequests.add(request);
				return;
			}
		}
		
		acceptHandler.accept(new RequestContext(serviceEndpoint, request.user, request.clientId), datagramSocket, remoteSocketAddress, mode);
	}

	private void onUdpConnectorError(int errorCode, Object attachment)
	{
		UdpRequest request = (UdpRequest)attachment;	
		synchronized(mutex)
		{
			if(pendingRequests.remove(request) == false)
				return;
		}		
		request.timeoutControlTask.cancel();
		request.channel.send(EncodeMessage_RequestError(request.requestUid, errorCode, request.userKind, request.clientId));
	}
	
	private SoftnetMessage EncodeMessage_AuthKey(int virtualPort, UUID connectionUid, int serverId, byte[] authKey)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.Int32(virtualPort);
        asnSequence.OctetString(connectionUid);
        asnSequence.Int32(serverId);
        asnSequence.OctetString(authKey);
        return MsgBuilder.Create(Constants.Service.UdpController.ModuleId, Constants.Service.UdpController.AUTH_KEY, asnEncoder);
	}
	
	private SoftnetMessage EncodeMessage_RequestError(byte[] requestUid, int errorCode, int userKind, long clientId)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.OctetString(requestUid);
        asnSequence.Int32(errorCode);
        asnSequence.Int32(userKind);
        asnSequence.Int64(clientId);
        return MsgBuilder.Create(Constants.Service.UdpController.ModuleId, Constants.Service.UdpController.REQUEST_ERROR, asnEncoder);
	}
	
	private UdpRequest findPendingRequest(UUID connectionUid)
	{
		for(UdpRequest request: pendingRequests)
		{
			if(request.connectionUid.equals(connectionUid))
				return request;
		}
		return null;
	}
	
	private UdpRequest removePendingRequest(UUID connectionUid)
	{
		for(UdpRequest request: pendingRequests)
		{
			if(request.connectionUid.equals(connectionUid))
			{
				pendingRequests.remove(request);
				return request;
			}
		}
		return null;
	}
		
	private class UdpRequest
	{
		public Channel channel;
		public byte[] requestUid;
		public UUID connectionUid;
		public int serverId;
		public int userKind;
		public MembershipUser user;
		public long clientId;
		public UDPConnector udpConnector;
		public DatagramSocket datagramSocket;
		public InetSocketAddress remoteSocketAddress;
		public ConnectionMode mode;
		public ScheduledTask timeoutControlTask;
		
		public UdpRequest()
		{			
			//socketChannel = null;			
		}
	}
}
