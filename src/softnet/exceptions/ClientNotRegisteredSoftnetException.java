package softnet.exceptions;

public class ClientNotRegisteredSoftnetException extends CriticalSoftnetException 
{
	private static final long serialVersionUID = 8901238624189434402L;

	public ClientNotRegisteredSoftnetException(softnet.client.ClientURI clientUri)
	{
		super(SoftnetError.ClientNotRegistered, String.format("The client '%s' has not been found in the softnet registry.", clientUri.value));
	}
}
