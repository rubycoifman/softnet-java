package softnet.exceptions;

public class ServerDbmsErrorSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -9120392693288437004L;

	public ServerDbmsErrorSoftnetException()
	{
		super(SoftnetError.ServerDbmsError, "The Softnet server encountered a dbms error.");
	}
	
	public ServerDbmsErrorSoftnetException(String serverAddress)
	{
		super(SoftnetError.ServerDbmsError, String.format("The Softnet server '%s' encountered a dbms error.", serverAddress));
	}

	public ServerDbmsErrorSoftnetException(java.net.InetAddress serverAddress)
	{
		super(SoftnetError.ServerDbmsError, String.format("The Softnet server '%s' encountered a dbms error.", serverAddress.toString()));
	}
}
