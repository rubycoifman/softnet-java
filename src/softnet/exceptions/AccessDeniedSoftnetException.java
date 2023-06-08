package softnet.exceptions;

public class AccessDeniedSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -5050790137120469391L;

	public AccessDeniedSoftnetException() 
	{
		super(SoftnetError.AccessDenied, "Access is denied.");
	}

	public AccessDeniedSoftnetException(String message) 
	{
		super(SoftnetError.AccessDenied, message);
	}
}
