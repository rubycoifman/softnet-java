package softnet.service;

import java.util.UUID;
import softnet.asn.*;

public class PrivateEvent
{
	public final UUID uid;
	public final String name;
	public final long clientId;
	public final SequenceEncoder arguments;
	
	private ASNEncoder asnEncoder;
	public byte[] getEncoding()
	{
		if(arguments.count() > 0)
			return asnEncoder.getEncoding();
		return null;		
	}	

	public PrivateEvent(String name, long clientId)
	{
		this.name = name;
		this.clientId = clientId;
		uid = UUID.randomUUID();
		asnEncoder = new ASNEncoder();
		arguments = asnEncoder.Sequence();
	}	
}
