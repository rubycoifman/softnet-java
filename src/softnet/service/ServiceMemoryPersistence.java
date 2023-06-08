package softnet.service;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import softnet.exceptions.PersistenceStorageFullSoftnetException;

class ServiceMemoryPersistence implements ServicePersistence
{
	public ServiceMemoryPersistence(long storageCapacity)
	{
		this.storageCapacity = storageCapacity;
		storageUid = UUID.randomUUID();
		storageMode = false;
		sepEvents = new LinkedList<ServiceEventPersistable>();
		replacingEvents = new LinkedList<REvent>();
		queueingEvents = new LinkedList<QEvent>();
		privateEvents = new LinkedList<PEvent>();
		isClosed = false;
	}
	
	private Object mutex = new Object();
	private UUID storageUid;
	private boolean storageMode;
	private long storageCapacity;
	private LinkedList<ServiceEventPersistable> sepEvents;
	private LinkedList<REvent> replacingEvents;
	private LinkedList<QEvent> queueingEvents;
	private LinkedList<PEvent> privateEvents;
	private boolean isClosed;
	
	public UUID getUid()
	{ 
		return storageUid; 
	}
	
	public void reset()
	{
		synchronized(mutex)
		{
			sepEvents.clear();
			replacingEvents.clear();
			queueingEvents.clear();
			privateEvents.clear();
		}
	}

	public void clear()
	{
		synchronized(mutex)
		{
			sepEvents.clear();
			replacingEvents.clear();
			queueingEvents.clear();
			privateEvents.clear();
		}
	}

	public void setStorageMode()
	{
		synchronized(mutex)
		{
			if(storageMode)
				return;			
			storageMode = true;
						
			queueingEvents.clear();
			privateEvents.clear();
		}
	}
	
	public boolean isInCacheMode()
	{
		return storageMode == false;
	}
	
	public boolean isInStorageMode()
	{
		return storageMode;		
	}
	
	public void invalidateAncientData() {}
	
