package softnet.service;

import softnet.exceptions.PersistenceSoftnetException;

public class ServicePersistenceFailedEvent extends ServiceEndpointEvent 
{
	private static final long serialVersionUID = 2676016261779721044L;
	public final PersistenceSoftnetException exception;

	public ServicePersistenceFailedEvent(PersistenceSoftnetException ex, ServiceEndpoint source) {
		super(source);
		exception = ex; 
	}
}
