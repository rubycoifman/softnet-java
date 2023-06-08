package softnet.exceptions;

public class InputDataInconsistentSoftnetException extends SoftnetException 
{
	private static final long serialVersionUID = 7923360475610844911L;

	public InputDataInconsistentSoftnetException()
	{
		super(SoftnetError.InputDataInconsistent, "The data received from the softnet server is inconsistent.");
	}

	public InputDataInconsistentSoftnetException(String message)
	{
		super(SoftnetError.InputDataInconsistent, message);
	}
	
	public InputDataInconsistentSoftnetException(java.net.InetAddress serverAddress)
	{
		super(SoftnetError.InputDataInconsistent, String.format("The data received from the softnet server '%s' is inconsistent.", serverAddress.toString()));
	}	
}
