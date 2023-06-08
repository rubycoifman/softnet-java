package softnet.service;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.UUID;

import softnet.*;
import softnet.core.*;
import softnet.exceptions.ErrorCodes;
import softnet.asn.ASNEncoder;
import softnet.asn.SequenceEncoder;

class TCPBinding 
{
	public final int virtualPort;
	public final GuestAccess guestAccess;
	public final String[] roles;

	private Object mutex;
	private TCPOptions tcpOptions;
	private int backlog;
	private LinkedList<TcpRequest> pendingRequests;
	private LinkedList<TcpRequest> completedRequests;
	private ServiceEndpoint serviceEndpoint;
	
	private TCPAcceptHandler m_acceptHandler;
	
	public TCPBinding(ServiceEndpoint serviceEndpoint, int virtualPort, TCPOptions tcpOptions, int backlog)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.virtualPort = virtualPort;
		this.guestAccess = null;
		this.roles = null;
		this.tcpOptions = tcpOptions;
		this.backlog = backlog;		
		init();
	}
	
	public TCPBinding(ServiceEndpoint serviceEndpoint, int virtualPort, TCPOptions tcpOptions, int backlog, String[] roles)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.virtualPort = virtualPort;
		this.guestAccess = null;
		this.roles = roles;
		this.tcpOptions = tcpOptions;
		this.backlog = backlog;
		init();
	}
	
	public TCPBinding(ServiceEndpoint serviceEndpoint, int virtualPort, TCPOptions tcpOptions, int backlog, GuestAccess guestAccess)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.virtualPort = virtualPort;
		this.guestAccess = guestAccess;
		this.roles = null;
		this.tcpOptions = tcpOptions;
		this.backlog = backlog;
		init();
	}

	private void init()
	{
		pendingRequests = new LinkedList<TcpRequest>();
		completedRequests = new LinkedList<TcpRequest>();
		mutex = new Object();
		m_acceptHandler = null;
	}
	
	public void close()
	{
		synchronized(mutex)
		{
			if(pendingRequests.size() > 0)
			{
				for(TcpRequest request: pendingRequests)
					request.tcpConnector.abort();
				pendingRequests.clear();
			}
			
			if(completedRequests.size() > 0)
			{
				for(TcpRequest request: completedRequests)
					closeChannel(request.socketChannel);
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
				for(TcpRequest request: pendingRequests)
					request.tcpConnector.abort();
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
				for(TcpRequest request: pendingRequests)
					request.tcpConnector.abort();
			}
			
			if(completedRequests.size() > 0)
			{
				for(TcpRequest request: completedRequests)
					closeChannel(request.socketChannel);
			}
		}		
	}
	
	public void accept(final TCPAcceptHandler acceptHandler)
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
				final TcpRequest request = completedRequests.removeFirst();				
				Thread thread = new Thread()
				{
				    public void run(){
				    	acceptHandler.accept(new RequestContext(serviceEndpoint, request.user, request.clientId), request.socketChannel, request.mode);
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
		TcpRequest request = new TcpRequest();
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
			request.tcpConnector = new TCPConnectorV6(connectionUid, serverIp, tcpOptions, serviceEndpoint.scheduler);
		}
		else
		{
			request.tcpConnector = new TCPConnectorV4(connectionUid, serverIp, tcpOptions, serviceEndpoint.scheduler);
		}
		
		synchronized(mutex)
		{						
			pendingRequests.add(request);
		}

		request.tcpConnector.connect(new TCPResponseHandler()
		{
			@Override
			public void onSuccess(SocketChannel socketChannel, ConnectionMode mode, Object attachment)
			{
				onTcpConnectorSuccess(socketChannel, mode, attachment);
			}

			@Override
			public void onError(int errorCode, Object attachment)
			{
				onTcpConnectorError(errorCode, attachment);
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
				
		serviceEndpoint.scheduler.add(request.timeoutControlTask, Constants.TcpConnectingWaitSeconds);
	}
	
	private void onConnectionAttemptTimedOut(Object state)
	{
		TcpRequest request = (TcpRequest)state;
		synchronized(mutex)
		{
			if(pendingRequests.remove(request) == false)
				return;
		}
		request.tcpConnector.abort();
	}
	
	public void onAuthenticationHash(UUID connectionUid, byte[] authHash, byte[] authKey2)
	{
		TcpRequest request = null;
		synchronized(mutex)
		{
			request = findPendingRequest(connectionUid);
		}
		
		if(request != null)
		{
			request.tcpConnector.onAuthenticationHash(authHash, authKey2);
		}
	}
	
	public void onAuthenticationError(UUID connectionUid)
	{
		TcpRequest request = null;
		synchronized(mutex)
		{
			request = removePendingRequest(connectionUid);
		}
		
		if(request != null)
		{
			request.timeoutControlTask.cancel();
			request.tcpConnector.abort();
			request.channel.send(EncodeMessage_RequestError(request.requestUid, ErrorCodes.CONNECTION_ATTEMPT_FAILED, request.userKind, request.clientId));
		}
	}
	
	private void sendAuthenticationKey(byte[] authKey, Object attachment)
	{
		TcpRequest request = (TcpRequest)attachment;
		request.channel.send(EncodeMessage_AuthKey(this.virtualPort, request.connectionUid, request.serverId, authKey));
	}
	
	private void onTcpConnectorSuccess(SocketChannel socketChannel, ConnectionMode mode, Object attachment)
	{
		TcpRequest request = (TcpRequest)attachment;
		TCPAcceptHandler acceptHandler = null;
		synchronized(mutex)
		{
			if(pendingRequests.remove(request) == false)
			{
				closeChannel(socketChannel);
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
				request.socketChannel = socketChannel;
				request.mode = mode;
				completedRequests.add(request);
				return;
			}
		}
		
		acceptHandler.accept(new RequestContext(serviceEndpoint, request.user, request.clientId), socketChannel, mode);
	}

	private void onTcpConnectorError(int errorCode, Object attachment)
	{
		TcpRequest request = (TcpRequest)attachment;	
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
        return MsgBuilder.Create(Constants.Service.TcpController.ModuleId, Constants.Service.TcpController.AUTH_KEY, asnEncoder);
	}
	
	private SoftnetMessage EncodeMessage_RequestError(byte[] requestUid, int errorCode, int userKind, long clientId)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.OctetString(requestUid);
        asnSequence.Int32(errorCode);
        asnSequence.Int32(userKind);
        asnSequence.Int64(clientId);
        return MsgBuilder.Create(Constants.Service.TcpController.ModuleId, Constants.Service.TcpController.REQUEST_ERROR, asnEncoder);
	}
	
	private TcpRequest findPendingRequest(UUID connectionUid)
	{
		for(TcpRequest request: pendingRequests)
		{
			if(request.connectionUid.equals(connectionUid))
				return request;
		}
		return null;
	}
	
	private TcpRequest removePendingRequest(UUID connectionUid)
	{
		for(TcpRequest request: pendingRequests)
		{
			if(request.connectionUid.equals(connectionUid))
			{
				pendingRequests.remove(request);
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
		public Channel channel;
		public byte[] requestUid;
		public UUID connectionUid;
		public int serverId;
		public int userKind;
		public MembershipUser user;
		public long clientId;
		public TCPConnector tcpConnector;
		public SocketChannel socketChannel;
		public ConnectionMode mode;
		public ScheduledTask timeoutControlTask;
		
		public TcpRequest()
		{			
			socketChannel = null;			
		}
	}
}
