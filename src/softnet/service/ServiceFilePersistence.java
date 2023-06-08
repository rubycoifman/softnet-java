package softnet.service;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import softnet.asn.*;
import softnet.exceptions.*;
import softnet.utils.ByteConverter;

class ServiceFilePersistence implements ServicePersistence
{
	private ServiceFilePersistence(UUID serviceUid, long storageCapacity)
	{
		this.serviceUid = serviceUid;
		this.storageCapacity = storageCapacity;
		replacingEvents = new LinkedList<REvent>();
		queueingEvents = new LinkedList<QEvent>();
		privateEvents = new LinkedList<PEvent>();
		isInStorageMode = true;
		storageUid = null;
	}
	
	private Object mutex = new Object();
	
	private UUID serviceUid;
	private UUID storageUid;
	private String filePath;
	private RandomAccessFile storageFile;	
	private long storageCapacity;
	private boolean isClosed = false;
	
	private LinkedList<REvent> replacingEvents;
	private LinkedList<QEvent> queueingEvents;
	private LinkedList<PEvent> privateEvents;
	
	private boolean isInStorageMode;
	private long tailPosition;
	private long headPosition;
	private long ancientDataTailPosition;
	private PeekedRecord peekedRecord;
	private int unacknowledgedEvents;	
	
