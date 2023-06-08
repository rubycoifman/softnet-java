package softnet.service;

import java.util.HashSet;
import java.util.regex.Pattern;

import softnet.*;
import softnet.core.*;
import softnet.exceptions.*;

public class ServiceEndpoint 
{
	public static SiteStructure createStructure(String serviceType, String contractAuthor)
	{
		return new SiteStructureAdapter(serviceType, contractAuthor);
	}
	
	public static ServiceEndpoint create(SiteStructure siteStructure, String serviceVersion, ServiceURI serviceURI, String password) throws HostFunctionalitySoftnetException
	{
		if(siteStructure == null)
			throw new IllegalArgumentException("'siteStructure' must not be null.");
		
		if(serviceURI == null)
			throw new IllegalArgumentException("'serviceURI' must not be null.");

		if(serviceVersion != null)
		{
			if(serviceVersion.length() > 0)
				validateServiceVersionFormat(serviceVersion);
			else
				serviceVersion = null;
		}
		
		if(password == null || password.length() == 0)
			throw new IllegalArgumentException("'password' must not be null or empty.");	
		
		if (password.length() > 256)
			throw new IllegalArgumentException("The length of 'password' must not be greater than 256.");
		
		ServiceEndpoint service = new ServiceEndpoint();
		service.initialize((SiteStructureAdapter)siteStructure, serviceVersion, serviceURI, password);
		return service;
	}
		
	private ServiceEndpoint()
	{
		threadPool = new ThreadPool();
		scheduler = new Scheduler(threadPool);
	}

	private void initialize(SiteStructureAdapter siteStructure, String serviceVersion, ServiceURI serviceURI, String password) throws HostFunctionalitySoftnetException
	{
		siteStructure.commit();
		
		threadPool.init();
		scheduler.init();
				
		endpointConnectivity = new EndpointConnectivity(ConnectivityStatus.Disconnected);
		
		endpointConnector = new EndpointConnector(serviceURI, password, endpoint_mutex, this);
		endpointConnector.connectedEventCallback = new Acceptor<Channel>()
		{
			public void accept(Channel channel)
			{
				EndpointConnector_onConnectedEvent(channel);
			}
		};		
		endpointConnector.disconnectedEventCallback = new Runnable()
		{
			public void run()
			{
				EndpointConnector_onDisconnectedEvent();
			}
		};		
		endpointConnector.closedCallback = new Runnable()
		{
			public void run()
			{
				EndpointConnector_onClosed();
			}
		};				
		endpointConnector.connectivityEventCallback = new Acceptor<EndpointConnectivity>()
		{
			public void accept(EndpointConnectivity connectivity)
			{
				EndpointConnector_onConnectivityEvent(connectivity);
			}
		};						

		stateController = new StateController(endpointConnector, endpoint_mutex);
		stateController.hostnameChangedCallback = new Runnable()
		{
			public void run()
			{
				fireHostnameChangedEvent();
			}
		};		
		
		if(siteStructure.isRbacSupported())
			membership = new RBMembership(siteStructure, this, endpoint_mutex);
		else
			membership = new UBMembership(siteStructure, this, endpoint_mutex);
		
		if(siteStructure.areEventsSupported())
		{
			eventController = new EventController(this, siteStructure, serviceURI);
			eventController.init();
		}

		tcpController = new TCPController(this, membership);
		udpController = new UDPController(this, membership);
		rpcController = new RPCController(this, membership);

		serviceInstaller = new ServiceInstaller(siteStructure, serviceVersion, membership, stateController, endpoint_mutex);
		serviceInstaller.Init();
		serviceInstaller.serviceOnlineCallback = new Runnable()
		{
			@Override
			public void run()
			{	
				ServiceInstaller_onServiceOnline();
			}
		};
		serviceInstaller.serviceParkedCallback = new Runnable()
		{
			@Override
			public void run()
			{	
				ServiceInstaller_onServiceParked();
			}
		};
		serviceInstaller.serviceStatusEventCallback = new Runnable()
		{
			@Override
			public void run()
			{	
				fireStatusEvent();
			}
		};		
		
		eventListeners = new HashSet<ServiceEventListener>();
	}
			
	public boolean isOnline()
	{
		return serviceInstaller.Online();
	}
		
	public boolean isClosed()
	{
		return is_closed;
	}
	
	public EndpointConnectivity getConnectivity()
	{
		return endpointConnectivity;
	}

