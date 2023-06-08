package softnet.client;

import java.util.GregorianCalendar;

class EventIData
{
	public long eventId;
	public byte[] transactionUid;
	public String name;
	public EventCategory category;
	public boolean isNull;
	public long instanceId;
	public long serviceId;
	public long age;
	public GregorianCalendar createdDate;
	public byte[] argumentsEncoding = null;
}
