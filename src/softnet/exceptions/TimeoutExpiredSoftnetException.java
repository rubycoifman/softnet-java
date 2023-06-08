package softnet.exceptions;

public class TimeoutExpiredSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -1872806299059752679L;

	public TimeoutExpiredSoftnetException(String message)
	{
		super(SoftnetError.TimeoutExpired, message);
	}
}
