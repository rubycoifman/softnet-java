package softnet.service;

import java.util.UUID;
import softnet.exceptions.*;

public interface ServicePersistence
{
	UUID getUid();
	void setStorageMode();
	boolean isInCacheMode();
	boolean isInStorageMode();
	void invalidateAncientData() throws PersistenceIOSoftnetException;
	void reset() throws PersistenceIOSoftnetException;
	void clear() throws PersistenceIOSoftnetException;
	void cache(ReplacingEvent event) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException;
	void cache(QueueingEvent event) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException;
	void cache(PrivateEvent event) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException;
	ServiceEventPersistable setAcknowledment(int eventKind, String eventName) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException;
	ServiceEventPersistable peek(int eventKind, String eventName) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException;
	void save(ReplacingEvent event) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException;
	void save(QueueingEvent event) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException;
	void save(PrivateEvent event) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException;
	void setAcknowledment() throws PersistenceIOSoftnetException;
	ServiceEventPersistable peek() throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException;
	void close();
}
