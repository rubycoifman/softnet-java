package softnet.exceptions;

import java.util.UUID;

public class DublicatedServiceUidUsageSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -482564307968540445L;

	public DublicatedServiceUidUsageSoftnetException(UUID serviceUid)
	{
		super(SoftnetError.DublicatedServiceUidUsage, String.format("Dublicated usage of the service Uid '%s'.", serviceUid.toString()));
	}
}
