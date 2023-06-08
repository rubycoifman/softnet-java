package softnet.client;

import java.util.HashSet;
import java.util.regex.Pattern;

import softnet.EndpointConnectivity;
import softnet.MembershipUser;
import softnet.TCPOptions;
import softnet.core.*;

public class ClientEndpoint
{
	public boolean isOnline()
	{
		return clientInstaller.isOnline();
	}
	
	public ClientStatus getStatus()
	{
		return clientInstaller.getStatus();
	}

	public EndpointConnectivity getConnectivity()
	{
		return endpointConnector.getConnectivity();
	}

	public boolean isClosed()
	{
		return _isClosed;
	}
	
	public void connect()
	{
		eventController.onConnectCalled();
		endpointConnector.Connect();
	}

	public void disconnect()
	{
		endpointConnector.Disconnect();
	}
	
	public void setPingPeriod(int seconds)
	{
		endpointConnector.setLocalPingPeriod(seconds);
	}

	public void close()
	{
		_isClosed = true;
		endpointConnector.Close();
		scheduler.shutdown();
		threadPool.shutdown();
	}
	
	public boolean isSingleService()
	{
		return clientURI.isSingleService();
	}

	public boolean isMultiService()
	{
		return clientURI.isMultiService();
	}

	public boolean isStateless()
	{
		return clientURI.isStateless();
	}

	public boolean isStateful()
	{
		return clientURI.isStateful();
	}
	
	public RemoteService findService(long serviceId)
	{
		return serviceGroup.findService(serviceId);		
	}
	
	public RemoteService findService(String hostname)
	{
		return serviceGroup.findService(hostname);
	}

	public RemoteService[] getServices()
	{
		return serviceGroup.getServices();
	}
	
	public MembershipUser getUser()
	{
		if(clientURI.isStateful())
			return membership.getUser();
		else
			return statelessGuest;
	}
		
	public void addEventListener(ClientEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.add(listener);
		}
		
		serviceGroup.addEventListener(listener);
		eventController.addEventListener(listener);
		
