package softnet.service;

import java.util.UUID;

public class ServiceEventPersistable
{
	public final UUID instanceUid;
	public final String name;
	public final int kind;
	public final boolean isNull;
	public final long clientId;
	public final byte[] argumentsEncoding;
	
	private ServiceEventPersistable(String name, int kind, UUID instanceUid, boolean isNull, long clientId, byte[] argumentsEncoding)
	{
		this.instanceUid = instanceUid;
		this.name = name;
		this.kind = kind;
		this.isNull = isNull;
		this.clientId = clientId;
		this.argumentsEncoding = argumentsEncoding;
	}
	
	public static ServiceEventPersistable createReplacingEvent(String name, UUID instanceUid, byte[] argumentsEncoding)
	{
		return new ServiceEventPersistable(name, 1, instanceUid, false, 0, argumentsEncoding);
	}
	
	public static ServiceEventPersistable createReplacingNullEvent(String name, UUID instanceUid)
	{
		return new ServiceEventPersistable(name, 1, instanceUid, true, 0, null);
	}

	public static ServiceEventPersistable createQueueingEvent(String name, UUID instanceUid, byte[] argumentsEncoding)
	{
		return new ServiceEventPersistable(name, 2, instanceUid, false, 0, argumentsEncoding);
	}

	public static ServiceEventPersistable createPrivateEvent(String name, UUID instanceUid, long clientId, byte[] argumentsEncoding)
	{
		return new ServiceEventPersistable(name, 4, instanceUid, false, clientId, argumentsEncoding);
	}
}
