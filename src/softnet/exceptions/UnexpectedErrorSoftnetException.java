package softnet.exceptions;

public class UnexpectedErrorSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = 1653417669572702260L;

	public UnexpectedErrorSoftnetException(int errorCode)
	{
		super(SoftnetError.UnexpectedError, String.format("The softnet server has returned an unexpected error code %d.", errorCode));
	}
	
	public UnexpectedErrorSoftnetException(int errorCode, java.net.InetAddress serverAddress)
	{
		super(SoftnetError.UnexpectedError, String.format("The softnet server '%s' has returned an unexpected error code %d.", serverAddress.toString(), errorCode));
	}
	
	public UnexpectedErrorSoftnetException(String message)
	{
		super(SoftnetError.UnexpectedError, String.format("Unexpected error occurred. Text: '%s'.", message));
	}
}
