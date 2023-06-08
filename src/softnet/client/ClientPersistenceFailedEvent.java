package softnet.client;

import softnet.exceptions.PersistenceSoftnetException;

public class ClientPersistenceFailedEvent extends ClientEndpointEvent 
{
	private static final long serialVersionUID = -1602993696370585911L;
	public final PersistenceSoftnetException exception;
	
	public ClientPersistenceFailedEvent(PersistenceSoftnetException ex, ClientEndpoint source) {
		super(source);
		exception = ex; 
	}
}