	public ServiceStatus getStatus()
	{
		return serviceInstaller.getStatus();
	}

	public String getHostname()
	{
		return stateController.getHostname();
	}
	
	public void connect()
	{
		if(eventController != null)
			eventController.onConnectCalled();
		endpointConnector.Connect();
	}

	public void disconnect()
	{
		endpointConnector.Disconnect();
	}

	public void close()
	{
		is_closed = true;
		endpointConnector.Close();
		scheduler.shutdown();
		threadPool.shutdown();
	}

	public void setPingPeriod(int seconds)
	{
		endpointConnector.setLocalPingPeriod(seconds);
	}
	
	public boolean isGuestAllowed()
	{
		return membership.isGuestAllowed(); 
	}

	public MembershipUser[] getUsers()
	{
		return membership.getUsers();
	}

	public MembershipUser findUser(long userId)
	{
		return membership.findUser(userId);
	}

	public MembershipUser findUser(String userName)
	{
		return membership.findUser(userName);
	}

	public void setPersistenceL1()
	{
		if(eventController == null)
			throw new IllegalStateException("Events are not supported.");
		eventController.setPersistenceL1();		
	}
	
	public void setPersistenceL1(long memoryStorageCapacity)
	{
		if(eventController == null)
			throw new IllegalStateException("Events are not supported.");
		eventController.setPersistenceL1(memoryStorageCapacity);				
	}
	
	public void setPersistenceL2()
	{
		if(eventController == null)
			throw new IllegalStateException("Events are not supported.");
		eventController.setPersistenceL2();
	}
	
	public void setPersistenceL2(String fileStorageDirectory, long fileStorageCapacity, long memoryStorageCapacity)
	{
		if(eventController == null)
			throw new IllegalStateException("Events are not supported.");
		eventController.setPersistenceL2(fileStorageDirectory, fileStorageCapacity, memoryStorageCapacity);
	}
		
	public void setPersistenceL2(ServicePersistence servicePersistence, long memoryStorageCapacity)
	{
		if(eventController == null)
			throw new IllegalStateException("Events are not supported.");
		eventController.setPersistenceL2(servicePersistence, memoryStorageCapacity);
	}
	
	public void addEventListener(ServiceEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.add(listener);
		}
		
