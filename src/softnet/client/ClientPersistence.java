package softnet.client;

import softnet.exceptions.*;

public interface ClientPersistence
{	
	void reset() throws PersistenceIOSoftnetException;
	ClientEventPersistable getItem(String name);
	void putItem(String name, long instanceId) throws PersistenceIOSoftnetException;
	void close();
}
