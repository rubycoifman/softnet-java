package softnet.exceptions;

public class HostFunctionalitySoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -1993083820798060986L;

	public HostFunctionalitySoftnetException(String message)
	{
		super(SoftnetError.UnsupportedHostFunctionality, message);
	}
}
