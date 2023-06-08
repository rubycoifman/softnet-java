package softnet.exceptions;

public class InputDataFormatSoftnetException extends SoftnetException 
{
	private static final long serialVersionUID = 3799687744268163541L;

	public InputDataFormatSoftnetException()
	{
		super(SoftnetError.InputDataFormatError, "Data received from the softnet server has an invalid format.");
	}

	public InputDataFormatSoftnetException(String message)
	{
		super(SoftnetError.InputDataFormatError, message);
	}
	
	public InputDataFormatSoftnetException(java.net.InetAddress serverAddress)
	{
		super(SoftnetError.InputDataFormatError, String.format("Data received from the softnet server '%s' has an invalid format.", serverAddress.toString()));
	}
	
	public InputDataFormatSoftnetException(java.net.InetAddress serverAddress, int port)
	{
		super(SoftnetError.InputDataFormatError, String.format("Data received from the softnet server '%s:%d' has an invalid format.", serverAddress.toString(), port));
	}
}