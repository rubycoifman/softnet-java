package softnet.exceptions;

public class ServiceBusySoftnetException extends SoftnetException 
{
	private static final long serialVersionUID = -7534668484830222014L;

	public ServiceBusySoftnetException(String message)
	{
		super(SoftnetError.ServiceBusy, message);
	}
	
	public ServiceBusySoftnetException()
	{
		super(SoftnetError.ServiceBusy, "The remote service is busy.");
	}
}
