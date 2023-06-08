package softnet.exceptions;

public class RestartDemandedSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = 3400619694030276862L;
	public RestartDemandedSoftnetException()
	{
		super(SoftnetError.RestartDemanded, "The softnet server demanded to restart the endpoint.");
	}
}