		membership.addEventListener(listener);
		if(eventController != null)
			eventController.addEventListener(listener);
	}

	public void removeEventListener(ServiceEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.remove(listener);
		}
		
		membership.removeEventListener(listener);
		if(eventController != null)
			eventController.removeEventListener(listener);
	}

	public void raiseEvent(ReplacingEvent event)
	{
		if(eventController == null)
			throw new IllegalStateException("Events are not supported.");
		eventController.raiseEvent(event);
	}

	public void raiseEvent(QueueingEvent event)
	{
		if(eventController == null)
			throw new IllegalStateException("Events are not supported.");
		eventController.raiseEvent(event);
	}

	public void raiseEvent(PrivateEvent event)
	{
		if(eventController == null)
			throw new IllegalStateException("Events are not supported.");
		eventController.raiseEvent(event);
	}
	
	public void registerProcedure(String procedureName, RPCRequestHandler requestHandler, int concurrencyLimit)
	{
		rpcController.register(procedureName, requestHandler, concurrencyLimit);
	}
	
	public void registerProcedure(String procedureName, RPCRequestHandler requestHandler, int concurrencyLimit, GuestAccess guestAccess)
	{
		rpcController.register(procedureName, requestHandler, concurrencyLimit, guestAccess);
	}

	public void registerProcedure(String procedureName, RPCRequestHandler requestHandler, int concurrencyLimit, String roles)
	{
		rpcController.register(procedureName, requestHandler, concurrencyLimit, roles);
	}
	
	public void tcpListen(int virtualPort, TCPOptions tcpOptions, int backlog)
	{
		tcpController.listen(virtualPort, tcpOptions, backlog);
	}

	public void tcpListen(int virtualPort, TCPOptions tcpOptions, int backlog, String roles)
	{
		tcpController.listen(virtualPort, tcpOptions, backlog, roles);
	}

	public void tcpListen(int virtualPort, TCPOptions tcpOptions, int backlog, GuestAccess guestAccess)
	{
		tcpController.listen(virtualPort, tcpOptions, backlog, guestAccess);
	}

	public void tcpRelease(int virtualPort)
	{
		tcpController.releasePort(virtualPort);
	}
	
	public void tcpAccept(int virtualPort, TCPAcceptHandler acceptHandler)
	{
		tcpController.accept(virtualPort, acceptHandler);
	}
	
	public void udpListen(int virtualPort, int backlog)
	{
		udpController.listen(virtualPort, backlog);
	}

	public void udpListen(int virtualPort, int backlog, String roles)
	{
		udpController.listen(virtualPort, backlog, roles);
	}

	public void udpListen(int virtualPort, int backlog, GuestAccess guestAccess)
	{
		udpController.listen(virtualPort, backlog, guestAccess);
	}

	public void udpRelease(int virtualPort)
	{
		udpController.releasePort(virtualPort);
	}
	
	public void udpAccept(int virtualPort, UDPAcceptHandler acceptHandler)
	{
		udpController.accept(virtualPort, acceptHandler);
	}

	protected Object endpoint_mutex = new Object();
	protected ThreadPool threadPool;
	protected Scheduler scheduler;
	private StateController stateController;
	private Membership membership;
	private ServiceInstaller serviceInstaller;
	private TCPController tcpController;
	private UDPController udpController;
	private RPCController rpcController;
	private EventController eventController;
	private EndpointConnector endpointConnector;
	private HashSet<ServiceEventListener> eventListeners;
	
	private boolean is_closed = false;
	private EndpointConnectivity endpointConnectivity;

	private void EndpointConnector_onConnectedEvent(Channel channel)
	{
		stateController.onEndpointConnected(channel);
		serviceInstaller.onEndpointConnected(channel);
		membership.onEndpointConnected(channel);
		tcpController.onEndpointConnected(channel);
		udpController.onEndpointConnected(channel);
		rpcController.onEndpointConnected(channel);
		if(eventController != null)
			eventController.onEndpointConnected(channel);
	}

	private void EndpointConnector_onDisconnectedEvent()
	{
		serviceInstaller.onEndpointDisconnected();
		membership.onEndpointDisconnected();
		tcpController.onEndpointDisconnected();
		udpController.onEndpointDisconnected();
		if(eventController != null)
			eventController.onEndpointDisconnected();
	}	

	private void EndpointConnector_onClosed()
	{
		tcpController.onEndpointClosed();
		udpController.onEndpointClosed();
		if(eventController != null)
			eventController.onEndpointClosed();
	}	

	private void EndpointConnector_onConnectivityEvent(EndpointConnectivity endpointConnectivity)
	{		
		this.endpointConnectivity = endpointConnectivity;
		fireConnectivityEvent();
	}

	private void ServiceInstaller_onServiceOnline()
	{
		endpointConnector.onEndpointInstalled();
		membership.onServiceOnline();
		if(eventController != null)
			eventController.onServiceOnline();		
	}

	private void ServiceInstaller_onServiceParked()
	{
		endpointConnector.onEndpointInstalled();
	}
			
	private void fireConnectivityEvent()
	{
		final ServiceEndpointEvent event = new ServiceEndpointEvent(this);
		synchronized(eventListeners)
		{
			for (final ServiceEventListener listener: eventListeners)
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
		
	private void fireStatusEvent()
	{
		final ServiceEndpointEvent event = new ServiceEndpointEvent(this);		
		synchronized(eventListeners)
		{
			for (final ServiceEventListener listener: eventListeners)
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

	private void fireHostnameChangedEvent()
	{
		final ServiceEndpointEvent event = new ServiceEndpointEvent(this);
		synchronized(eventListeners)
		{
			for (final ServiceEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onHostnameChanged(event);
					}
				};
				threadPool.execute(runnable);
		    }
		}
	}
	
    private static void validateServiceVersionFormat(String version)
    {
		if(version.length() > 64)
			throw new IllegalArgumentException(String.format("The software version '%s' contains more than 64 characters.", version));

		if (Pattern.matches("^[\\u0020-\\u007F]+$", version) == false)			
            throw new IllegalArgumentException(String.format("The software version '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", version));
								
		if (Pattern.matches("^.*\\s\\s.*$", version))
			throw new IllegalArgumentException(String.format("The format of the software version '%s' is illegal. Two or more consecutive spaces are not allowed.", version));

		if (Pattern.matches("^[\\w\\s.$*+()#@%&=':\\^\\[\\]\\-/!]+$", version) == false)
            throw new IllegalArgumentException(String.format("The software version '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", version));    
    }
}
