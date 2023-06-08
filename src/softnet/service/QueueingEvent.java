package softnet.service;

import java.util.UUID;
import softnet.asn.*;

public class QueueingEvent 
{
	public final UUID uid;
	public final String name;
	public final SequenceEncoder arguments;
		
	private ASNEncoder asnEncoder;
	public byte[] getEncoding()
	{
		if(arguments.count() > 0)
			return asnEncoder.getEncoding();
		return null;
	}	

	public QueueingEvent(String name)
	{
		this.name = name;
		uid = UUID.randomUUID();
		asnEncoder = new ASNEncoder();
		arguments = asnEncoder.Sequence();
	}
}
