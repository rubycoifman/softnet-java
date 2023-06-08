package softnet.exceptions;

public class IncompatibleProtocolVersionSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = 5057477572673130016L;

	public IncompatibleProtocolVersionSoftnetException()
	{
		super(SoftnetError.IncompatibleProtocolVersion, "Incompatible softnet protocol version.");
	}
}
