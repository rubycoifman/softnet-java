package softnet.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;

class EventController
{ 
	public EventController(ServiceEndpoint serviceEndpoint, SiteStructureAdapter siteStructureAdapter, ServiceURI serviceURI)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.scheduler = serviceEndpoint.scheduler;
		this.siteStructure = siteStructureAdapter;
		this.serviceURI = serviceURI;
	}
	
	private Object mutex = new Object();

	private enum StatusEnum { 
		Disconnected, Connected, Online
	}
	private StatusEnum status;
	private boolean isClosed = false;
	private boolean isInitialized = false;
	private ServiceEndpoint serviceEndpoint;	
	private SiteStructureAdapter siteStructure;
	private ServiceURI serviceURI;
	private UUID lastStorageUid = null;
	private Channel channel;
	private Scheduler scheduler;
	private ArrayList<String> regularEvents;
	private ArrayList<DeliveryAgent> deliveryAgents; 
	private SuccessiveDeliveryAgent successiveDeliveryAgent;
	private ServicePersistence servicePersistence;
	private HashSet<ServiceEventListener> eventListeners;
	private long memoryBasedStorageCapacity = 16384;
	private static final long fileBasedStorageCapacity = 1048576;
	
	public void init()
	{
		status = StatusEnum.Disconnected;		
		eventListeners = new HashSet<ServiceEventListener>(1);
		
		regularEvents = new ArrayList<String>();	
		successiveDeliveryAgent = new SuccessiveDeliveryAgent();		
		
		int eventIndex = 0;
		deliveryAgents = new ArrayList<DeliveryAgent>();
		
		if(siteStructure.containsReplacingEvents())
		{
			ArrayList<SiteStructureAdapter.REvent> rEvents = siteStructure.getReplacingEvents();
			for(SiteStructureAdapter.REvent rEvent: rEvents)
			{
				regularEvents.add(rEvent.name);
				DeliveryAgent agent = new DeliveryAgent(rEvent.name, eventIndex, 1); 
				deliveryAgents.add(agent);
				eventIndex++;
			}
		}

		if(siteStructure.containsQueueingEvents())
		{
			ArrayList<SiteStructureAdapter.QEvent> qEvents = siteStructure.getQueueingEvents();
			for(SiteStructureAdapter.QEvent qEvent: qEvents)
			{
				regularEvents.add(qEvent.name);
				DeliveryAgent agent = new DeliveryAgent(qEvent.name, eventIndex, 2); 
				deliveryAgents.add(agent);
				eventIndex++;
			}
		}

		if(siteStructure.containsPrivateEvents())
		{
			ArrayList<SiteStructureAdapter.PEvent> pEvents = siteStructure.getPrivateEvents();
			for(SiteStructureAdapter.PEvent pEvent: pEvents)
			{
				regularEvents.add(pEvent.name);
				DeliveryAgent agent = new DeliveryAgent(pEvent.name, eventIndex, 4); 
				deliveryAgents.add(agent);
				eventIndex++;
			}
		}
	}

	public void setPersistenceL1()
	{
		synchronized(mutex)
		{
			if(isInitialized)
				throw new IllegalStateException("The persistence has already been set.");
			isInitialized = true;
									
			servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
			servicePersistence.setStorageMode();
		}
	}

	public void setPersistenceL1(long memoryBasedStorageCapacity)
	{
		synchronized(mutex)
		{
			if(isInitialized)
				throw new IllegalStateException("The persistence has already been set.");
			isInitialized = true;
						
			if(memoryBasedStorageCapacity != 0)
				this.memoryBasedStorageCapacity = memoryBasedStorageCapacity > 4096 ? memoryBasedStorageCapacity : 4096;
			
			servicePersistence = new ServiceMemoryPersistence(this.memoryBasedStorageCapacity); 
			servicePersistence.setStorageMode();
		}
	}
	
	public void setPersistenceL2()
	{
		synchronized(mutex)
		{
			if(isInitialized)
				throw new IllegalStateException("The persistence has already been set.");
			isInitialized = true;
			
			try
			{
				try
				{
					servicePersistence = ServiceFilePersistence.create(serviceURI.serviceUid, fileBasedStorageCapacity);
					servicePersistence.setStorageMode();			
				}
				catch(PersistenceDataFormatSoftnetException ex)
				{
					servicePersistence.reset();
					servicePersistence.setStorageMode();			
					raisePersistenceFailedEvent(ex);
				}
			}
			catch(PersistenceIOSoftnetException ex)
			{
				servicePersistence.close();
				servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
				servicePersistence.setStorageMode();			
				raisePersistenceFailedEvent(ex);
			}
		}
	}

	public void setPersistenceL2(String fileBasedStorageDirectory, long fileBasedStorageCapacity, long memoryBasedStorageCapacity)
	{
		synchronized(mutex)
		{
			if(isInitialized)
				throw new IllegalStateException("The persistence has already been set.");
			isInitialized = true;						
			
			if(memoryBasedStorageCapacity != 0)
				this.memoryBasedStorageCapacity = memoryBasedStorageCapacity > 4096 ? memoryBasedStorageCapacity : 4096;

			try
			{
				try
				{
					if(fileBasedStorageCapacity != 0 && fileBasedStorageCapacity < 8192)
						fileBasedStorageCapacity = 8192;
						
					if(fileBasedStorageDirectory == null || fileBasedStorageDirectory.length() == 0)
						servicePersistence = ServiceFilePersistence.create(serviceURI.serviceUid, fileBasedStorageCapacity);
					else
						servicePersistence = ServiceFilePersistence.create(serviceURI.serviceUid, fileBasedStorageCapacity, fileBasedStorageDirectory);
					servicePersistence.setStorageMode();
				}
				catch(PersistenceDataFormatSoftnetException ex)
				{
					servicePersistence.reset();
					servicePersistence.setStorageMode();			
					raisePersistenceFailedEvent(ex);
				}
			}
			catch(PersistenceIOSoftnetException ex)
			{
				servicePersistence.close();
				servicePersistence = new ServiceMemoryPersistence(this.memoryBasedStorageCapacity); 
				servicePersistence.setStorageMode();			
				raisePersistenceFailedEvent(ex);
			}
		}
	}

	public void setPersistenceL2(ServicePersistence servicePersistence, long memoryBasedStorageCapacity)
	{
		synchronized(mutex)
		{
			if(isInitialized)
				throw new IllegalStateException("The persistence has already been set.");

			if(servicePersistence == null)
				throw new IllegalArgumentException("The persistence implementation is not provided.");			

			isInitialized = true;

			if(memoryBasedStorageCapacity != 0)
				this.memoryBasedStorageCapacity = memoryBasedStorageCapacity > 4096 ? memoryBasedStorageCapacity : 4096;

			this.servicePersistence = servicePersistence;
			this.servicePersistence.setStorageMode();			
		}
	}
	
	public void onConnectCalled()
	{
		synchronized(mutex) {		
			if(isInitialized == false)
				throw new IllegalStateException("The persistence is not set.");
		}
	}
		
	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Service.EventController.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					onMessageReceived(message, _channel);
				}
			});		
		
		synchronized(mutex)
		{
			status = StatusEnum.Connected;
			this.channel = channel;
		}
	}

	public void onServiceOnline()
	{
		synchronized(mutex)
		{
			status = StatusEnum.Online;	
			
			if(lastStorageUid != null)
			{
				if(lastStorageUid.equals(servicePersistence.getUid()) == false)
				{
					try
					{						
						servicePersistence.invalidateAncientData();
					}
					catch(PersistenceIOSoftnetException ex)
					{
						servicePersistence.close();
						servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
						raisePersistenceFailedEvent(ex);						
					}			
					
					channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));			
				}
			}
			else
			{
				channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));			
			}
			
			try
			{				
				try
				{
					while(servicePersistence.isInStorageMode())
					{
						ServiceEventPersistable sep = servicePersistence.peek();
						if(sep == null)
							return;

						if(validateEventName(sep.name))
						{													
							Acceptor<Object> acceptor = new Acceptor<Object>()
							{
								public void accept(Object noData) { verifyEventDelivery(); }
							};
							
							successiveDeliveryAgent.task = new ScheduledTask(acceptor, null);
							successiveDeliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;
							successiveDeliveryAgent.instanceUid = sep.instanceUid;

							channel.send(encodeMessage_RegularEvent(sep, 0));
							scheduler.add(successiveDeliveryAgent.task, Constants.EventDeliverySeconds);
							
							return;
						}
						else
						{
							servicePersistence.setAcknowledment();
						}					
					}
				}
				catch(PersistenceDataFormatSoftnetException ex)
				{
					servicePersistence.reset();
					raisePersistenceFailedEvent(ex);
				}							
			}
			catch(PersistenceIOSoftnetException ex)
			{
				servicePersistence.close();
				raisePersistenceFailedEvent(ex);
				
				servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
				channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
			}
		}
	}

	public void onEndpointDisconnected()
	{
		synchronized(mutex)
		{
			status = StatusEnum.Disconnected;
			channel = null;
			lastStorageUid = null;
			servicePersistence.setStorageMode();
			resetDeliveryAgents();
			resetSuccessiveDeliveryAgent();
		}
	}
	
	public void onEndpointClosed()
	{
		synchronized(mutex)
		{
			isClosed = true;
			status = StatusEnum.Disconnected;
			servicePersistence.close();
			resetDeliveryAgents();
			resetSuccessiveDeliveryAgent();
		}
	}

	public void addEventListener(ServiceEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.add(listener);
		}
	}
	
	public void removeEventListener(ServiceEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.remove(listener);
		}
	}	
	
	public void raiseEvent(ReplacingEvent replacingEvent)
	{
		synchronized(mutex)
		{
			if(isClosed) return;
			
			if(isInitialized == false)
				throw new IllegalStateException("The persistence is not set.");
								
			if(status == StatusEnum.Online)
			{				
				DeliveryAgent deliveryAgent = findDeliveryAgent(1, replacingEvent.name);
				if(deliveryAgent == null)
					throw new IllegalArgumentException(String.format("The event name '%s' is illegal.", replacingEvent.name));
				
				if(servicePersistence.isInCacheMode())
				{					
					try
					{
						try
						{
							servicePersistence.cache(replacingEvent);
						}		
						catch(PersistenceIOSoftnetException ex)
						{
							servicePersistence.close();
							resetDeliveryAgents();
							raisePersistenceFailedEvent(ex);
							
							servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
							((ServiceMemoryPersistence)servicePersistence).cache(replacingEvent);
							channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
						}
						
						if(deliveryAgent.instanceUid == null)
						{
							Acceptor<Object> acceptor = new Acceptor<Object>()
							{
								public void accept(Object state) { verifyEventDelivery(state); }
							};
							deliveryAgent.task = new ScheduledTask(acceptor, deliveryAgent);
							deliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;							
							deliveryAgent.instanceUid = replacingEvent.uid;

							scheduler.add(deliveryAgent.task, Constants.EventDeliverySeconds);							
							channel.send(encodeMessage_ReplacingEvent(replacingEvent, deliveryAgent.index));
						}
					}
					catch(PersistenceStorageFullSoftnetException ex)
					{
						raisePersistenceFailedEvent(ex);						
					}
				}
				else
				{
					try
					{
						try
						{
							servicePersistence.save(replacingEvent);
						}		
						catch(PersistenceIOSoftnetException ex)
						{
							servicePersistence.close();
							resetSuccessiveDeliveryAgent();							
							raisePersistenceFailedEvent(ex);

							servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
							((ServiceMemoryPersistence)servicePersistence).cache(replacingEvent);
							channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
							
							Acceptor<Object> acceptor = new Acceptor<Object>()
							{
								public void accept(Object state) { verifyEventDelivery(state); }
							};
							deliveryAgent.task = new ScheduledTask(acceptor, deliveryAgent);
							deliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;							
							deliveryAgent.instanceUid = replacingEvent.uid;

							scheduler.add(deliveryAgent.task, Constants.EventDeliverySeconds);							
							channel.send(encodeMessage_ReplacingEvent(replacingEvent, deliveryAgent.index));
						}
					}
					catch(PersistenceStorageFullSoftnetException ex)
					{
						raisePersistenceFailedEvent(ex);
					}
				}
			}
			else
			{
				if(validateEventName(replacingEvent.name) == false)
					throw new IllegalArgumentException(String.format("The event name '%s' is illegal.", replacingEvent.name));
				
				try
				{
					try
					{
						servicePersistence.save(replacingEvent);
					}		
					catch(PersistenceIOSoftnetException ex)
					{
						servicePersistence.close();
						raisePersistenceFailedEvent(ex);

						servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
						servicePersistence.setStorageMode();
						((ServiceMemoryPersistence)servicePersistence).save(replacingEvent);												
					}
				}
				catch(PersistenceStorageFullSoftnetException ex)
				{
					raisePersistenceFailedEvent(ex);
				}
			}
		}
	}
	
	public void raiseEvent(QueueingEvent queueingEvent)
	{
		synchronized(mutex)
		{
			if(isClosed) return;			
			
			if(isInitialized == false)
				throw new IllegalStateException("The persistence is not set.");

			if(status == StatusEnum.Online)
			{				
				DeliveryAgent deliveryAgent = findDeliveryAgent(2, queueingEvent.name);
				if(deliveryAgent == null)
					throw new IllegalArgumentException(String.format("The event name '%s' is illegal.", queueingEvent.name));
				
				if(servicePersistence.isInCacheMode())
				{
					try
					{
						try
						{
							servicePersistence.cache(queueingEvent);
						}		
						catch(PersistenceIOSoftnetException ex)
						{
							servicePersistence.close();
							resetDeliveryAgents();
							raisePersistenceFailedEvent(ex);
							
							servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
							((ServiceMemoryPersistence)servicePersistence).cache(queueingEvent);
							channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
						}
						
						if(deliveryAgent.instanceUid == null)
						{
							Acceptor<Object> acceptor = new Acceptor<Object>()
							{
								public void accept(Object state) { verifyEventDelivery(state); }
							};
							deliveryAgent.task = new ScheduledTask(acceptor, deliveryAgent);
							deliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;							
							deliveryAgent.instanceUid = queueingEvent.uid;

							scheduler.add(deliveryAgent.task, Constants.EventDeliverySeconds);
							channel.send(encodeMessage_QueueingEvent(queueingEvent, deliveryAgent.index));
						}
					}
					catch(PersistenceStorageFullSoftnetException ex)
					{
						raisePersistenceFailedEvent(ex);
					}					
				}
				else
				{
					try
					{
						try
						{
							servicePersistence.save(queueingEvent);
						}		
						catch(PersistenceIOSoftnetException ex)
						{
							servicePersistence.close();
							resetSuccessiveDeliveryAgent();							
							raisePersistenceFailedEvent(ex);

							servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
							((ServiceMemoryPersistence)servicePersistence).cache(queueingEvent);
							channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
							
							Acceptor<Object> acceptor = new Acceptor<Object>()
							{
								public void accept(Object state) { verifyEventDelivery(state); }
							};
							deliveryAgent.task = new ScheduledTask(acceptor, deliveryAgent);
							deliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;							
							deliveryAgent.instanceUid = queueingEvent.uid;

							scheduler.add(deliveryAgent.task, Constants.EventDeliverySeconds);							
							channel.send(encodeMessage_QueueingEvent(queueingEvent, deliveryAgent.index));
						}
					}
					catch(PersistenceStorageFullSoftnetException ex)
					{
						raisePersistenceFailedEvent(ex);
					}
				}
			}
			else
			{
				if(validateEventName(queueingEvent.name) == false)
					throw new IllegalArgumentException(String.format("The event name '%s' is illegal.", queueingEvent.name));
				
				try
				{
					try
					{
						servicePersistence.save(queueingEvent);
					}		
					catch(PersistenceIOSoftnetException ex)
					{
						servicePersistence.close();
						raisePersistenceFailedEvent(ex);
						
						servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
						servicePersistence.setStorageMode();
						((ServiceMemoryPersistence)servicePersistence).save(queueingEvent);
					}
				}
				catch(PersistenceStorageFullSoftnetException ex)
				{
					raisePersistenceFailedEvent(ex);
				}
			}
		}
	}

	public void raiseEvent(PrivateEvent privateEvent)
	{
		synchronized(mutex)
		{
			if(isClosed) return;
								
			if(isInitialized == false)
				throw new IllegalStateException("The persistence is not set.");

			if(status == StatusEnum.Online)
			{		
				DeliveryAgent deliveryAgent = findDeliveryAgent(4, privateEvent.name);
				if(deliveryAgent == null)
					throw new IllegalArgumentException(String.format("The event name '%s' is illegal.", privateEvent.name));

				if(servicePersistence.isInCacheMode())
				{
					try
					{
						try
						{
							servicePersistence.cache(privateEvent);
						}		
						catch(PersistenceIOSoftnetException ex)
						{
							servicePersistence.close();
							resetDeliveryAgents();
							raisePersistenceFailedEvent(ex);

							servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
							((ServiceMemoryPersistence)servicePersistence).cache(privateEvent);
							channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
						}
						
						if(deliveryAgent.instanceUid == null)
						{
							Acceptor<Object> acceptor = new Acceptor<Object>()
							{
								public void accept(Object state) { verifyEventDelivery(state); }
							};
							deliveryAgent.task = new ScheduledTask(acceptor, deliveryAgent);
							deliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;							
							deliveryAgent.instanceUid = privateEvent.uid;

							scheduler.add(deliveryAgent.task, Constants.EventDeliverySeconds);							
							channel.send(encodeMessage_PrivateEvent(privateEvent, deliveryAgent.index));
						}						
					}
					catch(PersistenceStorageFullSoftnetException ex)
					{
						raisePersistenceFailedEvent(ex);
					}					
				}
				else
				{
					try
					{
						try
						{
							servicePersistence.save(privateEvent);
						}		
						catch(PersistenceIOSoftnetException ex)
						{
							servicePersistence.close();
							resetSuccessiveDeliveryAgent();
							raisePersistenceFailedEvent(ex);
							
							servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
							((ServiceMemoryPersistence)servicePersistence).cache(privateEvent);
							channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
							
							Acceptor<Object> acceptor = new Acceptor<Object>()
							{
								public void accept(Object state) { verifyEventDelivery(state); }
							};
							deliveryAgent.task = new ScheduledTask(acceptor, deliveryAgent);
							deliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;							
							deliveryAgent.instanceUid = privateEvent.uid;

							scheduler.add(deliveryAgent.task, Constants.EventDeliverySeconds);							
							channel.send(encodeMessage_PrivateEvent(privateEvent, deliveryAgent.index));
						}
					}
					catch(PersistenceStorageFullSoftnetException ex)
					{
						raisePersistenceFailedEvent(ex);
					}
				}
			}
			else
			{
				if(validateEventName(privateEvent.name) == false)
					throw new IllegalArgumentException(String.format("The event name '%s' is illegal.", privateEvent.name));
				
				try
				{
					try
					{
						servicePersistence.save(privateEvent);
					}		
					catch(PersistenceIOSoftnetException ex)
					{
						servicePersistence.close();
						raisePersistenceFailedEvent(ex);
						
						servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
						servicePersistence.setStorageMode();
						((ServiceMemoryPersistence)servicePersistence).save(privateEvent);
					}
				}
				catch(PersistenceStorageFullSoftnetException ex)
				{
					raisePersistenceFailedEvent(ex);
				}
			}
		}
	}

	private void verifyEventDelivery()
	{
		synchronized(mutex)
		{
			if(status != StatusEnum.Online)
				return;
			if(successiveDeliveryAgent.instanceUid == null || servicePersistence.isInCacheMode())
				return;
			
			try
			{
				try
				{
					long currentTime = SystemClock.seconds();
					
					if(successiveDeliveryAgent.expirationTime <= currentTime)
					{
						ServiceEventPersistable sep = servicePersistence.peek();
						if(sep == null || successiveDeliveryAgent.instanceUid.equals(sep.instanceUid) == false)
							throw new PersistenceIllegalStateSoftnetException();
																		
						Acceptor<Object> acceptor = new Acceptor<Object>()
						{
							public void accept(Object noData) { verifyEventDelivery(); }
						};
						successiveDeliveryAgent.task = new ScheduledTask(acceptor, null);
						successiveDeliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;
						scheduler.add(successiveDeliveryAgent.task, Constants.EventDeliverySeconds);
						
						channel.send(encodeMessage_RegularEvent(sep, 0));
					}
					else
					{
						Acceptor<Object> acceptor = new Acceptor<Object>()
						{
							public void accept(Object noData) { verifyEventDelivery(); }
						};
						successiveDeliveryAgent.task = new ScheduledTask(acceptor, null);						
						scheduler.add(successiveDeliveryAgent.task, successiveDeliveryAgent.expirationTime - currentTime);
					}
				}
				catch(PersistenceDataFormatSoftnetException ex)
				{
					servicePersistence.reset();
					resetSuccessiveDeliveryAgent();
					raisePersistenceFailedEvent(ex);
				}
			}
			catch(PersistenceIOSoftnetException | PersistenceIllegalStateSoftnetException ex)
			{
				servicePersistence.close();
				resetSuccessiveDeliveryAgent();
				servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
				channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
				raisePersistenceFailedEvent(ex);
			}			
		}
	}
	
	private void verifyEventDelivery(Object state)
	{
		DeliveryAgent deliveryAgent = (DeliveryAgent)state;
		synchronized(mutex)
		{
			if(status != StatusEnum.Online)
				return;
			if(deliveryAgent.instanceUid == null || servicePersistence.isInStorageMode())
				return;

			try
			{
				try
				{
					long currentTime = SystemClock.seconds();
					
					if(deliveryAgent.expirationTime <= currentTime)
					{
						ServiceEventPersistable sep = servicePersistence.peek(deliveryAgent.kind, deliveryAgent.name);							
						if(sep == null || deliveryAgent.instanceUid.equals(sep.instanceUid) == false)
							throw new PersistenceIllegalStateSoftnetException();
						
						Acceptor<Object> acceptor = new Acceptor<Object>()
						{
							public void accept(Object state) { verifyEventDelivery(state); }
						};
						deliveryAgent.task = new ScheduledTask(acceptor, deliveryAgent);
						deliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;
						scheduler.add(deliveryAgent.task, Constants.EventDeliverySeconds);
						
						channel.send(encodeMessage_RegularEvent(sep, deliveryAgent.index));
					}
					else
					{
						Acceptor<Object> acceptor = new Acceptor<Object>()
						{
							public void accept(Object state) { verifyEventDelivery(state); }
						};
						deliveryAgent.task = new ScheduledTask(acceptor, deliveryAgent);						
						scheduler.add(deliveryAgent.task, deliveryAgent.expirationTime - currentTime);
					}
				}
				catch(PersistenceDataFormatSoftnetException ex)
				{
					servicePersistence.reset();
					resetDeliveryAgents();	
					raisePersistenceFailedEvent(ex);
				}
			}
			catch(PersistenceIOSoftnetException | PersistenceIllegalStateSoftnetException ex)
			{
				servicePersistence.close();
				resetDeliveryAgents();
				servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
				channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
				raisePersistenceFailedEvent(ex);
			}
		}
	}
		
	private void processMessage_EventAck(byte[] message, Channel channel) throws AsnException, SoftnetException
	{		
		SequenceDecoder sequenceDecoder = ASNDecoder.Sequence(message, 2);		
		int index = sequenceDecoder.Int32();
		UUID instanceUid = sequenceDecoder.OctetStringToUUID();
		sequenceDecoder.end();		
		
		if(servicePersistence.isInCacheMode())
		{
			if(!(0 <= index && index < deliveryAgents.size()))
				throw new InputDataFormatSoftnetException();
			
			DeliveryAgent deliveryAgent = deliveryAgents.get(index);
			if(deliveryAgent.instanceUid == null)
				return;
			if(instanceUid.equals(deliveryAgent.instanceUid) == false)
				return;
						
			try
			{
				try
				{
					ServiceEventPersistable sep = servicePersistence.setAcknowledment(deliveryAgent.kind, deliveryAgent.name);
					if(sep != null)
					{
						deliveryAgent.instanceUid = sep.instanceUid;
						deliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;
						
						channel.send(encodeMessage_RegularEvent(sep, deliveryAgent.index));					
					}
					else
					{
						deliveryAgent.instanceUid = null;
						deliveryAgent.task.cancel();
						deliveryAgent.task = null;						
					}
				}
				catch(PersistenceDataFormatSoftnetException ex)
				{
					servicePersistence.reset();
					resetDeliveryAgents();
					raisePersistenceFailedEvent(ex);
				}
			}
			catch(PersistenceIOSoftnetException ex)
			{
				servicePersistence.close();
				resetDeliveryAgents();
				servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
				channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
				raisePersistenceFailedEvent(ex);
			}			
		}
		else
		{
			if(successiveDeliveryAgent.instanceUid == null)
				return;
			if(instanceUid.equals(successiveDeliveryAgent.instanceUid) == false)
				return;
			
			successiveDeliveryAgent.instanceUid = null;
			
			try
			{
				try
				{
					servicePersistence.setAcknowledment();
					
					while(servicePersistence.isInStorageMode())
					{					
						ServiceEventPersistable sep = servicePersistence.peek();
						if(sep == null)
							break;
						if(validateEventName(sep.name))
						{
							successiveDeliveryAgent.instanceUid = sep.instanceUid;
							successiveDeliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;
							
							channel.send(encodeMessage_RegularEvent(sep, 0));
							return;
						}
						else
						{
							servicePersistence.setAcknowledment();
						}
					}
					
					if(servicePersistence.isInCacheMode())
					{
						successiveDeliveryAgent.task.cancel();
						successiveDeliveryAgent.task = null;
					}
				}
				catch(PersistenceDataFormatSoftnetException ex)
				{
					servicePersistence.reset();
					resetSuccessiveDeliveryAgent();
					raisePersistenceFailedEvent(ex);
				}
			}
			catch(PersistenceIOSoftnetException ex)
			{
				servicePersistence.close();
				resetSuccessiveDeliveryAgent();
				servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
				channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
				raisePersistenceFailedEvent(ex);
			}
		}
	}

	private void processMessage_IllegalEventName(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder sequenceDecoder = ASNDecoder.Sequence(message, 2);		
		int index = sequenceDecoder.Int32();
		UUID instanceUid = sequenceDecoder.OctetStringToUUID();
		sequenceDecoder.end();
				
		if(servicePersistence.isInCacheMode())
		{
			if(!(0 <= index && index < deliveryAgents.size()))
				throw new InputDataFormatSoftnetException();
			
			DeliveryAgent deliveryAgent = deliveryAgents.get(index);
			if(deliveryAgent.instanceUid == null)
				return;
			if(instanceUid.equals(deliveryAgent.instanceUid) == false)
				return;
			
			try
			{
				try
				{
					ServiceEventPersistable sep = servicePersistence.setAcknowledment(deliveryAgent.kind, deliveryAgent.name);
					if(sep != null)
					{
						deliveryAgent.instanceUid = sep.instanceUid;
						deliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;
						
						channel.send(encodeMessage_RegularEvent(sep, deliveryAgent.index));					
					}
					else
					{
						deliveryAgent.instanceUid = null;
						deliveryAgent.task.cancel();
						deliveryAgent.task = null;
					}
				}
				catch(PersistenceDataFormatSoftnetException ex)
				{
					servicePersistence.reset();
					resetDeliveryAgents();
					raisePersistenceFailedEvent(ex);
				}
			}
			catch(PersistenceIOSoftnetException ex)
			{
				servicePersistence.close();
				resetDeliveryAgents();
				servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
				channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
				raisePersistenceFailedEvent(ex);
			}
		}
		else
		{
			if(successiveDeliveryAgent.instanceUid == null)
				return;
			if(instanceUid.equals(successiveDeliveryAgent.instanceUid) == false)
				return;
			
			successiveDeliveryAgent.instanceUid = null;
			
			try
			{
				try
				{
					servicePersistence.setAcknowledment();
					
					while(servicePersistence.isInStorageMode())
					{					
						ServiceEventPersistable sep = servicePersistence.peek();
						if(sep == null)
							break;
						if(validateEventName(sep.name))
						{
							successiveDeliveryAgent.instanceUid = sep.instanceUid;
							successiveDeliveryAgent.expirationTime = SystemClock.seconds() + Constants.EventDeliverySeconds;
							
							channel.send(encodeMessage_RegularEvent(sep, 0));
							return;
						}
						else
						{
							servicePersistence.setAcknowledment();
						}					
					}
					
					if(servicePersistence.isInCacheMode())
					{
						successiveDeliveryAgent.task.cancel();
						successiveDeliveryAgent.task = null;
					}
				}
				catch(PersistenceDataFormatSoftnetException ex)
				{
					servicePersistence.reset();
					resetSuccessiveDeliveryAgent();
					raisePersistenceFailedEvent(ex);
				}
			}
			catch(PersistenceIOSoftnetException ex)
			{
				servicePersistence.close();
				resetSuccessiveDeliveryAgent();
				servicePersistence = new ServiceMemoryPersistence(memoryBasedStorageCapacity); 
				channel.send(encodeMessage_NewStorageUid(servicePersistence.getUid()));
				raisePersistenceFailedEvent(ex);
			}
		}
	}

	private void processMessage_LastStorageUid(byte[] message) throws AsnException
	{
		SequenceDecoder sequenceDecoder = ASNDecoder.Sequence(message, 2);		
		lastStorageUid = sequenceDecoder.OctetStringToUUID();
		sequenceDecoder.end();		
	}

	private SoftnetMessage encodeMessage_ReplacingEvent(ReplacingEvent rEvent, int index)
	{
		if(rEvent.isNull == false)
		{
			ASNEncoder asnEncoder = new ASNEncoder();
	        SequenceEncoder asnSequence = asnEncoder.Sequence();
	        asnSequence.IA5String(rEvent.name);
	        asnSequence.Int32(index);
	        asnSequence.OctetString(rEvent.uid);
		        
	        byte[] argumentsEncoding = rEvent.getEncoding();
	        if(argumentsEncoding != null)
	        	asnSequence.OctetString(1, argumentsEncoding);
	        
	        return MsgBuilder.Create(Constants.Service.EventController.ModuleId, Constants.Service.EventController.REPLACING_EVENT, asnEncoder);
		}
		else
		{
			ASNEncoder asnEncoder = new ASNEncoder();
	        SequenceEncoder asnSequence = asnEncoder.Sequence();
	        asnSequence.IA5String(rEvent.name);
	        asnSequence.Int32(index);
	        asnSequence.OctetString(rEvent.uid);
	        
	        return MsgBuilder.Create(Constants.Service.EventController.ModuleId, Constants.Service.EventController.REPLACING_NULL_EVENT, asnEncoder);
		}
	}
	
	private SoftnetMessage encodeMessage_QueueingEvent(QueueingEvent qEvent, int index)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.IA5String(qEvent.name);
        asnSequence.Int32(index);
        asnSequence.OctetString(qEvent.uid);
        
        byte[] argumentsEncoding = qEvent.getEncoding();
        if(argumentsEncoding != null)
        	asnSequence.OctetString(1, argumentsEncoding);
        
        return MsgBuilder.Create(Constants.Service.EventController.ModuleId, Constants.Service.EventController.QUEUEING_EVENT, asnEncoder);
	}

	private SoftnetMessage encodeMessage_PrivateEvent(PrivateEvent pEvent, int index)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.IA5String(pEvent.name);
        asnSequence.Int32(index);
        asnSequence.OctetString(pEvent.uid);
        asnSequence.Int64(pEvent.clientId);
        
        byte[] argumentsEncoding = pEvent.getEncoding();
        if(argumentsEncoding != null)
        	asnSequence.OctetString(1, argumentsEncoding);
                
        return MsgBuilder.Create(Constants.Service.EventController.ModuleId, Constants.Service.EventController.PRIVATE_EVENT, asnEncoder);
	}

	private SoftnetMessage encodeMessage_RegularEvent(ServiceEventPersistable sep, int index)
	{
		if(sep.kind == 1)
		{
			if(sep.isNull == false)
			{
				ASNEncoder asnEncoder = new ASNEncoder();
		        SequenceEncoder asnSequence = asnEncoder.Sequence();
		        asnSequence.IA5String(sep.name);
		        asnSequence.Int32(index);
		        asnSequence.OctetString(sep.instanceUid);
		
		        if(sep.argumentsEncoding != null)
		        	asnSequence.OctetString(1, sep.argumentsEncoding);
		        		        
		        return MsgBuilder.Create(Constants.Service.EventController.ModuleId, Constants.Service.EventController.REPLACING_EVENT, asnEncoder);
			}
			else
			{
				ASNEncoder asnEncoder = new ASNEncoder();
		        SequenceEncoder asnSequence = asnEncoder.Sequence();
		        asnSequence.IA5String(sep.name);
		        asnSequence.Int32(index);
		        asnSequence.OctetString(sep.instanceUid);
		
		        return MsgBuilder.Create(Constants.Service.EventController.ModuleId, Constants.Service.EventController.REPLACING_NULL_EVENT, asnEncoder);				
			}
		}
		else if(sep.kind == 2)
		{
			ASNEncoder asnEncoder = new ASNEncoder();
	        SequenceEncoder asnSequence = asnEncoder.Sequence();
	        asnSequence.IA5String(sep.name);
	        asnSequence.Int32(index);
	        asnSequence.OctetString(sep.instanceUid);
	
	        if(sep.argumentsEncoding != null)
	        	asnSequence.OctetString(1, sep.argumentsEncoding);
	        
	        return MsgBuilder.Create(Constants.Service.EventController.ModuleId, Constants.Service.EventController.QUEUEING_EVENT, asnEncoder);
		}
		else // sep.kind == 4 
		{
			ASNEncoder asnEncoder = new ASNEncoder();
	        SequenceEncoder asnSequence = asnEncoder.Sequence();
	        asnSequence.IA5String(sep.name);
	        asnSequence.Int32(index);
	        asnSequence.OctetString(sep.instanceUid);
	        asnSequence.Int64(sep.clientId);
	        
	        if(sep.argumentsEncoding != null)
	        	asnSequence.OctetString(1, sep.argumentsEncoding);
	        
	        return MsgBuilder.Create(Constants.Service.EventController.ModuleId, Constants.Service.EventController.PRIVATE_EVENT, asnEncoder);
		}
	}
	
	private SoftnetMessage encodeMessage_NewStorageUid(UUID storageUid)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.OctetString(storageUid);        
        return MsgBuilder.Create(Constants.Service.EventController.ModuleId, Constants.Service.EventController.NEW_STORAGE_UID, asnEncoder);
	}

	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
	{
		synchronized(mutex)
		{
			if(channel.isClosed()) 
				return;
			
			byte messageTag = message[1]; 
			if(status == StatusEnum.Online)
			{	
				if(messageTag == Constants.Service.EventController.EVENT_ACK)
				{
					processMessage_EventAck(message, channel);	
				}								
				else if(messageTag == Constants.Service.EventController.ILLEGAL_EVENT_NAME)
				{
					processMessage_IllegalEventName(message);
				}
				else
					throw new FormatException();
			}
			else if(status == StatusEnum.Connected)
			{
				if(messageTag == Constants.Service.EventController.LAST_STORAGE_UID)
				{
					processMessage_LastStorageUid(message);
				}
				else
					throw new FormatException();
			}
			else
				throw new FormatException();
		}
	}
	
	private void raisePersistenceFailedEvent(PersistenceSoftnetException ex)
	{
		final ServicePersistenceFailedEvent event = new ServicePersistenceFailedEvent(ex, serviceEndpoint);		
		synchronized(eventListeners)
		{
			for (final ServiceEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onPersistenceFailed(event);
					}
				};
				serviceEndpoint.threadPool.execute(runnable);
		    }
		}
	}
	
	private class DeliveryAgent
	{
		public final String name;
		public final int index;
		public final int kind;
		public UUID instanceUid;
		public long expirationTime;
		public ScheduledTask task;
		
		public DeliveryAgent(String name, int index, int kind)
		{
			this.name = name;
			this.index = index;
			this.kind = kind;
			instanceUid = null;
		}		
	}
	
	private class SuccessiveDeliveryAgent
	{
		public UUID instanceUid;
		public long expirationTime;
		public ScheduledTask task;
	}
	
	private void resetDeliveryAgents()
	{
		for(DeliveryAgent agent: deliveryAgents)
		{
			agent.instanceUid = null;
			if(agent.task != null)
			{
				agent.task.cancel();
				agent.task = null;
			}
		}
	}
	
	private void resetSuccessiveDeliveryAgent()
	{
		successiveDeliveryAgent.instanceUid = null;
		if(successiveDeliveryAgent.task != null)
		{
			successiveDeliveryAgent.task.cancel();
			successiveDeliveryAgent.task = null;
		}
	}
	
	private boolean validateEventName(String name)
	{
		for(String eventName: regularEvents)
			if(eventName.equals(name))
				return true;
		return false;		
	}
	
	private DeliveryAgent findDeliveryAgent(int kind, String name)
	{
		for(DeliveryAgent aEvent: deliveryAgents)
			if(aEvent.kind == kind && aEvent.name.equals(name))
				return aEvent;
		return null;
	}
}










