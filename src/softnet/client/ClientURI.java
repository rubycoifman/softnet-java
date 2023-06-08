package softnet.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public class ClientURI
{
	public final String scheme;
	public final String clientKey;
	public final String server;
	public final ClientCategory category;
	public final String value;
	
	public boolean isSingleService()
	{
		if(category == ClientCategory.SingleService || category == ClientCategory.SingleServiceStateless)
			return true;
		return false;
	}

	public boolean isMultiService()
	{
		if(category == ClientCategory.MultiService || category == ClientCategory.MultiServiceStateless)
			return true;
		return false;
	}

	public boolean isStateless()
	{
		if(category == ClientCategory.SingleServiceStateless || category == ClientCategory.MultiServiceStateless)
			return true;
		return false;
	}

	public boolean isStateful()
	{
		if(category == ClientCategory.SingleService || category == ClientCategory.MultiService)
			return true;
		return false;
	}

	public ClientURI(String value)
	{		
		try
		{
			this.value = value;
			
			URI uri = new URI(value);		
			String providedUriScheme = uri.getScheme();
			String providedServerAddress = uri.getHost();			
			String providedClientKey = uri.getUserInfo();
						
			if(providedUriScheme == null)
				throw new IllegalArgumentException("Invalid client URI. The URI scheme is not provided.");

			if(providedServerAddress == null)
				throw new IllegalArgumentException("Invalid client URI. The server address is not provided.");

			if(providedClientKey == null)
				throw new IllegalArgumentException("Invalid client URI. The client key is not provided.");
			
			String[] items = providedUriScheme.split("-");
			if(items.length != 2)
				throw new IllegalArgumentException(String.format("Invalid client URI. The URI scheme '%s' is invalid", providedUriScheme));
	
			if(items[0].equals("softnet") == false)
				throw new IllegalArgumentException(String.format("Invalid client URI. The URI scheme '%s' is invalid", providedUriScheme));
						
			if(items[1].equals("s"))
			{
				this.category = ClientCategory.SingleService;
			}
			else if(items[1].equals("m"))
			{
				this.category = ClientCategory.MultiService;
			}
			else if(items[1].equals("ss"))
			{
				this.category = ClientCategory.SingleServiceStateless;
			}
			else if(items[1].equals("ms"))
			{
				this.category = ClientCategory.MultiServiceStateless;
			}
			else
				throw new IllegalArgumentException(String.format("Invalid client URI. The URI scheme '%s' is invalid", providedUriScheme));
									
			if(providedClientKey.length() < 4 || providedClientKey.length() > 64)
				throw new IllegalArgumentException(String.format("Invalid client URI. The length of the client key must be in the range 4-64.", providedClientKey));
			
			if (Pattern.matches("^[a-zA-Z0-9]+$", providedClientKey) == false)			
	            throw new IllegalArgumentException(String.format("Invalid client URI. The client key '%s' contains illegal characters.", providedClientKey));			
			
			if (Pattern.matches("^[a-zA-Z0-9_.\\-]+$", providedServerAddress) == false)				
				throw new IllegalArgumentException(String.format("Invalid client URI. The server address '%s' contains illegal characters.", providedServerAddress));
			
			this.scheme = providedUriScheme;
			this.server = providedServerAddress;			
			this.clientKey = providedClientKey;
		}
		catch(URISyntaxException e)
		{
			throw new IllegalArgumentException(e.getMessage());
		}
	}	
}
