package softnet.exceptions;

public class NetworkErrorSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = 1172090073823663975L;

	public NetworkErrorSoftnetException(String message)
	{
		super(SoftnetError.NetworkError, message);
	}
}