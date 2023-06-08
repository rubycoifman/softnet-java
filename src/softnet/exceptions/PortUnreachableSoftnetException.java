package softnet.exceptions;

public class PortUnreachableSoftnetException extends SoftnetException 
{
	private static final long serialVersionUID = 1419665543633945665L;

	public PortUnreachableSoftnetException(int virtualPort) 
	{
		super(SoftnetError.PortUnreachable, String.format("The virtual port %d of a remote service is unreachable.", virtualPort));
	}

	public PortUnreachableSoftnetException(String message) 
	{
		super(SoftnetError.PortUnreachable, message);
	}
}
