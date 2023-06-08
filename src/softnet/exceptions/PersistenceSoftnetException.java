package softnet.exceptions;

public class PersistenceSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -7631028562315935894L;
	public PersistenceSoftnetException()
	{
		super(SoftnetError.PersistenceError);
	}
	public PersistenceSoftnetException(String message)
	{
		super(SoftnetError.PersistenceError, message);
	}
}
