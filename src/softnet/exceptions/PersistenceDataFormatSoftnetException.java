package softnet.exceptions;

public class PersistenceDataFormatSoftnetException extends PersistenceSoftnetException
{
	private static final long serialVersionUID = 7839856683215482655L;
	public PersistenceDataFormatSoftnetException() {}
	public PersistenceDataFormatSoftnetException(String message)
	{
		super(message);
	}
}
