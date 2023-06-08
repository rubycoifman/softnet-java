package softnet.exceptions;

public class ClientOfflineSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -6434692420049320186L;

	public ClientOfflineSoftnetException(String message)
	{
		super(SoftnetError.ClientOffline, message);
	}
	
	public ClientOfflineSoftnetException()
	{
		super(SoftnetError.ClientOffline, "The client is offline.");
	}	
}
