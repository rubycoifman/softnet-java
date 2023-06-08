package softnet.exceptions;

public class InvalidServerEndpointSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = 754574313424304194L;

	public InvalidServerEndpointSoftnetException(String message)
	{
		super(SoftnetError.InvalidServerEndpoint, message);
	}

	public InvalidServerEndpointSoftnetException(java.net.InetAddress peerAddress)
	{
		super(SoftnetError.InvalidServerEndpoint, String.format("The server endpoint '%s' is not valid.", peerAddress.toString()));
	}

	public InvalidServerEndpointSoftnetException(java.net.InetAddress peerAddress, int port)
	{
		super(SoftnetError.InvalidServerEndpoint, String.format("The server endpoint '%s:%d' is not valid.", peerAddress.toString(), port));
	}
}
