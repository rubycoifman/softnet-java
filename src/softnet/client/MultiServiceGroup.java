package softnet.client;

import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;

import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;
import softnet.utils.Sha1Hash;

class MultiServiceGroup implements ServiceGroup
{
	public MultiServiceGroup(ClientEndpoint clientEndpoint)
	{
		this.clientEndpoint = clientEndpoint;
		this.threadPool = clientEndpoint.threadPool;
		this.endpoint_mutex = clientEndpoint.endpoint_mutex;
		eventListeners = new HashSet<ClientEventListener>();
		remoteServices = new TreeSet<RmtService>();
	}
	
	public BiAcceptor<Long, Channel> remoteServiceOfflineCallback;
	
	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Client.MultiServiceGroup.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					onMessageReceived(message, _channel);
				}
			});		
		
		clientStatus = StatusEnum.Connected;
	}

	public void onClientOnline()
	{
		clientStatus = StatusEnum.Online;
	}
	
	public void onEndpointDisconnected()
	{
		clientStatus = StatusEnum.Disconnected;
	}
	
	public byte[] getHash() throws HostFunctionalitySoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(remoteServices.size() == 0)
				return null;
			
			ASNEncoder asnEncoder = new ASNEncoder();
	        SequenceEncoder asnRootSequence = asnEncoder.Sequence();	
	        for (RmtService service: remoteServices)
	        {
	            SequenceEncoder asnService = asnRootSequence.Sequence();
	            asnService.Int64(service.getId());
	            asnService.IA5String(service.getHostname());
	            asnService.IA5String(service.getVersion());
	        }	
	        return Sha1Hash.compute(asnEncoder.getEncoding());
		}
	}
	
	public RemoteService findService(long serviceId)
	{
		synchronized(endpoint_mutex)
		{
			for(RmtService service: remoteServices)
			{
				if(service.getId() == serviceId)
					return service;
			}
		}
		return null;			
	}
	
	public RemoteService findService(String hostname)
	{
		synchronized(endpoint_mutex)
		{
			for(RmtService service: remoteServices)
			{
				if(hostname.equals(service.getHostname()))
					return service;
			}
		}
		return null;
	}

	public RemoteService[] getServices()
	{		
		synchronized(endpoint_mutex)
		{			
			RemoteService[] services = new RemoteService[remoteServices.size()];
			remoteServices.toArray(services);
			return services;
		}
	}
	
	public void addEventListener(ClientEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.add(listener);
		}		
	}

	public void removeEventListener(ClientEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.remove(listener);
		}		
	}

	private Object endpoint_mutex;

	private enum StatusEnum
	{ 
		Disconnected, Connected, Online
	}
	private StatusEnum clientStatus = StatusEnum.Disconnected;
	
	private ClientEndpoint clientEndpoint;
	private ThreadPool threadPool;
	private HashSet<ClientEventListener> eventListeners;
	private SortedSet<RmtService> remoteServices;
	
	private RmtService findRemoteService(long serviceId)
	{
		for(RmtService service: remoteServices)
		{
			if(service.getId() == serviceId)
				return service;
		}
		return null;		
	}
	
	private void ProcessMessage_ServicesUpdated(byte[] message) throws AsnException, SoftnetException
	{
		SortedSet<RmtService> updatedServices = new TreeSet<RmtService>();
		
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
		while(asnRootSequence.hasNext())
		{
			SequenceDecoder asnService = asnRootSequence.Sequence();
			long serviceId = asnService.Int64();
			String hostname = asnService.IA5String(0, 256);
			String version = asnService.IA5String(0, 64);
			asnService.end();
			
			RmtService remoteService = findRemoteService(serviceId);
			if(remoteService != null)
			{
				remoteServices.remove(remoteService);
				remoteService.setHostname(hostname);
				remoteService.setVersion(version);
			}
			else
			{
				remoteService = new RmtService(serviceId, hostname, version);
			}

			updatedServices.add(remoteService);
		}
		asnRootSequence.end();
		
        for (RmtService service: remoteServices)
        	service.setRemoved();

        this.remoteServices = updatedServices;
        fireServicesUpdatedEvent();
	}
		
	private void ProcessMessage_ServicesOnline(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
		while(asnRootSequence.hasNext())
		{
			long serviceId = asnRootSequence.Int64();
			RmtService remoteService = findRemoteService(serviceId);
			if(remoteService == null)
				throw new InputDataInconsistentSoftnetException();
			remoteService.setOnline();
		}
        asnRootSequence.end();			
	}
	
	private void ProcessMessage_ServiceIncluded(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
		long serviceId = asnRootSequence.Int64();
		String hostname = asnRootSequence.IA5String(0, 256);
		String version = asnRootSequence.IA5String(0, 64);
        asnRootSequence.end();
                
		if(findRemoteService(serviceId) != null)
			throw new InputDataInconsistentSoftnetException();
		
		RmtService remoteService = new RmtService(serviceId, hostname, version);		
		remoteServices.add(remoteService);
		
        fireServiceIncludedEvent(remoteService);
	}
	
	private void ProcessMessage_ServiceRemoved(byte[] message, Channel channel) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        long serviceId = asnRootSequence.Int64();
        asnRootSequence.end();
			
        RmtService remoteService = findRemoteService(serviceId);
		if(remoteService == null)
			throw new InputDataInconsistentSoftnetException();
		
		remoteServices.remove(remoteService);
		remoteService.setRemoved();
	
        fireServiceRemovedEvent(remoteService);
	}
	
	private void ProcessMessage_ServiceUpdated(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        long serviceId = asnRootSequence.Int64();
        String version = asnRootSequence.IA5String(0, 64);
        String hostname = asnRootSequence.IA5String(0, 256);        
        asnRootSequence.end();
        
		RmtService remoteService = findRemoteService(serviceId);
		if(remoteService == null)
			throw new InputDataInconsistentSoftnetException();
		remoteService.setVersion(version);
		remoteService.setHostname(hostname);			

		fireServiceUpdatedEvent(remoteService);
	}

	private void ProcessMessage_ServiceOnline(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        long serviceId = asnRootSequence.Int64();
        asnRootSequence.end();
        
        RmtService remoteService = findRemoteService(serviceId);
		if(remoteService == null)
			throw new InputDataInconsistentSoftnetException();
		remoteService.setOnline();
		
        fireServiceOnlineEvent(remoteService);
	}

	private void ProcessMessage_ServiceOnline2(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        long serviceId = asnRootSequence.Int64();
        String version = asnRootSequence.IA5String(0, 64);
        asnRootSequence.end();
        
		RmtService remoteService = findRemoteService(serviceId);
		if(remoteService == null)
			throw new InputDataInconsistentSoftnetException();
		remoteService.setVersion(version);
		remoteService.setOnline();
		
        fireServiceOnlineEvent(remoteService);
	}
	
	private void ProcessMessage_ServiceOffline(byte[] message, Channel channel) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        long serviceId = asnRootSequence.Int64();
        asnRootSequence.end();

        RmtService remoteService = findRemoteService(serviceId);
		if(remoteService == null)
			throw new InputDataInconsistentSoftnetException();
		remoteService.setOffline();			
	
        remoteServiceOfflineCallback.accept(remoteService.getId(), channel);
        fireServiceOfflineEvent(remoteService);        
	}
	
	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(channel.closed())
				return;
			
			byte messageTag = message[1]; 
			if(clientStatus == StatusEnum.Online)
			{	
				if(messageTag == Constants.Client.MultiServiceGroup.SERVICE_ONLINE)
				{
					ProcessMessage_ServiceOnline(message);
				}
				else if(messageTag == Constants.Client.MultiServiceGroup.SERVICE_ONLINE_2)
				{
					ProcessMessage_ServiceOnline2(message);
				}
				else if(messageTag == Constants.Client.MultiServiceGroup.SERVICE_OFFLINE)
				{
					ProcessMessage_ServiceOffline(message, channel);
				}
				else if(messageTag == Constants.Client.MultiServiceGroup.SERVICE_INCLUDED)
				{
					ProcessMessage_ServiceIncluded(message);
				}
				else if(messageTag == Constants.Client.MultiServiceGroup.SERVICE_REMOVED)
				{
					ProcessMessage_ServiceRemoved(message, channel);
				}
				else if(messageTag == Constants.Client.MultiServiceGroup.SERVICE_UPDATED)
				{
					ProcessMessage_ServiceUpdated(message);
				}
				else 
					throw new FormatException();
			}
			else if(clientStatus == StatusEnum.Connected)
			{
				if(messageTag == Constants.Client.MultiServiceGroup.SERVICES_UPDATED)
				{
					ProcessMessage_ServicesUpdated(message);
				}
				else if(messageTag == Constants.Client.MultiServiceGroup.SERVICES_ONLINE)
				{
					ProcessMessage_ServicesOnline(message);
				}
				else 
					throw new FormatException();
			}
		}
	}
		
	private void fireServiceOnlineEvent(RmtService remoteService)
	{
		final RemoteServiceEvent event = new RemoteServiceEvent(remoteService, clientEndpoint);		
		synchronized(eventListeners)
		{
			for (final ClientEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onServiceOnline(event);
					}
				};
				threadPool.execute(runnable);
		    }
		}
	}

	private void fireServiceOfflineEvent(RmtService remoteService)
	{
		final RemoteServiceEvent event = new RemoteServiceEvent(remoteService, clientEndpoint);		
		synchronized(eventListeners)
		{
			for (final ClientEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onServiceOffline(event);
					}
				};
				threadPool.execute(runnable);
		    }
		}
	}	
	
	private void fireServiceIncludedEvent(RmtService remoteService)
	{
		final RemoteServiceEvent event = new RemoteServiceEvent(remoteService, clientEndpoint);		
		synchronized(eventListeners)
		{
			for (final ClientEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onServiceIncluded(event);
					}
				};
				threadPool.execute(runnable);
		    }
		}
	}
	
	private void fireServiceRemovedEvent(RmtService remoteService)
	{
		final RemoteServiceEvent event = new RemoteServiceEvent(remoteService, clientEndpoint);		
		synchronized(eventListeners)
		{
			for (final ClientEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onServiceRemoved(event);
					}
				};
				threadPool.execute(runnable);
		    }
		}
	}
	
	private void fireServiceUpdatedEvent(RmtService remoteService)
	{
		final RemoteServiceEvent event = new RemoteServiceEvent(remoteService, clientEndpoint);		
		synchronized(eventListeners)
		{
			for (final ClientEventListener listener: eventListeners)
			{
				Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onServiceUpdated(event);
					}
				};
				threadPool.execute(runnable);
		    }
		}
	}
	
	private void fireServicesUpdatedEvent()
	{
		final ClientEndpointEvent event = new ClientEndpointEvent(clientEndpoint);		
		synchronized(eventListeners)
		{
			for (final ClientEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onServicesUpdated(event);
					}
				};
				threadPool.execute(runnable);
		    }
		}
	}
	
	private class RmtService implements RemoteService, Comparable<RmtService>
	{
		private long serviceId;
		public long getId()
		{
			return serviceId;
		}
		
		private boolean _isOnline;
		public boolean isOnline()
		{
			return _isOnline;
		}
		
		private String hostname;
		public String getHostname()
		{
			return hostname;
		}
		
		String version;
		public String getVersion()
		{
			return version;
		}
		
		private boolean _isRemoved;
		public boolean isRemoved()
		{
			return _isRemoved;
		}

		public RmtService(long serviceId, String hostname, String version)
		{
			this.serviceId = serviceId;
			this.hostname = hostname;
			this.version = version;
			_isOnline = false;
			_isRemoved = false;
		}

		public void setOnline()
		{
			_isOnline = true;
		}

		public void setOffline()
		{
			_isOnline = false;
		}

		public void setHostname(String value)
		{
			hostname = value;
		}
		
		public void setVersion(String value)
		{
			version = value;
		}
		
		public void setRemoved()
		{
			_isOnline = false;
			_isRemoved = true;
		}

		public int compareTo(RmtService other)
        {
            if (this.serviceId > other.serviceId)
            {
                return 1;
            }
            else if (this.serviceId < other.serviceId)
            {
                return -1;
            }
            else return 0;
        }
        
        @Override
        public boolean equals(Object other) 
        {
        	if (other == null) return false;
            RmtService service = (RmtService)other;
            return this.serviceId == service.serviceId;
        }
        
        @Override
        public int hashCode() 
        {
            return Long.hashCode(this.serviceId);
        }
	}	
}
