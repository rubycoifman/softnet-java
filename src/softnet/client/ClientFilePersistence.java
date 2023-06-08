package softnet.client;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

import softnet.utils.*;
import softnet.exceptions.PersistenceDataFormatSoftnetException;
import softnet.exceptions.PersistenceIOSoftnetException;
import softnet.utils.IPUtility;

public class ClientFilePersistence implements ClientPersistence
{	
	private ClientFilePersistence(String clientKey)
	{
		this.clientKey = clientKey;
	}
	
	public static ClientFilePersistence create(ClientURI clientURI) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException
	{
		try
		{
			Class<softnet.client.ClientEndpoint> clientClass = softnet.client.ClientEndpoint.class;
			java.security.CodeSource codeSource = clientClass.getProtectionDomain().getCodeSource();
	
			File clientJarFile;
			URL url = codeSource.getLocation();
			if (url != null) {
				clientJarFile = new File(url.toURI());
			}
			else {
			    String path = clientClass.getResource(clientClass.getSimpleName() + ".class").getPath();
			    String jarFilePath = path.substring(path.indexOf(":") + 1, path.indexOf("!"));
			    jarFilePath = URLDecoder.decode(jarFilePath, "UTF-8");
			    clientJarFile = new File(jarFilePath);
			}
			
			String directory = clientJarFile.getParentFile().getAbsolutePath();
			
			String serverAddress = clientURI.server;
			byte[] ipv6Bytes = IPUtility.textToNumericFormatV6(serverAddress);
			if(ipv6Bytes != null)
			{
				InetAddress addr = InetAddress.getByAddress(ipv6Bytes);
				serverAddress = addr.getHostAddress().replaceAll(":", "-");
			}
			
			String fileName = "softnet.client.persistence_" + serverAddress + "_" + clientURI.clientKey + ".scp";			
			String filePath = directory + File.separator + fileName;
			
			RandomAccessFile file = new RandomAccessFile(new File(filePath), "rwd");
			
			ClientFilePersistence cfp = new ClientFilePersistence(clientURI.clientKey);
			cfp.load(file, filePath);				
			return cfp;			
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
	
	public static ClientFilePersistence create(ClientURI clientURI, String directory) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException
	{
		try
		{
			String serverAddress = clientURI.server;
			byte[] ipv6Bytes = IPUtility.textToNumericFormatV6(serverAddress);
			if(ipv6Bytes != null)
			{
				InetAddress addr = InetAddress.getByAddress(ipv6Bytes);
				serverAddress = addr.getHostAddress().replaceAll(":", "-");
			}
			
			String fileName = "softnet.client.persistence_" + serverAddress + "_" + clientURI.clientKey + ".scp";			
			String filePath = directory + File.separator + fileName;
			
			RandomAccessFile file = new RandomAccessFile(new File(filePath), "rwd");
			
			ClientFilePersistence cfp = new ClientFilePersistence(clientURI.clientKey);
			cfp.load(file, filePath);				
			return cfp;
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
	
	private Object mutex = new Object();
	private String clientKey;
	private boolean isClosed = false;
	private RandomAccessFile storageFile;
	private byte[] buffer;
	private ArrayList<Record> records;
	private long tailPosition;
	
	private void load(RandomAccessFile file, String filePath) throws PersistenceIOSoftnetException, PersistenceDataFormatSoftnetException
	{
		try		
		{
			storageFile = file;			
			records = new ArrayList<Record>();
			buffer = new byte[512];
			tailPosition = 0;
			
			if(storageFile.length() <= 2)
			{
				reset();
				return;
			}
			
			storageFile.seek(0);			
			storageFile.read(buffer, 0, 2);
			int keylength = ByteConverter.toInt32FromInt16(buffer, 0);
			if(keylength != (2 * clientKey.length()) || storageFile.length() < (2 + keylength))
				throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));

			byte[] keyBytes = new byte[keylength];
			storageFile.seek(2);
			storageFile.read(keyBytes, 0, keylength);		
			String clientKey = new String(keyBytes, 0, keylength, java.nio.charset.StandardCharsets.UTF_16BE);
			if(clientKey.equals(this.clientKey) == false)
				throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));

			tailPosition = 2 + keylength;
			
			while(true)
			{
				if(storageFile.length() == tailPosition)
					return;
				
				if(storageFile.length() - tailPosition == 1)
					throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));
				
				storageFile.seek(tailPosition);			
				storageFile.read(buffer, 0, 2);
				int recordSize = ByteConverter.toInt32FromInt16(buffer, 0);
				if(recordSize < 10 || recordSize > 520)
					throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));
				if(storageFile.length() < recordSize + 2)
					throw new PersistenceDataFormatSoftnetException(String.format("The data in the persistance storage '%s' has invalid format.", filePath));
	
				Record record = new Record();
	
				tailPosition += 2;
				record.position = tailPosition;
	
				storageFile.seek(tailPosition);
				storageFile.read(buffer, 0, 8);			
				record.instanceId = ByteConverter.toInt64(buffer, 0);
				
				int nameBytesCount = recordSize - 8;
				storageFile.seek(tailPosition + 8);
				storageFile.read(buffer, 0, nameBytesCount);			
				record.name = new String(buffer, 0, nameBytesCount, java.nio.charset.StandardCharsets.UTF_16BE);			
				records.add(record);

				tailPosition += recordSize;				
			}
		}
		catch(IOException ex)
		{
			throw new PersistenceIOSoftnetException(ex.getMessage());
		}
	}
	
	public void reset() throws PersistenceIOSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed)
				throw new IllegalStateException("The storage has been closed.");
			try
			{
				byte[] keyBytes = clientKey.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);	
				byte[] lengthBytes = new byte[2];
				ByteConverter.writeAsInt16(keyBytes.length, lengthBytes, 0);
				storageFile.seek(0);
				storageFile.write(lengthBytes);
				storageFile.seek(2);
				storageFile.write(keyBytes);
				tailPosition = 2 + keyBytes.length;
				storageFile.setLength(tailPosition);
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}

	public void close()
	{
		try
		{
			isClosed = true;
			records.clear();
			storageFile.close();
		}
		catch(IOException ex) {}
	}
	
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
	
	public void putItem(String name, long instanceId) throws PersistenceIOSoftnetException
	{
		synchronized(mutex)
		{
			if(isClosed)
				throw new IllegalStateException("The storage has been closed.");
			
			try
			{
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
					storageFile.seek(record.position);
					storageFile.write(ByteConverter.getBytes(instanceId), 0, 8);
				}
				else
				{
					record = new Record();
					record.name = name;
					record.instanceId = instanceId;
		
					byte[] nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);				
					int recordSize = 8 + nameBytes.length;				
					ByteConverter.writeAsInt16(recordSize, buffer, 0);
					
					storageFile.seek(tailPosition);
					storageFile.write(buffer, 0, 2);
									
					tailPosition += 2;
					storageFile.seek(tailPosition);
					storageFile.write(ByteConverter.getBytes(instanceId));
					
					storageFile.seek(tailPosition + 8);
					storageFile.write(nameBytes);
	
					record.position = tailPosition;
					records.add(record);
					
					tailPosition += recordSize;					
				}
			}
			catch(IOException ex)
			{
				throw new PersistenceIOSoftnetException(ex.getMessage());
			}
		}
	}
	
	private class Record
	{
		public String name;
		public long instanceId;
		public long position;
	}
}
