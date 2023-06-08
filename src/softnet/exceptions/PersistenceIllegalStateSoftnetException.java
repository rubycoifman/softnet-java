package softnet.exceptions;

public class PersistenceIllegalStateSoftnetException extends PersistenceSoftnetException
{
	private static final long serialVersionUID = -514932548283718592L;
	public PersistenceIllegalStateSoftnetException() {}
	public PersistenceIllegalStateSoftnetException(String message)
	{
		super(message);
	}
}
