package softnet.exceptions;

public class ConnectionAttemptFailedSoftnetException extends SoftnetException 
{	
	private static final long serialVersionUID = -795068469305021452L;

	public ConnectionAttemptFailedSoftnetException()
	{
		super(SoftnetError.ConnectionAttemptFailed, "The connection attempt failed.");
	}

	public ConnectionAttemptFailedSoftnetException(String message)
	{
		super(SoftnetError.ConnectionAttemptFailed, message);
	}
}
