package softnet.exceptions;

public class SecuritySoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -1233388272318759235L;

	public SecuritySoftnetException(String message) 
	{
		super(SoftnetError.AccessDenied, message);
	}
}
