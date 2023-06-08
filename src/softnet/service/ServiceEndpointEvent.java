package softnet.service;

import java.util.EventObject;

public class ServiceEndpointEvent extends EventObject
{
	private static final long serialVersionUID = 9204065529106751890L;
	public ServiceEndpointEvent(ServiceEndpoint source) 
	{
		super(source);
	}
	
	public ServiceEndpoint getEndpoint()
	{
		return (ServiceEndpoint)getSource();
	}
}
