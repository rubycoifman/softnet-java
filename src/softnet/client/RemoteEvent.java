package softnet.client;

import java.util.GregorianCalendar;
import softnet.asn.*;

public class RemoteEvent
{
	public final long instanceId;
	public final String name;
	public final EventCategory category;
	public final boolean isNull;
	public final long serviceId;
	public final long age;
	public final GregorianCalendar createdDate;
	public final SequenceDecoder arguments;
	
	protected RemoteEvent(EventIData eventIData) throws AsnException
	{
		if(eventIData.category == EventCategory.Replacing)
		{
			this.category = EventCategory.Replacing;
			this.instanceId = eventIData.instanceId;
			this.name = eventIData.name;
			this.serviceId = eventIData.serviceId;
			this.age = eventIData.age;
			this.createdDate = eventIData.createdDate;
			if(eventIData.isNull)
			{
				this.isNull = true;
				this.arguments = null;
			}
			else
			{
				this.isNull = false;
				if(eventIData.argumentsEncoding != null)
					this.arguments = ASNDecoder.Sequence(eventIData.argumentsEncoding);
				else
					this.arguments = null;
			}
		}
		else if(eventIData.category == EventCategory.Queuing)
		{
			this.category = EventCategory.Queuing;
			this.instanceId = eventIData.instanceId;
			this.name = eventIData.name;
			this.serviceId = eventIData.serviceId;
			this.age = eventIData.age;
			this.createdDate = eventIData.createdDate;
			if(eventIData.argumentsEncoding != null)
				this.arguments = ASNDecoder.Sequence(eventIData.argumentsEncoding);
			else
				this.arguments = null;
			this.isNull = false;
		}		
		else			
		{
			this.category = EventCategory.Private;
			this.instanceId = eventIData.instanceId;
			this.name = eventIData.name;
			this.serviceId = eventIData.serviceId;
			this.age = eventIData.age;
			this.createdDate = eventIData.createdDate;
			if(eventIData.argumentsEncoding != null)
				this.arguments = ASNDecoder.Sequence(eventIData.argumentsEncoding);
			else
				this.arguments = null;
			this.isNull = false;
		}
	}
}
