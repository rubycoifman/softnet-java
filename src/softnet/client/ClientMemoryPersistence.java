package softnet.client;

import java.util.ArrayList;

public class ClientMemoryPersistence implements ClientPersistence
{
	public ClientMemoryPersistence()
	{
		records = new ArrayList<Record>();
	}
	
	public void reset()
	{
		synchronized(mutex)
		{
			records.clear();
		}	
	}
		
	public void close()
	{
		synchronized(mutex)
		{
			isClosed = true;
			records.clear();
		}		
	}
	
	private Object mutex = new Object();
	private ArrayList<Record> records;
	private boolean isClosed = false;
	
	public ClientEventPersistable getItem(String name)
	{
		synchronized(mutex)
		{
			if(isClosed)
				throw new IllegalStateException("The storage has been closed.");
			
			for(Record record: records)
			{
				if(record.name.equals(name))
					return new ClientEventPersistable(record.name, record.instanceId);
			}
			return null;
		}
	}

	public void putItem(String name, long instanceId)
	{
		synchronized(mutex)
		{
			if(isClosed)
				throw new IllegalStateException("The storage has been closed.");
			
			Record record = null;
			for(Record rec: records)
			{
				if(rec.name.equals(name))
				{
					record = rec;
					break;
				}
			}
					
			if(record != null)
			{
				record.instanceId = instanceId;
			}
			else
			{
				record = new Record();
				record.name = name;
				record.instanceId = instanceId;
				records.add(record);
			}
		}
	}
	
	private class Record
	{
		public String name;
		public long instanceId;
	}
}
