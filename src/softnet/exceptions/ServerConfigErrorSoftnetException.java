package softnet.exceptions;

public class ServerConfigErrorSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = 6950041359757191765L;

	public ServerConfigErrorSoftnetException()
	{
		super(SoftnetError.ServerConfigError, "The Softnet server encountered a configuration error.");
	}
}
