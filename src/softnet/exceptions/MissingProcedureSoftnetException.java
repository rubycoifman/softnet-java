package softnet.exceptions;

public class MissingProcedureSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -8829426781068658306L;

	public MissingProcedureSoftnetException(String procedureName) 
	{
		super(SoftnetError.MissingProcedure, String.format("The procedure '%s' is missing on the remote service.", procedureName));
	}
}
