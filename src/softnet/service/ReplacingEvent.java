package softnet.service;

import java.util.UUID;
import softnet.asn.*;

public class ReplacingEvent
{
	public final UUID uid;
	public final String name;
	public final SequenceEncoder arguments;		
	public final boolean isNull;

	private ASNEncoder asnEncoder;	
	public byte[] getEncoding()
	{
		if(arguments != null && arguments.count() > 0)
			return asnEncoder.getEncoding(); 
		return null;
	}

	public ReplacingEvent(String name)
	{
		this.name = name;
		uid = UUID.randomUUID();
		asnEncoder = new ASNEncoder();
		arguments = asnEncoder.Sequence();
		isNull = false;
	}

	protected ReplacingEvent(String name, boolean isNull)
	{
		this.name = name;
		uid = UUID.randomUUID();
		if(isNull) {
			arguments = null;
			this.isNull = true;
		}
		else {
			asnEncoder = new ASNEncoder();
			arguments = asnEncoder.Sequence();
			this.isNull = false;
		}
	}
}
