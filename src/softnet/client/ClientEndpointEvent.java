package softnet.client;

import java.util.EventObject;

public class ClientEndpointEvent extends EventObject
{
	private static final long serialVersionUID = 3282433864310988161L;
	public ClientEndpointEvent(ClientEndpoint source) 
	{
		super(source);
	}
	
	public ClientEndpoint getEndpoint()
	{
		return (ClientEndpoint)getSource();
	}
}