	public static ServiceFilePersistence create(UUID serviceUid, long storageCapacity) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException
	{
		try
		{
			Class<softnet.service.ServiceEndpoint> serviceClass = softnet.service.ServiceEndpoint.class;
			java.security.CodeSource codeSource = serviceClass.getProtectionDomain().getCodeSource();
	
			File jarFile;
			URL url = codeSource.getLocation();
			if (url != null) {
				jarFile = new File(url.toURI());
			}
			else {
			    String path = serviceClass.getResource(serviceClass.getSimpleName() + ".class").getPath();
			    String jarFilePath = path.substring(path.indexOf(":") + 1, path.indexOf("!"));
			    jarFilePath = URLDecoder.decode(jarFilePath, "UTF-8");
			    jarFile = new File(jarFilePath);
			}
			
			String directory = jarFile.getParentFile().getAbsolutePath();			
			
			String fileName = "softnet.service.persistence_" + serviceUid.toString() + ".ssp";			
			String filePath = directory + File.separator + fileName;
			
			RandomAccessFile file = new RandomAccessFile(new File(filePath), "rwd");
			
			ServiceFilePersistence sfp = new ServiceFilePersistence(serviceUid, storageCapacity);
			sfp.load(file, filePath);
			return sfp;
		}
		catch(IOException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());
		}
		catch(SecurityException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());			
		}
		catch(URISyntaxException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());
		}
		catch(NullPointerException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());
		}
	}

	public static ServiceFilePersistence create(UUID serviceUid, long storageCapacity, String directory) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException
	{
		try
		{			
			String fileName = "softnet.service.persistence_" + serviceUid.toString() + ".ssp";			
			String filePath = directory + File.separator + fileName;			
			RandomAccessFile file = new RandomAccessFile(new File(filePath), "rwd");
			ServiceFilePersistence sfp = new ServiceFilePersistence(serviceUid, storageCapacity);
			sfp.load(file, filePath);
			return sfp;
		}
		catch(IOException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());
		}
		catch(SecurityException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());			
		}
	}
	
	private void load(RandomAccessFile file, String filePath) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException
	{
		try		
		{
			this.storageFile = file;
			this.filePath = filePath;									
			peekedRecord = null;

			long fileLength = storageFile.length();
			
			headPosition = 32;
			tailPosition = 32;
			ancientDataTailPosition = 32;
			
			if(fileLength < 32)
			{
				storageFile.setLength(32);
				
				byte[] uidBytes = ByteConverter.getBytes(serviceUid);
				storageFile.seek(0);
				storageFile.write(uidBytes);

				storageUid = UUID.randomUUID();
				uidBytes = ByteConverter.getBytes(storageUid);
				storageFile.seek(16);
				storageFile.write(uidBytes);									
								
				return;
			}			

			byte[] uidBytes = new byte[16];
			storageFile.seek(0);
			storageFile.read(uidBytes);
			UUID serviceUid = ByteConverter.toUuid(uidBytes);
			if(serviceUid.equals(this.serviceUid) == false)
				throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));
			
			storageFile.seek(16);
			storageFile.read(uidBytes);
			storageUid = ByteConverter.toUuid(uidBytes);
			
			if(fileLength == 32)
				return;
						
			byte[] buffer = new byte[128];			
			long currentPosition = 32;
			
			try
			{
				while(true)
				{				
					if(fileLength - currentPosition <= 1)
					{
						tailPosition = currentPosition;
						return;
					}
	
					storageFile.seek(currentPosition);
					storageFile.read(buffer, 0, 2);
					int recordSize = ByteConverter.toInt32FromInt16(buffer, 0);
					if(recordSize == 0)
					{
						tailPosition = currentPosition;
						return;
					}
	
					if(currentPosition + recordSize > fileLength)
						throw new PersistenceDataFormatSoftnetException(String.format("The file of the persistance storage '%s' has been truncated.", filePath));
	
					if(recordSize < 6)
						throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));
									
					if(buffer.length < recordSize)
						buffer = new byte[recordSize];
					
					storageFile.seek(currentPosition + 2);
					int isAcknowledged = storageFile.readByte();							
					if(isAcknowledged != 0)
					{
						if(isAcknowledged == 1)
						{
							currentPosition += recordSize;
							continue;
						}
						else
							throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));
					}
									
					storageFile.seek(currentPosition + 3);
					int messageKind = storageFile.readByte();
					if(!(messageKind == 1 || messageKind == 5 || messageKind == 2 || messageKind == 4))
						throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));
					 
					storageFile.seek(currentPosition + 4);
					storageFile.read(buffer, 0, 2);
					int nameBytesCount = ByteConverter.toInt32FromInt16(buffer, 0);
					if(nameBytesCount < 1 || nameBytesCount > 512 || 8 + nameBytesCount > recordSize)
						throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));				
					
					storageFile.seek(currentPosition + 6);
					storageFile.read(buffer, 0, nameBytesCount);
					String eventName = new String(buffer, 0, nameBytesCount, java.nio.charset.StandardCharsets.UTF_16BE);
					
					int headerSize = 6 + nameBytesCount;
					
					if(messageKind == 1)
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
						{
							rEvent = new REvent(eventName);
							replacingEvents.add(rEvent);						
							rEvent.lastInstance = new RERecord(currentPosition, recordSize, headerSize, false);
						}
						else
						{
							storageFile.seek(rEvent.lastInstance.position + 2);
							storageFile.writeByte(1);
							rEvent.lastInstance = new RERecord(currentPosition, recordSize, headerSize, false);						
						}
					}
					else if(messageKind == 5)
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
						{
							rEvent = new REvent(eventName);
							replacingEvents.add(rEvent);						
							rEvent.lastInstance = new RERecord(currentPosition, recordSize, headerSize, true);
						}
						else
						{
							storageFile.seek(rEvent.lastInstance.position + 2);
							storageFile.writeByte(1);
							rEvent.lastInstance = new RERecord(currentPosition, recordSize, headerSize, true);						
						}
					}
					
					currentPosition += recordSize;
				}
			}
			finally
			{
				ancientDataTailPosition = tailPosition;
			}
		}
		catch(IOException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());
		}
	}

	public UUID getUid()
	{
		return storageUid;
	}

	public void invalidateAncientData() throws PersistenceIOSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(isInStorageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			if(ancientDataTailPosition == 32)
				return;
						
			try
			{
				if(ancientDataTailPosition == tailPosition)
				{
					storageFile.setLength(32);
				
					headPosition = 32;
					tailPosition = 32;
					ancientDataTailPosition = 32;
					
					replacingEvents.clear();
					queueingEvents.clear();
					privateEvents.clear();				
				}
				else
				{
					byte[] buffer = new byte[2];
					long currentPosition = 32;

					while(currentPosition < ancientDataTailPosition)
					{
						storageFile.seek(currentPosition);
						storageFile.read(buffer, 0, 2);
						int recordSize = ByteConverter.toInt32FromInt16(buffer, 0);
						storageFile.seek(currentPosition + 2);
						storageFile.writeByte(1);
						currentPosition += recordSize;
					}
						
					headPosition = ancientDataTailPosition;
					ancientDataTailPosition = 32;
				}
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}	
	
	public void clear() throws PersistenceIOSoftnetException
	{
		try			
		{
			synchronized(mutex)
			{
				storageFile.setLength(32);
				headPosition = 32;
				tailPosition = 32;
				ancientDataTailPosition = 32;
				replacingEvents.clear();
				queueingEvents.clear();
				privateEvents.clear();
			}
		}
		catch(IOException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());
		}
	}

	public void reset() throws PersistenceIOSoftnetException
	{
		try
		{
			synchronized(mutex)
			{
				storageFile.setLength(32);

				byte[] uidBytes = ByteConverter.getBytes(serviceUid);
				storageFile.seek(0);
				storageFile.write(uidBytes);

				if(storageUid == null)
					storageUid = UUID.randomUUID();
				uidBytes = ByteConverter.getBytes(storageUid);
				storageFile.seek(16);
				storageFile.write(uidBytes);
				
				headPosition = 32;
				tailPosition = 32;
				ancientDataTailPosition = 32;
				
				replacingEvents.clear();
				queueingEvents.clear();
				privateEvents.clear();				
			}
		}
		catch(IOException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());
		}
	}

	public void setStorageMode()
	{
		synchronized(mutex)
		{
			if(isInStorageMode)
				return;			
			isInStorageMode = true;
			headPosition = 32;
						
			queueingEvents.clear();
			privateEvents.clear();
		}
	}

	public boolean isInCacheMode()
	{
		return isInStorageMode == false;
	}
	
	public boolean isInStorageMode()
	{
		return isInStorageMode;		
	}

	public void cache(ReplacingEvent replacingEvent) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(isInStorageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");
				
			try
			{
				byte[] nameBytes = replacingEvent.name.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);				
				int headerSize = 6 + nameBytes.length;
				
				byte[] bodyEncoding = null;				
				if(replacingEvent.isNull == false)
				{
					ASNEncoder asnEncoder = new ASNEncoder();
					SequenceEncoder asnSequence = asnEncoder.Sequence();
					asnSequence.OctetString(replacingEvent.uid);
					byte[] argumentsEncoding = replacingEvent.getEncoding();
					if(argumentsEncoding != null)
						asnSequence.OctetString(1, argumentsEncoding);
					bodyEncoding = asnEncoder.getEncoding();
				}
				else
				{
					ASNEncoder asnEncoder = new ASNEncoder();
					SequenceEncoder asnSequence = asnEncoder.Sequence();
					asnSequence.OctetString(replacingEvent.uid);
					bodyEncoding = asnEncoder.getEncoding();
				}
				
				int recordSize = headerSize + bodyEncoding.length;
				if(tailPosition + recordSize > storageCapacity)					
					throw new PersistenceStorageFullSoftnetException(String.format("The persistance storage '%s' has reached the maximum size limit.", filePath));
				
				storageFile.seek(tailPosition);
				byte[] sizeBytes = new byte[2];
				ByteConverter.writeAsInt16(recordSize, sizeBytes, 0);
				storageFile.write(sizeBytes);
				
				storageFile.seek(tailPosition + 2);
				storageFile.writeByte(0);

				storageFile.seek(tailPosition + 3);
				storageFile.writeByte(replacingEvent.isNull == false ? 1 : 5);

				storageFile.seek(tailPosition + 4);
				ByteConverter.writeAsInt16(nameBytes.length, sizeBytes, 0);
				storageFile.write(sizeBytes);

				storageFile.seek(tailPosition + 6);
				storageFile.write(nameBytes);

				storageFile.seek(tailPosition + headerSize);
				storageFile.write(bodyEncoding);				

				long recordPosition = tailPosition;
				tailPosition += recordSize;				

				if(storageFile.length() > tailPosition)
				{
					storageFile.seek(tailPosition);
					storageFile.writeByte(0);
					storageFile.seek(tailPosition + 1);
					storageFile.writeByte(0);
				}
				
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
					
					rEvent.currentInstance = new RERecord(recordPosition, recordSize, headerSize, replacingEvent.isNull);						
					unacknowledgedEvents++;
				}
				else if(rEvent.currentInstance == null)
				{
					rEvent.currentInstance = new RERecord(recordPosition, recordSize, headerSize, replacingEvent.isNull);						
					unacknowledgedEvents++; 						
				}
				else if(rEvent.lastInstance == null)
				{
					rEvent.lastInstance = new RERecord(recordPosition, recordSize, headerSize, replacingEvent.isNull);
				}
				else
				{
					storageFile.seek(rEvent.lastInstance.position + 2);
					storageFile.writeByte(1);
					rEvent.lastInstance = new RERecord(recordPosition, recordSize, headerSize, replacingEvent.isNull);
				}
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}

	public void cache(QueueingEvent queueingEvent) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(isInStorageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");
			
			try
			{
				byte[] nameBytes = queueingEvent.name.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);				
				int headerSize = 6 + nameBytes.length;
				
				ASNEncoder asnEncoder = new ASNEncoder();
				SequenceEncoder asnSequence = asnEncoder.Sequence();
				asnSequence.OctetString(queueingEvent.uid);
				byte[] argumentsEncoding = queueingEvent.getEncoding();
				if(argumentsEncoding != null)
					asnSequence.OctetString(1, argumentsEncoding);
				byte[] bodyEncoding = asnEncoder.getEncoding();
				
				int recordSize = headerSize + bodyEncoding.length;
				if(tailPosition + recordSize > storageCapacity)					
					throw new PersistenceStorageFullSoftnetException(String.format("The persistance storage '%s' has reached the maximum size limit.", filePath));

				storageFile.seek(tailPosition);
				byte[] sizeBytes = new byte[2];
				ByteConverter.writeAsInt16(recordSize, sizeBytes, 0);
				storageFile.write(sizeBytes);
				
				storageFile.seek(tailPosition + 2);
				storageFile.writeByte(0);

				storageFile.seek(tailPosition + 3);
				storageFile.writeByte(2); // Softnet.Core.Constants.Service.EventController.QUEUEING_EVENT

				storageFile.seek(tailPosition + 4);
				ByteConverter.writeAsInt16(nameBytes.length, sizeBytes, 0);
				storageFile.write(sizeBytes);

				storageFile.seek(tailPosition + 6);
				storageFile.write(nameBytes);

				storageFile.seek(tailPosition + headerSize);
				storageFile.write(bodyEncoding);				

				long recordPosition = tailPosition;
				tailPosition += recordSize;				

				if(storageFile.length() > tailPosition)
				{
					storageFile.seek(tailPosition);
					storageFile.writeByte(0);
					storageFile.seek(tailPosition + 1);
					storageFile.writeByte(0);
				}
							
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
					
					qEvent.instanceQueue.add(new QERecord(recordPosition, recordSize, headerSize));						
					unacknowledgedEvents++;						
				}
				else if(qEvent.instanceQueue.isEmpty())
				{
					qEvent.instanceQueue.add(new QERecord(recordPosition, recordSize, headerSize));						
					unacknowledgedEvents++; 						
				}					
				else
				{
					qEvent.instanceQueue.add(new QERecord(recordPosition, recordSize, headerSize));
				}
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}

	public void cache(PrivateEvent privateEvent) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(isInStorageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");
			
			try
			{
				byte[] nameBytes = privateEvent.name.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);				
				int headerSize = 6 + nameBytes.length;
				
				ASNEncoder asnEncoder = new ASNEncoder();
				SequenceEncoder asnSequence = asnEncoder.Sequence();
				asnSequence.OctetString(privateEvent.uid);
				asnSequence.Int64(privateEvent.clientId);
				byte[] argumentsEncoding = privateEvent.getEncoding();
				if(argumentsEncoding != null)
					asnSequence.OctetString(1, argumentsEncoding);
				byte[] bodyEncoding = asnEncoder.getEncoding();
				
				int recordSize = headerSize + bodyEncoding.length;
				if(tailPosition + recordSize > storageCapacity)					
					throw new PersistenceStorageFullSoftnetException(String.format("The persistance storage '%s' has reached the maximum size limit.", filePath));

				storageFile.seek(tailPosition);
				byte[] sizeBytes = new byte[2];
				ByteConverter.writeAsInt16(recordSize, sizeBytes, 0);
				storageFile.write(sizeBytes);
				
				storageFile.seek(tailPosition + 2);
				storageFile.writeByte(0);

				storageFile.seek(tailPosition + 3);
				storageFile.writeByte(4); // Softnet.Core.Constants.Service.EventController.PRIVATE_EVENT

				storageFile.seek(tailPosition + 4);
				ByteConverter.writeAsInt16(nameBytes.length, sizeBytes, 0);
				storageFile.write(sizeBytes);

				storageFile.seek(tailPosition + 6);
				storageFile.write(nameBytes);

				storageFile.seek(tailPosition + headerSize);
				storageFile.write(bodyEncoding);				

				long recordPosition = tailPosition;
				tailPosition += recordSize;				

				if(storageFile.length() > tailPosition)
				{
					storageFile.seek(tailPosition);
					storageFile.writeByte(0);
					storageFile.seek(tailPosition + 1);
					storageFile.writeByte(0);
				}
								
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
					
					pEvent.instanceQueue.add(new PERecord(recordPosition, recordSize, headerSize));						
					unacknowledgedEvents++;						
				}
				else if(pEvent.instanceQueue.isEmpty())
				{
					pEvent.instanceQueue.add(new PERecord(recordPosition, recordSize, headerSize));						
					unacknowledgedEvents++; 						
				}					
				else
				{
					pEvent.instanceQueue.add(new PERecord(recordPosition, recordSize, headerSize));
				}
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}

	public ServiceEventPersistable setAcknowledment(int eventKind, String eventName) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");

			if(isInStorageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");			

			if(eventName == null)
				throw new NullPointerException("The value of the event's name is null.");
			
			try
			{
				try
				{
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
							throw new IllegalStateException(String.format("There are no events of the type '%s' to be acknowledged.", eventName));
	
						if(rEvent.currentInstance == null)
							throw new IllegalStateException(String.format("There are no events of the type '%s' to be acknowledged.", eventName));
						
						storageFile.seek(rEvent.currentInstance.position + 2);
						storageFile.writeByte(1);
						rEvent.currentInstance = null;
						
						if(rEvent.lastInstance == null)
						{
							unacknowledgedEvents--;						
							if(unacknowledgedEvents == 0)
							{							
								storageFile.setLength(32);
								headPosition = 32;
								tailPosition = 32;
							}
							return null;
						}
						
						rEvent.currentInstance = rEvent.lastInstance;
						rEvent.lastInstance = null;
						
						int bodySize = rEvent.currentInstance.size - rEvent.currentInstance.headerSize;
						byte[] bodyBuffer = new byte[bodySize];
						storageFile.seek(rEvent.currentInstance.position + rEvent.currentInstance.headerSize);
						storageFile.read(bodyBuffer);
											
						if(rEvent.currentInstance.isNull == false)
						{
							SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
							UUID instanceUid = asnSequence.OctetStringToUUID();
							byte[] argumentsEncoding = null;
							if(asnSequence.exists(1))
								argumentsEncoding = asnSequence.OctetString();
							
							return ServiceEventPersistable.createReplacingEvent(eventName, instanceUid, argumentsEncoding);
						}
						else
						{
							SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
							UUID instanceUid = asnSequence.OctetStringToUUID();
							
							return ServiceEventPersistable.createReplacingNullEvent(eventName, instanceUid);
						}					
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
							throw new IllegalStateException(String.format("There are no events of the type '%s' to be acknowledged.", eventName));
	
						if(qEvent.instanceQueue.isEmpty())
							throw new IllegalStateException(String.format("There are no events of the type '%s' to be acknowledged.", eventName));
						
						QERecord qeRecord = qEvent.instanceQueue.remove();
						storageFile.seek(qeRecord.position + 2);
						storageFile.writeByte(1);
						
						if(qEvent.instanceQueue.isEmpty())
						{
							unacknowledgedEvents--;						
							if(unacknowledgedEvents == 0)
							{							
								storageFile.setLength(32);
								headPosition = 32;
								tailPosition = 32;
							}
							return null;
						}
						
						qeRecord = qEvent.instanceQueue.element();
						
						int bodySize = qeRecord.size - qeRecord.headerSize;
						byte[] bodyBuffer = new byte[bodySize];
						storageFile.seek(qeRecord.position + qeRecord.headerSize);
						storageFile.read(bodyBuffer);
						
						SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
						UUID instanceUid = asnSequence.OctetStringToUUID();
						byte[] argumentsEncoding = null;
						if(asnSequence.exists(1))
							argumentsEncoding = asnSequence.OctetString();
						
						return ServiceEventPersistable.createQueueingEvent(eventName, instanceUid, argumentsEncoding);
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
							throw new IllegalStateException(String.format("There are no events of the type '%s' to be acknowledged.", eventName));
	
						if(pEvent.instanceQueue.isEmpty())
							throw new IllegalStateException(String.format("There are no events of the type '%s' to be acknowledged.", eventName));
						
						PERecord peRecord = pEvent.instanceQueue.remove();
						storageFile.seek(peRecord.position + 2);
						storageFile.writeByte(1);
						
						if(pEvent.instanceQueue.isEmpty())
						{
							unacknowledgedEvents--;						
							if(unacknowledgedEvents == 0)
							{							
								storageFile.setLength(32);
								headPosition = 32;
								tailPosition = 32;
							}
							return null;
						}
						
						peRecord = pEvent.instanceQueue.element();
						
						int bodySize = peRecord.size - peRecord.headerSize;
						byte[] bodyBuffer = new byte[bodySize];
						storageFile.seek(peRecord.position + peRecord.headerSize);
						storageFile.read(bodyBuffer);
						
						SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
						UUID instanceUid = asnSequence.OctetStringToUUID();
						long clientId = asnSequence.Int64();
						byte[] argumentsEncoding = null;
						if(asnSequence.exists(1))
							argumentsEncoding = asnSequence.OctetString();
						
						return ServiceEventPersistable.createPrivateEvent(eventName, instanceUid, clientId, argumentsEncoding);
					}
					else 
						throw new IllegalArgumentException("The value of 'eventKind' is illegal.");
				}
				catch(AsnException ex)
				{					
					throw new PersistenceDataFormatSoftnetException(ex.getMessage());
				}
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}			
		}
	}

	public ServiceEventPersistable peek(int eventKind, String eventName) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");

			if(isInStorageMode) 
				throw new IllegalStateException("The storage is in synchronous mode.");			

			if(eventName == null)
				throw new NullPointerException("The value of eventName is null.");
			
			try
			{
				try
				{
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
						
						int bodySize = rEvent.currentInstance.size - rEvent.currentInstance.headerSize;
						byte[] bodyBuffer = new byte[bodySize];
						storageFile.seek(rEvent.currentInstance.position + rEvent.currentInstance.headerSize);
						storageFile.read(bodyBuffer);
						
						if(rEvent.currentInstance.isNull == false)
						{
							SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
							UUID instanceUid = asnSequence.OctetStringToUUID();
							byte[] argumentsEncoding = null;
							if(asnSequence.exists(1))
								argumentsEncoding = asnSequence.OctetString();
							
							return ServiceEventPersistable.createReplacingEvent(eventName, instanceUid, argumentsEncoding);
						}
						else
						{
							SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
							UUID instanceUid = asnSequence.OctetStringToUUID();
							
							return ServiceEventPersistable.createReplacingNullEvent(eventName, instanceUid);
						}
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
											
						QERecord qeRecord = qEvent.instanceQueue.element();
						
						int bodySize = qeRecord.size - qeRecord.headerSize;
						byte[] bodyBuffer = new byte[bodySize];
						storageFile.seek(qeRecord.position + qeRecord.headerSize);
						storageFile.read(bodyBuffer);
						
						SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
						UUID instanceUid = asnSequence.OctetStringToUUID();
						byte[] argumentsEncoding = null;
						if(asnSequence.exists(1))
							argumentsEncoding = asnSequence.OctetString();
						
						return ServiceEventPersistable.createQueueingEvent(eventName, instanceUid, argumentsEncoding);
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
											
						PERecord peRecord = pEvent.instanceQueue.element();
						
						int bodySize = peRecord.size - peRecord.headerSize;
						byte[] bodyBuffer = new byte[bodySize];
						storageFile.seek(peRecord.position + peRecord.headerSize);
						storageFile.read(bodyBuffer);
						
						SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
						UUID instanceUid = asnSequence.OctetStringToUUID();
						long clientId = asnSequence.Int64();
						byte[] argumentsEncoding = null;
						if(asnSequence.exists(1))
							argumentsEncoding = asnSequence.OctetString();
						
						return ServiceEventPersistable.createPrivateEvent(eventName, instanceUid, clientId, argumentsEncoding);
					}
					else 
						throw new IllegalArgumentException("The value of eventKind is illegal.");
				}
				catch(AsnException ex)
				{					
					throw new PersistenceDataFormatSoftnetException(ex.getMessage());
				}
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}

	public void save(ReplacingEvent replacingEvent) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(isInStorageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			try
			{
				byte[] nameBytes = replacingEvent.name.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);				
				int headerSize = 6 + nameBytes.length;
				
				byte[] bodyEncoding = null;				
				if(replacingEvent.isNull == false)
				{
					ASNEncoder asnEncoder = new ASNEncoder();
					SequenceEncoder asnSequence = asnEncoder.Sequence();
					asnSequence.OctetString(replacingEvent.uid);
					byte[] argumentsEncoding = replacingEvent.getEncoding();
					if(argumentsEncoding != null)
						asnSequence.OctetString(1, argumentsEncoding);
					bodyEncoding = asnEncoder.getEncoding();
				}
				else
				{
					ASNEncoder asnEncoder = new ASNEncoder();
					SequenceEncoder asnSequence = asnEncoder.Sequence();
					asnSequence.OctetString(replacingEvent.uid);
					bodyEncoding = asnEncoder.getEncoding();
				}
				
				int recordSize = headerSize + bodyEncoding.length;
				if(tailPosition + recordSize > storageCapacity)					
					throw new PersistenceStorageFullSoftnetException(String.format("The persistance storage '%s' has reached the maximum size limit.", filePath));
				
				storageFile.seek(tailPosition);
				byte[] sizeBytes = new byte[2];
				ByteConverter.writeAsInt16(recordSize, sizeBytes, 0);
				storageFile.write(sizeBytes);
				
				storageFile.seek(tailPosition + 2);
				storageFile.writeByte(0);

				storageFile.seek(tailPosition + 3);
				storageFile.writeByte(replacingEvent.isNull == false ? 1 : 5);

				storageFile.seek(tailPosition + 4);
				ByteConverter.writeAsInt16(nameBytes.length, sizeBytes, 0);
				storageFile.write(sizeBytes);

				storageFile.seek(tailPosition + 6);
				storageFile.write(nameBytes);

				storageFile.seek(tailPosition + headerSize);
				storageFile.write(bodyEncoding);				

				long recordPosition = tailPosition;
				tailPosition += recordSize;				

				if(storageFile.length() > tailPosition)
				{
					storageFile.seek(tailPosition);
					storageFile.writeByte(0);
					storageFile.seek(tailPosition + 1);
					storageFile.writeByte(0);
				}
				
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
					rEvent.lastInstance = new RERecord(recordPosition, recordSize, headerSize, replacingEvent.isNull);
				}
				else if(rEvent.lastInstance != null)
				{
					storageFile.seek(rEvent.lastInstance.position + 2);
					storageFile.writeByte(1);
					rEvent.lastInstance = new RERecord(recordPosition, recordSize, headerSize, replacingEvent.isNull);
				}
				else
				{
					rEvent.lastInstance = new RERecord(recordPosition, recordSize, headerSize, replacingEvent.isNull);
				}
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}

	public void save(QueueingEvent queueingEvent) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(isInStorageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			try
			{
				byte[] nameBytes = queueingEvent.name.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);				
				int headerSize = 6 + nameBytes.length;
				
				ASNEncoder asnEncoder = new ASNEncoder();
				SequenceEncoder asnSequence = asnEncoder.Sequence();
				asnSequence.OctetString(queueingEvent.uid);
				byte[] argumentsEncoding = queueingEvent.getEncoding();
				if(argumentsEncoding != null)
					asnSequence.OctetString(1, argumentsEncoding);
				byte[] bodyEncoding = asnEncoder.getEncoding();
				
				int recordSize = headerSize + bodyEncoding.length;
				if(tailPosition + recordSize > storageCapacity)					
					throw new PersistenceStorageFullSoftnetException(String.format("The persistance storage '%s' has reached the maximum size limit.", filePath));

				storageFile.seek(tailPosition);
				byte[] sizeBytes = new byte[2];
				ByteConverter.writeAsInt16(recordSize, sizeBytes, 0);
				storageFile.write(sizeBytes);
				
				storageFile.seek(tailPosition + 2);
				storageFile.writeByte(0);

				storageFile.seek(tailPosition + 3);
				storageFile.writeByte(2); // Softnet.Core.Constants.Service.EventController.QUEUEING_EVENT

				storageFile.seek(tailPosition + 4);
				ByteConverter.writeAsInt16(nameBytes.length, sizeBytes, 0);
				storageFile.write(sizeBytes);

				storageFile.seek(tailPosition + 6);
				storageFile.write(nameBytes);

				storageFile.seek(tailPosition + headerSize);
				storageFile.write(bodyEncoding);				

				tailPosition += recordSize;				

				if(storageFile.length() > tailPosition)
				{
					storageFile.seek(tailPosition);
					storageFile.writeByte(0);
					storageFile.seek(tailPosition + 1);
					storageFile.writeByte(0);
				}							
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}

	public void save(PrivateEvent privateEvent) throws PersistenceIOSoftnetException, PersistenceStorageFullSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");
			
			if(isInStorageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			try
			{
				byte[] nameBytes = privateEvent.name.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);				
				int headerSize = 6 + nameBytes.length;
				
				ASNEncoder asnEncoder = new ASNEncoder();
				SequenceEncoder asnSequence = asnEncoder.Sequence();
				asnSequence.OctetString(privateEvent.uid);
				asnSequence.Int64(privateEvent.clientId);
				byte[] argumentsEncoding = privateEvent.getEncoding();
				if(argumentsEncoding != null)
					asnSequence.OctetString(1, argumentsEncoding);
				byte[] bodyEncoding = asnEncoder.getEncoding();
				
				int recordSize = headerSize + bodyEncoding.length;
				if(tailPosition + recordSize > storageCapacity)					
					throw new PersistenceStorageFullSoftnetException(String.format("The persistance storage '%s' has reached the maximum size limit.", filePath));

				storageFile.seek(tailPosition);
				byte[] sizeBytes = new byte[2];
				ByteConverter.writeAsInt16(recordSize, sizeBytes, 0);
				storageFile.write(sizeBytes);
				
				storageFile.seek(tailPosition + 2);
				storageFile.writeByte(0);

				storageFile.seek(tailPosition + 3);
				storageFile.writeByte(4); // Softnet.Core.Constants.Service.EventController.PRIVATE_EVENT

				storageFile.seek(tailPosition + 4);
				ByteConverter.writeAsInt16(nameBytes.length, sizeBytes, 0);
				storageFile.write(sizeBytes);

				storageFile.seek(tailPosition + 6);
				storageFile.write(nameBytes);

				storageFile.seek(tailPosition + headerSize);
				storageFile.write(bodyEncoding);				

				tailPosition += recordSize;				

				if(storageFile.length() > tailPosition)
				{
					storageFile.seek(tailPosition);
					storageFile.writeByte(0);
					storageFile.seek(tailPosition + 1);
					storageFile.writeByte(0);
				}								
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}

	public void setAcknowledment() throws PersistenceIOSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");

			if(isInStorageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			if(peekedRecord == null)
				throw new IllegalStateException("No event has been peeked up to be acknowledged.");
			
			try
			{
				storageFile.seek(peekedRecord.position + 2);
				storageFile.writeByte(1);
				
				headPosition += peekedRecord.size;
				peekedRecord = null;
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}

	public ServiceEventPersistable peek() throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed) 
				throw new IllegalStateException("The storage has been closed.");

			if(isInStorageMode == false) 
				throw new IllegalStateException("The storage is in asynchronous mode.");
			
			try
			{
				try		
				{
					if(peekedRecord != null)
					{
						byte[] bodyBuffer = new byte[peekedRecord.size - peekedRecord.headerSize];
						storageFile.seek(peekedRecord.position + peekedRecord.headerSize);
						storageFile.read(bodyBuffer);
						
						if(peekedRecord.messageKind == 1)
						{
							SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
							UUID instanceUid = asnSequence.OctetStringToUUID();
							byte[] argumentsEncoding = null;
							if(asnSequence.exists(1))
								argumentsEncoding = asnSequence.OctetString();
							
							return ServiceEventPersistable.createReplacingEvent(peekedRecord.eventName, instanceUid, argumentsEncoding);
						}
						else if(peekedRecord.messageKind == 5)
						{
							SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
							UUID instanceUid = asnSequence.OctetStringToUUID();
							
							return ServiceEventPersistable.createReplacingNullEvent(peekedRecord.eventName, instanceUid);
						}
						else if(peekedRecord.messageKind == 2)
						{
							SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
							UUID instanceUid = asnSequence.OctetStringToUUID();
							byte[] argumentsEncoding = null;
							if(asnSequence.exists(1))
								argumentsEncoding = asnSequence.OctetString();
							
							return ServiceEventPersistable.createQueueingEvent(peekedRecord.eventName, instanceUid, argumentsEncoding);
						}
						else // peekedRecord.messageKind == 4
						{
							SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
							UUID instanceUid = asnSequence.OctetStringToUUID();
							long clientId = asnSequence.Int64();
							byte[] argumentsEncoding = null;
							if(asnSequence.exists(1))
								argumentsEncoding = asnSequence.OctetString();
							
							return ServiceEventPersistable.createPrivateEvent(peekedRecord.eventName, instanceUid, clientId, argumentsEncoding);
						}
					}
					else
					{
						byte[] buffer = new byte[128];
						long fileLength = storageFile.length();
						while(true)
						{
							if(fileLength - headPosition < 2)
							{
								storageFile.setLength(32);
								headPosition = 32;
								tailPosition = 32;
								ancientDataTailPosition = 32;
								replacingEvents.clear();
								peekedRecord = null;
								isInStorageMode = false;
								return null;
							}
												
							storageFile.seek(headPosition);
							storageFile.read(buffer, 0, 2);
							int recordSize = ByteConverter.toInt32FromInt16(buffer, 0);
							if(recordSize == 0)
							{
								storageFile.setLength(32);
								headPosition = 32;
								tailPosition = 32;
								ancientDataTailPosition = 32;
								replacingEvents.clear();
								peekedRecord = null;
								isInStorageMode = false;
								return null;
							}
							
							if(headPosition + recordSize > fileLength)
								throw new PersistenceDataFormatSoftnetException(String.format("The file of the persistance storage '%s' has been truncated.", filePath));
	
							if(recordSize < 6)
								throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));
															
							storageFile.seek(headPosition + 2);
							int isAcknowledged = storageFile.readByte();
							if(isAcknowledged != 0)
							{
								if(isAcknowledged == 1)
								{
									headPosition += recordSize;
									continue;
								}
								else
									throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));	
							}
																				
							storageFile.seek(headPosition + 3);
							int messageKind = storageFile.readByte();
							
							if(buffer.length < recordSize)
								buffer = new byte[recordSize];
							
							storageFile.seek(headPosition + 4);
							storageFile.read(buffer, 0, 2);
							int nameBytesCount = ByteConverter.toInt32FromInt16(buffer, 0);
							if(nameBytesCount < 1 || nameBytesCount > 512 || 8 + nameBytesCount > recordSize)
								throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));					
							
							storageFile.seek(headPosition + 6);
							storageFile.read(buffer, 0, nameBytesCount);				
							String eventName = new String(buffer, 0, nameBytesCount, java.nio.charset.StandardCharsets.UTF_16BE);
							
							int headerSize = 6 + nameBytesCount;										
							peekedRecord = new PeekedRecord(headPosition, recordSize, headerSize, eventName, messageKind);
							
							byte[] bodyBuffer = new byte[recordSize - headerSize];
							storageFile.seek(headPosition + headerSize);
							storageFile.read(bodyBuffer);
							
							if(messageKind == 1)
							{
								SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
								UUID instanceUid = asnSequence.OctetStringToUUID();
								byte[] argumentsEncoding = null;
								if(asnSequence.exists(1))
									argumentsEncoding = asnSequence.OctetString();
								
								return ServiceEventPersistable.createReplacingEvent(eventName, instanceUid, argumentsEncoding);
							}
							
							if(messageKind == 5)
							{
								SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
								UUID instanceUid = asnSequence.OctetStringToUUID();
								
								return ServiceEventPersistable.createReplacingNullEvent(eventName, instanceUid);
							}
							
							if(messageKind == 2)
							{
								SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
								UUID instanceUid = asnSequence.OctetStringToUUID();
								byte[] argumentsEncoding = null;
								if(asnSequence.exists(1))
									argumentsEncoding = asnSequence.OctetString();
								
								return ServiceEventPersistable.createQueueingEvent(eventName, instanceUid, argumentsEncoding);
							}
							
							if(messageKind == 4)
							{
								SequenceDecoder asnSequence = ASNDecoder.Sequence(bodyBuffer);
								UUID instanceUid = asnSequence.OctetStringToUUID();
								long clientId = asnSequence.Int64();
								byte[] argumentsEncoding = null;
								if(asnSequence.exists(1))
									argumentsEncoding = asnSequence.OctetString();
								
								return ServiceEventPersistable.createPrivateEvent(eventName, instanceUid, clientId, argumentsEncoding);
							}
							
							throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));
						}
					}
				}
				catch(AsnException ex)
				{
					throw new PersistenceDataFormatSoftnetException(ex.getMessage());
				}
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}		
	}

	public void close()
	{		
		synchronized(mutex)
		{
			if(isClosed) 
				return;
			
			isClosed = true;			
			try
			{
				storageFile.close();
			}
			catch(IOException ex) {}		
		}
	}	
	
	private class PeekedRecord
	{
		public final long position;
		public final int size;
		public final int headerSize;
		public final String eventName;
		public final int messageKind;
		public PeekedRecord(long position, int size, int headerSize, String eventName, int messageKind)
		{
			this.position = position;
			this.size = size;
			this.headerSize = headerSize;
			this.eventName = eventName;
			this.messageKind = messageKind;
		}
	}
		
	private class RERecord
	{
		public final long position;
		public final int size;
		public final int headerSize;		
		public final boolean isNull;
		public RERecord(long position, int size, int headerSize, boolean isNull)
		{
			this.position = position;
			this.size = size;
			this.headerSize = headerSize;
			this.isNull = isNull;
		}
	}
		
	private class REvent
	{
		public final String name;
		public RERecord currentInstance;
		public RERecord lastInstance;
		public REvent(String name)
		{
			this.name = name;
			currentInstance = null;
			lastInstance = null;
		}
	}
	
	private class QERecord
	{
		public final long position;
		public final int size;
		public final int headerSize;
		public QERecord(long position, int size, int headerSize)
		{
			this.position = position;
			this.size = size;
			this.headerSize = headerSize;
		}
	}
	
	private class QEvent
	{
		public final String name;
		public Queue<QERecord> instanceQueue;
		public QEvent(String name)
		{
			this.name = name;
			this.instanceQueue = new LinkedList<QERecord>();
		}
	}

	private class PERecord
	{
		public final long position;
		public final int size;
		public final int headerSize;
		public PERecord(long position, int size, int headerSize)
		{
			this.position = position;
			this.size = size;
			this.headerSize = headerSize;
		}
	}

	private class PEvent
	{
		public final String name;
		public Queue<PERecord> instanceQueue;
		public PEvent(String name)
		{
			this.name = name;
			this.instanceQueue = new LinkedList<PERecord>();
		}
	}
}