	public void cache(ReplacingEvent replacingEvent) throws PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(storageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");
			
			ServiceEventPersistable sepEvent = null;
			if(replacingEvent.isNull == false)
				sepEvent = ServiceEventPersistable.createReplacingEvent(replacingEvent.name, replacingEvent.uid, replacingEvent.getEncoding());
			else
				sepEvent = ServiceEventPersistable.createReplacingNullEvent(replacingEvent.name, replacingEvent.uid);
			sepEvents.add(sepEvent);
			
			REvent rEvent = null;
			for(REvent listElement: replacingEvents)
			{
				if(listElement.name.equals(replacingEvent.name))
				{
					rEvent = listElement;
					break;
				}
			}		
			
			if(rEvent == null)
			{
				rEvent = new REvent(replacingEvent.name);
				replacingEvents.add(rEvent);				
				rEvent.currentInstance = sepEvent;
			}
			else if(rEvent.currentInstance == null)
			{
				rEvent.currentInstance = sepEvent;						
			}
			else if(rEvent.lastInstance == null)
			{
				rEvent.lastInstance = sepEvent;
			}
			else
			{
				sepEvents.remove(rEvent.lastInstance);				
				rEvent.lastInstance = sepEvent;
			}
		}
	}

	public void cache(QueueingEvent queueingEvent) throws PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(storageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");
			
			ServiceEventPersistable	sepEvent = ServiceEventPersistable.createQueueingEvent(queueingEvent.name, queueingEvent.uid, queueingEvent.getEncoding());
			sepEvents.add(sepEvent);
			
			QEvent qEvent = null;
			for(QEvent listElement: queueingEvents)
			{
				if(listElement.name.equals(queueingEvent.name))
				{
					qEvent = listElement;
					break;
				}
			}
			
			if(qEvent == null)
			{
				qEvent = new QEvent(queueingEvent.name);
				queueingEvents.add(qEvent);				
				qEvent.instanceQueue.add(sepEvent);
			}
			else 
			{
				qEvent.instanceQueue.add(sepEvent);
			}
		}
	}

	public void cache(PrivateEvent privateEvent) throws PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(storageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");
			
			ServiceEventPersistable	sepEvent = ServiceEventPersistable.createPrivateEvent(privateEvent.name, privateEvent.uid, privateEvent.clientId, privateEvent.getEncoding());
			sepEvents.add(sepEvent);
			
			PEvent pEvent = null;
			for(PEvent listElement: privateEvents)
			{
				if(listElement.name.equals(privateEvent.name))
				{
					pEvent = listElement;
					break;
				}
			}
			
			if(pEvent == null)
			{
				pEvent = new PEvent(privateEvent.name);
				privateEvents.add(pEvent);				
				pEvent.instanceQueue.add(sepEvent);
			}
			else 
			{
				pEvent.instanceQueue.add(sepEvent);
			}
		}
	}
	
	public ServiceEventPersistable setAcknowledment(int eventKind, String eventName)
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");

			if(storageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");			

			if(eventName == null)
				throw new NullPointerException("The value of eventName is null.");
			
			if(eventKind == 1)
			{
				REvent rEvent = null;
				for(REvent listElement: replacingEvents)
				{
					if(listElement.name.equals(eventName))
					{
						rEvent = listElement;
						break;
					}
				}		
				
				if(rEvent == null)
					throw new IllegalStateException(String.format("No event '%s' in the storage to be acknowledged.", eventName));

				if(rEvent.currentInstance == null)
					throw new IllegalStateException(String.format("No event '%s' in the storage to be acknowledged.", eventName));
				
				sepEvents.remove(rEvent.currentInstance);
				rEvent.currentInstance = null;
				
				if(rEvent.lastInstance == null)
					return null;
				
				rEvent.currentInstance = rEvent.lastInstance;
				rEvent.lastInstance = null;
				
				return rEvent.currentInstance;
			}
			else if(eventKind == 2)
			{
				QEvent qEvent = null;
				for(QEvent listElement: queueingEvents)
				{
					if(listElement.name.equals(eventName))
					{
						qEvent = listElement;
						break;
					}
				}		
				
				if(qEvent == null)
					throw new IllegalStateException(String.format("No event '%s' in the storage to be acknowledged.", eventName));

				if(qEvent.instanceQueue.isEmpty())
					throw new IllegalStateException(String.format("No event '%s' in the storage to be acknowledged.", eventName));
				
				ServiceEventPersistable sepEvent = qEvent.instanceQueue.remove();
				sepEvents.remove(sepEvent);
				
				if(qEvent.instanceQueue.isEmpty())
					return null;
				
				return qEvent.instanceQueue.element();
			}
			else if(eventKind == 4)
			{
				PEvent pEvent = null;
				for(PEvent listElement: privateEvents)
				{
					if(listElement.name.equals(eventName))
					{
						pEvent = listElement;
						break;
					}
				}		
				
				if(pEvent == null)
					throw new IllegalStateException(String.format("No event '%s' in the storage to be acknowledged.", eventName));

				if(pEvent.instanceQueue.isEmpty())
					throw new IllegalStateException(String.format("No event '%s' in the storage to be acknowledged.", eventName));
				
				ServiceEventPersistable sepEvent = pEvent.instanceQueue.remove();
				sepEvents.remove(sepEvent);
				
				if(pEvent.instanceQueue.isEmpty())
					return null;
				
				return pEvent.instanceQueue.element();
			}
			else 
				throw new IllegalArgumentException("The value of 'eventKind' is illegal.");
		}
	}
	
	public ServiceEventPersistable peek(int eventKind, String eventName)
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");

			if(storageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");			

			if(eventName == null)
				throw new NullPointerException("The value of eventName is null.");
			
			if(eventKind == 1)
			{
				REvent rEvent = null;
				for(REvent listElement: replacingEvents)
				{
					if(listElement.name.equals(eventName))
					{
						rEvent = listElement;
						break;
					}
				}		
				
				if(rEvent == null)
					return null;

				if(rEvent.currentInstance == null)
					return null;
				
				return rEvent.currentInstance;
			}
			else if(eventKind == 2)
			{
				QEvent qEvent = null;
				for(QEvent listElement: queueingEvents)
				{
					if(listElement.name.equals(eventName))
					{
						qEvent = listElement;
						break;
					}
				}		
				
				if(qEvent == null)
					return null;

				if(qEvent.instanceQueue.isEmpty())
					return null;
				
				return qEvent.instanceQueue.element();
			}
			else if(eventKind == 4)
			{
				PEvent pEvent = null;
				for(PEvent listElement: privateEvents)
				{
					if(listElement.name.equals(eventName))
					{
						pEvent = listElement;
						break;
					}
				}		
				
				if(pEvent == null)
					return null;

				if(pEvent.instanceQueue.isEmpty())
					return null;
				
				return pEvent.instanceQueue.element();
			}
			else 
				throw new IllegalArgumentException("The value of 'eventKind' is illegal.");
		}
	}
	
	public void save(ReplacingEvent replacingEvent) throws PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(storageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			ServiceEventPersistable sepEvent = null;
			if(replacingEvent.isNull == false)
				sepEvent = ServiceEventPersistable.createReplacingEvent(replacingEvent.name, replacingEvent.uid, replacingEvent.getEncoding());
			else
				sepEvent = ServiceEventPersistable.createReplacingNullEvent(replacingEvent.name, replacingEvent.uid);
			sepEvents.add(sepEvent);
			
			REvent rEvent = null;
			for(REvent listElement: replacingEvents)
			{
				if(listElement.name.equals(replacingEvent.name))
				{
					rEvent = listElement;
					break;
				}
			}		
			
			if(rEvent == null)
			{
				rEvent = new REvent(replacingEvent.name);
				replacingEvents.add(rEvent);				
				rEvent.lastInstance = sepEvent;
			}
			else if(rEvent.lastInstance == null)
			{
				rEvent.lastInstance = sepEvent;
			}
			else
			{
				sepEvents.remove(rEvent.lastInstance);				
				rEvent.lastInstance = sepEvent;
			}
		}
	}

	public void save(QueueingEvent queueingEvent) throws PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(storageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			ServiceEventPersistable	sepEvent = ServiceEventPersistable.createQueueingEvent(queueingEvent.name, queueingEvent.uid, queueingEvent.getEncoding());
			sepEvents.add(sepEvent);			
		}
	}

	public void save(PrivateEvent privateEvent) throws PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(storageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			ServiceEventPersistable	sepEvent = ServiceEventPersistable.createPrivateEvent(privateEvent.name, privateEvent.uid, privateEvent.clientId, privateEvent.getEncoding());
			sepEvents.add(sepEvent);
		}
	}
	
	public void setAcknowledment()
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");

			if(storageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			if(sepEvents.size() == 0)
				throw new IllegalStateException("No events in the storage to be acknowledged.");
			
			sepEvents.remove();
		}
	}
	
	public ServiceEventPersistable peek()
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");

			if(storageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");

			if(sepEvents.size() > 0)
				return sepEvents.element();

			replacingEvents.clear();
			storageMode = false;
			return null;
		}
	}
	
	public void close()
	{		
		synchronized(mutex)
		{
			isClosed = true;
			sepEvents.clear();
			replacingEvents.clear();
			queueingEvents.clear();
			privateEvents.clear();
		}
	}	
	
	private class REvent
	{
		public final String name;
		public ServiceEventPersistable currentInstance;
		public ServiceEventPersistable lastInstance;
		public REvent(String name)
		{
			this.name = name;
			currentInstance = null;
			lastInstance = null;
		}
	}

	private class QEvent
	{
		public final String name;
		public Queue<ServiceEventPersistable> instanceQueue;
		public QEvent(String name)
		{
			this.name = name;
			this.instanceQueue = new LinkedList<ServiceEventPersistable>();
		}
	}

	private class PEvent
	{
		public final String name;
		public Queue<ServiceEventPersistable> instanceQueue;
		public PEvent(String name)
		{
			this.name = name;
			this.instanceQueue = new LinkedList<ServiceEventPersistable>();
		}
	}
}