		if(clientURI.isStateful())
			membership.addEventListener(listener);
	}

	public void removeEventListener(ClientEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.remove(listener);
		}		
		
		serviceGroup.removeEventListener(listener);
		eventController.removeEventListener(listener);
		
		if(clientURI.isStateful())
			membership.removeEventListener(listener);
	}
	
	public void setPersistenceL2()
	{
		eventController.setPersistenceL2();
	}
	
	public void setPersistenceL2(String fileBasedPersistenceDirectory)
	{
		eventController.setPersistenceL2(fileBasedPersistenceDirectory);
	}
		
	public void subscribeToREvent(String eventName, RemoteEventListener listener)
	{		
		eventController.subscribeToREvent(eventName, listener);		
	}

	public void subscribeToQEvent(String eventName, RemoteEventListener listener)
	{
		eventController.subscribeToQEvent(eventName, listener);				
	}

	public void subscribeToPEvent(String eventName, RemoteEventListener listener)
	{
		eventController.subscribeToPEvent(eventName, listener);		
	}

	public boolean removeSubscription(String eventName)
	{
		return eventController.removeSubscription(eventName);
	}
	
	public void call(RemoteService remoteService, RemoteProcedure remoteProcedure, RPCResponseHandler responseHandler)
	{
		rpcController.call(remoteService, remoteProcedure, responseHandler, null, Constants.RpcWaitSeconds);
	}
	
	public void call(RemoteService remoteService, RemoteProcedure remoteProcedure, RPCResponseHandler responseHandler, Object attachment)
	{
		rpcController.call(remoteService, remoteProcedure, responseHandler, attachment, Constants.RpcWaitSeconds);
	}
	
	public void call(RemoteService remoteService, RemoteProcedure remoteProcedure, RPCResponseHandler responseHandler, Object attachment, int waitSeconds)
	{
		rpcController.call(remoteService, remoteProcedure, responseHandler, attachment, waitSeconds);
	}
	
	public void tcpConnect(RemoteService remoteService, int virtualPort, TCPOptions tcpOptions, TCPResponseHandler responseHandler)
	{
		tcpController.connect(remoteService, virtualPort, tcpOptions, responseHandler, null);
	}
	
	public void tcpConnect(RemoteService remoteService, int virtualPort, TCPOptions tcpOptions, TCPResponseHandler responseHandler, Object attachment)
	{		
		tcpController.connect(remoteService, virtualPort, tcpOptions, responseHandler, attachment);
	}
	
	public void udpConnect(RemoteService remoteService, int virtualPort, UDPResponseHandler responseHandler)
	{
		udpController.connect(remoteService, virtualPort, responseHandler, null);
	}
	
	public void udpConnect(RemoteService remoteService, int virtualPort, UDPResponseHandler responseHandler, Object attachment)
	{
		udpController.connect(remoteService, virtualPort, responseHandler, attachment);
	}
	
	public static ClientEndpoint create(String serviceType, String contractAuthor, ClientURI clientURI) throws IllegalArgumentException
	{
		if(clientURI == null)
			throw new IllegalArgumentException("The value of 'clientURI' is null.");
		
		if(clientURI.category == ClientCategory.SingleService || clientURI.category == ClientCategory.SingleServiceStateless)
			throw new IllegalArgumentException(String.format("The URI '%s' identifies a single-service client that is not allowed in this context.", clientURI.value));

		if(clientURI.category == ClientCategory.MultiService)
			throw new IllegalArgumentException(String.format("The URI '%s' identifies a stataful client that is not allowed in this context.", clientURI.value));

		validateServiceType(serviceType);
		validateContractAuthor(contractAuthor);
		
		final ClientEndpoint clientEndpoint = new ClientEndpoint();
		
		MultiServiceGroup multiServiceGroup = new MultiServiceGroup(clientEndpoint);		
		multiServiceGroup.remoteServiceOfflineCallback = new BiAcceptor<Long, Channel>()
		{
			public void accept(Long serviceId, Channel channel)
			{
				clientEndpoint.ServiceGroup_onRemoteServiceOffline(serviceId, channel);
			}
		};
		
		clientEndpoint.initialize(serviceType, contractAuthor, clientURI, null, null, multiServiceGroup);	
		return clientEndpoint;
	}
	
	public static ClientEndpoint create(String serviceType, String contractAuthor, ClientURI clientURI, String password) throws IllegalArgumentException
	{
		if(clientURI == null)
			throw new IllegalArgumentException("The value of 'clientURI' is null.");
		
		if(clientURI.category == ClientCategory.SingleService || clientURI.category == ClientCategory.SingleServiceStateless)
			throw new IllegalArgumentException(String.format("The URI '%s' identifies a single-service client that is not allowed in this context.", clientURI.value));

		if(clientURI.category == ClientCategory.MultiService)
		{
			if (password == null || password.length() == 0)
				throw new IllegalArgumentException("The value of 'password' must not be null or empty for a statefull client.");

			if (password.length() > 256)
				throw new IllegalArgumentException("The length of 'password' must not be greater than 256.");
		}

		validateServiceType(serviceType);
		validateContractAuthor(contractAuthor);
		
		final ClientEndpoint clientEndpoint = new ClientEndpoint();
		
		MultiServiceGroup multiServiceGroup = new MultiServiceGroup(clientEndpoint);		
		multiServiceGroup.remoteServiceOfflineCallback = new BiAcceptor<Long, Channel>()
		{
			public void accept(Long serviceId, Channel channel)
			{
				clientEndpoint.ServiceGroup_onRemoteServiceOffline(serviceId, channel);
			}
		};
		
		clientEndpoint.initialize(serviceType, contractAuthor, clientURI, password, null, multiServiceGroup);	
		return clientEndpoint;
	}

	public static ClientEndpoint create(String serviceType,  String contractAuthor, ClientURI clientURI, String password, String clientDescription) throws IllegalArgumentException
	{
		if(clientURI == null)
			throw new IllegalArgumentException("The value of 'clientURI' is null.");

		if(clientURI.category == ClientCategory.SingleService || clientURI.category == ClientCategory.SingleServiceStateless)
			throw new IllegalArgumentException(String.format("The URI '%s' identifies a single-service client that is not allowed in this context.", clientURI.value));

		if(clientURI.category == ClientCategory.MultiService && (password == null || password.length() == 0))
			throw new IllegalArgumentException("The value of 'password' must not be null or empty for a stateful client.");		

		validateServiceType(serviceType);
		validateContractAuthor(contractAuthor);
						
		if(clientURI.category == ClientCategory.MultiServiceStateless)
		{
			clientDescription = null;
		}
		else if(clientDescription != null)
		{
			if(clientDescription.length() == 0)
				clientDescription = null;
			else
				validateClientDescription(clientDescription);
		}
		
		final ClientEndpoint clientEndpoint = new ClientEndpoint();
		
		MultiServiceGroup multiServiceGroup = new MultiServiceGroup(clientEndpoint);		
		multiServiceGroup.remoteServiceOfflineCallback = new BiAcceptor<Long, Channel>()
		{
			public void accept(Long serviceId, Channel channel)
			{
				clientEndpoint.ServiceGroup_onRemoteServiceOffline(serviceId, channel);
			}
		};
		
		clientEndpoint.initialize(serviceType, contractAuthor, clientURI, password, clientDescription, multiServiceGroup);	
		return clientEndpoint;
	}
	
	protected ClientEndpoint()
	{
		threadPool = new ThreadPool();
		scheduler = new Scheduler(threadPool);
		endpoint_mutex = new Object();
		_isClosed = false;		
	}
	
	protected void initialize(String serviceType, String contractAuthor, ClientURI clientURI, String password, String clientDescription, ServiceGroup serviceGroup)
	{
		this.clientURI = clientURI;
		this.serviceGroup = serviceGroup;
		
		threadPool.init();
		scheduler.init();		
		
		endpointConnector = new EndpointConnector(clientURI, password, endpoint_mutex, this);
		endpointConnector.onConnectedCallback = new Acceptor<Channel>()
		{
			public void accept(Channel channel)
			{
				EndpointConnector_onConnected(channel);
			}
		};
		endpointConnector.onDisconnectedCallback = new Runnable()
		{
			public void run()
			{
				EndpointConnector_onDisconnected();
			}
		};		
		endpointConnector.onClosedCallback = new Runnable()
		{
			public void run()
			{
				EndpointConnector_onClosed();
			}
		};		
		endpointConnector.onConnectivityEventCallback = new Runnable()
		{
			public void run()
			{				
				EndpointConnector_onConnectivityEvent();
			}
		};		
		
		stateController = new StateController(endpointConnector, endpoint_mutex);
		
		if(clientURI.isStateful())
			membership = new Membership(this);
		else
			statelessGuest = new StatelessGuest();
		
		clientInstaller = new ClientInstaller(serviceType, contractAuthor, clientDescription, serviceGroup, membership, endpoint_mutex);		
		clientInstaller.onClientOnlineCallback = new Acceptor<Channel>()
		{
			public void accept(Channel channel)
			{
				ClientIntaller_onClientOnline(channel);
			}
		};
		clientInstaller.onClientParkedCallback = new Runnable()
		{
			public void run()
			{
				endpointConnector.onEndpointInstalled();
			}
		};
		clientInstaller.onStatusChangedCallback = new Runnable()
		{
			public void run()
			{
				ClientIntaller_onStatusChanged();
			}
		};
			
		tcpController = new TCPController(this);
		udpController = new UDPController(this);
		rpcController = new RPCController(this);
		
		if(clientURI.isStateful())
			eventController = new SFEventController(this, clientURI);
		else
			eventController = new SLEventController(this, clientURI);

		eventListeners = new HashSet<ClientEventListener>();
	}

	protected Object endpoint_mutex;
	protected ThreadPool threadPool;
	protected Scheduler scheduler;
	protected ClientURI clientURI;
	private boolean _isClosed;
	private ServiceGroup serviceGroup;
	protected EndpointConnector endpointConnector;
	private StateController stateController;
	private ClientInstaller clientInstaller;
	private Membership membership;
	private StatelessGuest statelessGuest;
	protected TCPController tcpController;
	protected UDPController udpController;
	protected RPCController rpcController;
	private EventController eventController;
	private HashSet<ClientEventListener> eventListeners;

	private void EndpointConnector_onConnected(Channel channel)
	{
		clientInstaller.onEndpointConnected(channel);
		stateController.onChannelConnected(channel);
		serviceGroup.onEndpointConnected(channel);
		tcpController.onEndpointConnected(channel);
		udpController.onEndpointConnected(channel);
		rpcController.onEndpointConnected(channel);
		eventController.onEndpointConnected(channel);
		if(clientURI.isStateful())
			membership.onEndpointConnected(channel);
	}
	
	private void EndpointConnector_onDisconnected()
	{
		clientInstaller.onEndpointDisconnected();
		serviceGroup.onEndpointDisconnected();
		tcpController.onEndpointDisconnected();
		udpController.onEndpointDisconnected();
		rpcController.onEndpointDisconnected();
		eventController.onEndpointDisconnected();
		if(clientURI.isStateful())
			membership.onEndpointDisconnected();
	}	
	
	private void EndpointConnector_onClosed()
	{
		tcpController.onEndpointClosed();
		udpController.onEndpointClosed();
		eventController.onEndpointClosed();
	}
	
	private void EndpointConnector_onConnectivityEvent()
	{
		final ClientEndpointEvent event = new ClientEndpointEvent(this);		
		synchronized(eventListeners)
		{
			for (final ClientEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onConnectivityChanged(event);
					}
				};
				threadPool.execute(runnable);
		    }
		}
	}
	
	private void ClientIntaller_onClientOnline(Channel channel)
	{
		endpointConnector.onEndpointInstalled();		
		tcpController.onClientOnline();
		udpController.onClientOnline();
		rpcController.onClientOnline();
		serviceGroup.onClientOnline();
		if(clientURI.isStateful())
			membership.onClientOnline();			
	}

	private void ClientIntaller_onStatusChanged()
	{
		final ClientEndpointEvent event = new ClientEndpointEvent(this);		
		synchronized(eventListeners)
		{
			for (final ClientEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onStatusChanged(event);
					}
				};
				threadPool.execute(runnable);
		    }
		}
	}

	protected void ServiceGroup_onRemoteServiceOffline(long serviceId, Channel channel)
	{
		tcpController.onRemoteServiceOffline(serviceId, channel);
		udpController.onRemoteServiceOffline(serviceId, channel);
		rpcController.onRemoteServiceOffline(serviceId, channel);
	}
	
	private class StatelessGuest implements MembershipUser
    {
        public boolean isGuest() { return true; }
        public boolean isStatelessGuest() { return true; }
        public boolean hasRoles() { return false; }

        public long getId()
        {
            return 0;
        }

        public String getName()
        {
            return "Guest";
        }

        public String[] getRoles()
        {
            return new String[0];
        }

        public boolean isInRole(String role)
        {
            return false;
        }

        public boolean isRemoved()
        {
            return false;
        }
    }

	protected static void validateServiceType(String serviceType)
    {
		if(serviceType == null || serviceType.length() == 0)
			throw new IllegalArgumentException("The name of the service type must not be null or empty.");

		if(serviceType.length() > 256)
			throw new IllegalArgumentException("The name of the service type contains more than 256 characters.");
		
		if (Pattern.matches("^[\\u0020-\\u007F]+$", serviceType) == false)			
            throw new IllegalArgumentException(String.format("The name of the service type '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", serviceType));

		if (Pattern.matches("^[a-zA-Z].*$", serviceType) == false)
            throw new IllegalArgumentException(String.format("The leading character in the name of the service type '%s' is illegal. Allowed characters: a-z A-Z.", serviceType));		

		if (Pattern.matches("^.*\\s$", serviceType))
            throw new IllegalArgumentException(String.format("The trailing space is not allowed in the name of the service type '%s'.", serviceType));			
		
		if (Pattern.matches("^.*\\s\\s.*$", serviceType))
			throw new IllegalArgumentException(String.format("The name of the service type '%s' has illegal format. Two or more consecutive spaces are not allowed.", serviceType));

		if (Pattern.matches("^[\\w\\s.$*+()#@%&=':\\^\\[\\]\\-/!]+$", serviceType) == false)
            throw new IllegalArgumentException(String.format("The name of the service type '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", serviceType));    	    
    }
	    
	protected static void validateContractAuthor(String contractAuthor)
    {
		if(contractAuthor == null || contractAuthor.length() == 0)
			throw new IllegalArgumentException("The name of the contract author must not be null or empty.");

		if(contractAuthor.length() > 256)
			throw new IllegalArgumentException("The name of the contract author contains more than 256 characters.");

		if (Pattern.matches("^[\\u0020-\\u007F]+$", contractAuthor) == false)			
            throw new IllegalArgumentException(String.format("The name of the contract author '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", contractAuthor));

		if (Pattern.matches("^[a-zA-Z].*$", contractAuthor) == false)
            throw new IllegalArgumentException(String.format("The leading character in the name of the contract author '%s' is illegal. Allowed characters: a-z A-Z.", contractAuthor));		

		if (Pattern.matches("^.*\\s$", contractAuthor))
            throw new IllegalArgumentException(String.format("The trailing space is not allowed in the name of the contract author '%s'.", contractAuthor));			
		
		if (Pattern.matches("^.*\\s\\s.*$", contractAuthor))
			throw new IllegalArgumentException(String.format("The name of the contract author '%s' has illegal format. Two or more consecutive spaces are not allowed.", contractAuthor));

		if (Pattern.matches("^[\\w\\s.$*+()#@%&=':\\^\\[\\]\\-/!]+$", contractAuthor) == false)
            throw new IllegalArgumentException(String.format("The name of the contract author '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", contractAuthor));    
    }

	protected static void validateClientDescription(String clientDescription)
    {
		if(clientDescription.length() > 256)
			throw new IllegalArgumentException("The client description contains more than 256 characters.");
		
		if (Pattern.matches("^[\\u0020-\\u007F]+$", clientDescription) == false)			
            throw new IllegalArgumentException(String.format("The client description '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", clientDescription));

		if (Pattern.matches("^[a-zA-Z].*$", clientDescription) == false)
            throw new IllegalArgumentException(String.format("The leading character in the client description '%s' is illegal. Allowed characters: a-z A-Z.", clientDescription));		

		if (Pattern.matches("^.*\\s$", clientDescription))
            throw new IllegalArgumentException(String.format("The trailing space is not allowed in the client description '%s'.", clientDescription));			
		
		if (Pattern.matches("^.*\\s\\s.*$", clientDescription))
			throw new IllegalArgumentException(String.format("The client description '%s' has illegal format. Two or more consecutive spaces are not allowed.", clientDescription));

		if (Pattern.matches("^[\\w\\s.$*+()#@%&=':\\^\\[\\]\\-/!]+$", clientDescription) == false)
            throw new IllegalArgumentException(String.format("The client description '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", clientDescription));                	    
    }
}
