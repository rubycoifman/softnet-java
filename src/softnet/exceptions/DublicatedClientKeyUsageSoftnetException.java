package softnet.exceptions;

public class DublicatedClientKeyUsageSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -482564307968540445L;

	public DublicatedClientKeyUsageSoftnetException(softnet.client.ClientURI clientKey)
	{
		super(SoftnetError.DublicatedClientKeyUsage, String.format("Dublicated usage of the client key '%s'.", clientKey.value));
	}
}
