package softnet.discovery;

import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.ErrorCodes;
import softnet.exceptions.ServiceNotRegisteredSoftnetException;
import softnet.exceptions.SoftnetException;
import softnet.service.ServiceURI;
import softnet.utils.IPUtility;
import java.nio.ByteBuffer;

public class QueryOnServiceUid extends QueryBuilder
{
	private ServiceURI serviceURI;
	
	public QueryOnServiceUid(ServiceURI serviceURI)
	{
		this.serviceURI = serviceURI;
	}
	
	public ByteBuffer GetQuery()
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder sequence = asnEncoder.Sequence();
        
        if(IPUtility.isLiteralIP(serviceURI.server) == false)
        	sequence.UTF8String(1, serviceURI.server.toLowerCase());
    	sequence.OctetString(serviceURI.serviceUid);
    	
    	byte[] buffer = asnEncoder.getEncoding(2);
    	buffer[0] = Constants.ProtocolVersion;
    	buffer[1] = Constants.FrontServer.SERVICE_UID;
    	
    	return ByteBuffer.wrap(buffer);
	}
	
	public void ThrowException(int errorCode) throws SoftnetException
	{
		if(errorCode == ErrorCodes.SERVICE_NOT_REGISTERED)
			throw new ServiceNotRegisteredSoftnetException(serviceURI.serviceUid);		
	}
}