package softnet.exceptions;

public class IllegalNameSoftnetException extends SoftnetException 
{
	private static final long serialVersionUID = -7948929666154279163L;

	public IllegalNameSoftnetException(String message)
	{
		super(SoftnetError.IllegalName, message);
	}
}
