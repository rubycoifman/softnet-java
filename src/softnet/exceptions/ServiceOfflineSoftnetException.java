package softnet.exceptions;

public class ServiceOfflineSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = 1334109502772271033L;

	public ServiceOfflineSoftnetException()
	{
		super(SoftnetError.ServiceOffline, "The remote service is offline.");
	}
}
