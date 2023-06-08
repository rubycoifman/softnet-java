package softnet.exceptions;

public class PersistenceStorageFullSoftnetException extends PersistenceSoftnetException
{
	private static final long serialVersionUID = 9186238533194242030L;
	public PersistenceStorageFullSoftnetException() {}
	public PersistenceStorageFullSoftnetException(String message)
	{
		super(message);
	}
}
