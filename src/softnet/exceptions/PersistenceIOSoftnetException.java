package softnet.exceptions;

public class PersistenceIOSoftnetException extends PersistenceSoftnetException
{
	private static final long serialVersionUID = -8927569741890781964L;
	public PersistenceIOSoftnetException() {}
	public PersistenceIOSoftnetException(String message)
	{
		super(message);
	}
}
