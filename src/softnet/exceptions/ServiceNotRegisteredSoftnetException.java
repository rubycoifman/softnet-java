package softnet.exceptions;

import java.util.UUID;

public class ServiceNotRegisteredSoftnetException extends CriticalSoftnetException 
{
	private static final long serialVersionUID = 6556483597545987332L;

	public ServiceNotRegisteredSoftnetException(String message)
	{
		super(SoftnetError.ServiceNotRegistered, message);
	}
	
	public ServiceNotRegisteredSoftnetException(UUID serviceUid)
	{
		super(SoftnetError.ServiceNotRegistered, String.format("The service uid '%s' has not been found in the softnet registry.", serviceUid.toString()));
	}
}