package softnet.exceptions;

public class InvalidClientCategorySoftnetException extends CriticalSoftnetException
{
	private static final long serialVersionUID = 5353171164556964344L;

	public InvalidClientCategorySoftnetException(softnet.client.ClientURI clientURI)
	{			
		super(SoftnetError.InvalidClientCategory, String.format("The client category '%s' specified in '%s' is not valid.", categoryToString(clientURI.category), clientURI.value));
	}
	
	private static String categoryToString(softnet.client.ClientCategory category)
	{
		if(category == softnet.client.ClientCategory.SingleService)
			return "Single-Service Stateful";
		if(category == softnet.client.ClientCategory.MultiService)
			return "Multi-Service Stateful";
		if(category == softnet.client.ClientCategory.SingleServiceStateless)
			return "Single-Service Stateless";
		return "Multi-Service Stateless";		
	}
}
