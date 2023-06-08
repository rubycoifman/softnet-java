package softnet.client;

import java.util.HashSet;

import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;
import softnet.utils.*;

class SingleServiceGroup implements ServiceGroup
{
	public SingleServiceGroup(ClientEndpoint clientEndpoint, Object endpoint_mutex)
	{
		this.clientEndpoint = clientEndpoint;
		this.endpoint_mutex = endpoint_mutex;
		eventListeners = new HashSet<ClientEventListener>();
		remoteService = new RmtService();
		clientStatus = StatusEnum.Disconnected;
	}
	
	public BiAcceptor<Long, Channel> RemoteServiceOfflineCallback;
	
	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Client.SingleServiceGroup.ModuleId, 
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
		if(remoteService.isOnline())
			fireServiceOnlineEvent();
	}
	
	public void onEndpointDisconnected()
	{
		clientStatus = StatusEnum.Disconnected;
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
	
	public RemoteService findService(long serviceId)
	{
		synchronized(endpoint_mutex)
		{
			if(serviceId == 0)
				return remoteService;
		}
		return null;
	}
	
	public RemoteService findService(String hostname)
	{
		synchronized(endpoint_mutex)
		{
			if(hostname.equals(remoteService.getHostname()))
				return remoteService;
		}
		return null;
	}

	public RemoteService[] getServices()
	{
		synchronized(endpoint_mutex)
		{			
			return new RemoteService[] {remoteService};
		}
	}

	public RemoteService getService()
	{
		synchronized(endpoint_mutex)
		{
			return remoteService;
		}
	}
	
	public byte[] getHash() throws HostFunctionalitySoftnetException
	{
		synchronized(endpoint_mutex)
		{
    		ASNEncoder asnEncoder = new ASNEncoder();
            SequenceEncoder asnRootSequence = asnEncoder.Sequence();
            asnRootSequence.IA5String(remoteService.getHostname());
            asnRootSequence.IA5String(remoteService.getVersion());
            return Sha1Hash.compute(asnEncoder.getEncoding());
		}
	}

	private Object endpoint_mutex;

	private enum StatusEnum
	{ 
		Disconnected, Connected, Online
	}
	private StatusEnum clientStatus;
	
	private ClientEndpoint clientEndpoint;
	private HashSet<ClientEventListener> eventListeners;
	private RmtService remoteService;
	
	private void ProcessMessage_ServiceUpdated(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);				
		String version = asnRootSequence.IA5String(0, 64);
		String hostname = asnRootSequence.IA5String(1, 256);
		asnRootSequence.end();
		
		remoteService.setHostname(hostname);
		remoteService.setVersion(version);
		
		fireServiceUpdatedEvent();
	}

	private void ProcessMessage_ServiceOnline(byte[] message) throws AsnException, SoftnetException
	{
		remoteService.setOnline();				
		fireServiceOnlineEvent();
	}

	private void ProcessMessage_ServiceOnline1(byte[] message) throws AsnException, SoftnetException
	{
		remoteService.setOnline();
	}

	private void ProcessMessage_ServiceOnline2(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        String version = asnRootSequence.IA5String(0, 64);
        asnRootSequence.end();

		remoteService.setVersion(version);
		remoteService.setOnline();		
		fireServiceOnlineEvent();
	}

	private void ProcessMessage_ServiceOffline(byte[] message, Channel channel) throws AsnException, SoftnetException
	{
		remoteService.setOffline();		
        RemoteServiceOfflineCallback.accept(remoteService.getId(), channel);
		fireServiceOfflineEvent();
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
				if(messageTag == Constants.Client.SingleServiceGroup.SERVICE_ONLINE)
				{
					ProcessMessage_ServiceOnline(message);
				}
				else if(messageTag == Constants.Client.SingleServiceGroup.SERVICE_ONLINE_2)
				{
					ProcessMessage_ServiceOnline2(message);
				}
				else if(messageTag == Constants.Client.SingleServiceGroup.SERVICE_OFFLINE)
				{
					ProcessMessage_ServiceOffline(message, channel);
				}
				else if(messageTag == Constants.Client.SingleServiceGroup.SERVICE_UPDATED)
				{
					ProcessMessage_ServiceUpdated(message);
				}
				else
					throw new FormatException();
			}
			else if(clientStatus == StatusEnum.Connected)
			{
				if(messageTag == Constants.Client.SingleServiceGroup.SERVICE_ONLINE)
				{
					ProcessMessage_ServiceOnline1(message);
				}
				else if(messageTag == Constants.Client.SingleServiceGroup.SERVICE_UPDATED)
				{
					ProcessMessage_ServiceUpdated(message);
				}
				else
					throw new FormatException();
			}
		}
	}
	
	private void fireServiceOnlineEvent()
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
				clientEndpoint.threadPool.execute(runnable);
		    }
		}
	}

	private void fireServiceOfflineEvent()
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
				clientEndpoint.threadPool.execute(runnable);
		    }
		}
	}
		
	private void fireServiceUpdatedEvent()
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
						listener.onServiceUpdated(event);
					}
				};
				clientEndpoint.threadPool.execute(runnable);
		    }
		}
	}
		
	private class RmtService implements RemoteService
	{
		private boolean _isOnline;
		public boolean isOnline()
		{
			return _isOnline;
		}
		
		public long getId()
		{
			return 0;
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
				
		public boolean isRemoved()
		{
			return false;
		}

		public RmtService()
		{
			_isOnline = false;
			hostname = "";
			version = "";
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
	}
}
