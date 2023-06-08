package softnet.exceptions;

public class ServerDataIntegritySoftnetException extends SoftnetException
{
	private static final long serialVersionUID = 44347960162023867L;

	public ServerDataIntegritySoftnetException()
	{
		super(SoftnetError.ServerDataIntegrityError, "The Softnet server encountered a data integrity error.");
	}
}
