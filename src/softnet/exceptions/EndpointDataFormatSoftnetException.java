package softnet.exceptions;

public class EndpointDataFormatSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -5333712111452368510L;

	public EndpointDataFormatSoftnetException()
	{
		super(SoftnetError.EndpointDataFormatError, "The data sent to the softnet server is invalid.");
	}
}
