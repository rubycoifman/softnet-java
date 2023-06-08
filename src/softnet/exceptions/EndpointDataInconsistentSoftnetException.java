package softnet.exceptions;

public class EndpointDataInconsistentSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -3546027528390759018L;

	public EndpointDataInconsistentSoftnetException()
	{
		super(SoftnetError.EndpointDataInconsistent, "The data sent to the softnet server is inconsistent.");
	}
}
