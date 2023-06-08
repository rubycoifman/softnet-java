package softnet.client;

public class RemoteServiceEvent extends ClientEndpointEvent
{		
	private static final long serialVersionUID = -2915287403390716834L;
	public final RemoteService service;
	
	public RemoteServiceEvent(RemoteService service, ClientEndpoint source)
	{
		super(source);
		this.service = service;
	}
}