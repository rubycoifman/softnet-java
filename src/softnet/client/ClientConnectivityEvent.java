package softnet.client;

import java.util.EventObject;
import softnet.EndpointConnectivity;

public class ClientConnectivityEvent extends EventObject
{
	private static final long serialVersionUID = -3706981364953373925L;

	public ClientConnectivityEvent(ClientEndpoint source)
	{
		super(source);
	}
	
	public EndpointConnectivity getConnectivity()
	{
		return ((ClientEndpoint)getSource()).getConnectivity();
	}	
}