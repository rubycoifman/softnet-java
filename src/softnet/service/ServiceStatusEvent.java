package softnet.service;

import java.util.EventObject;

public class ServiceStatusEvent extends EventObject
{
	private static final long serialVersionUID = 9185539472459634857L;

	public ServiceStatusEvent(Object source)
	{
		super(source);
	}
}
