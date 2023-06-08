package softnet.exceptions;

public class ServerBusySoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -273573975703394249L;

	public ServerBusySoftnetException(String serverAddress)
	{
		super(SoftnetError.ServerBusy, String.format("The Softnet server '%s' is busy.", serverAddress));
	}	
	
	public ServerBusySoftnetException()
	{
		super(SoftnetError.ServerBusy, "The Softnet server is busy.");
	}	
}
