package softnet.client;

public class ResponseContext 
{
	public final ClientEndpoint clientEndpoint;
	public final RemoteService remoteService;
	public final Object attachment;
	
	public ResponseContext(ClientEndpoint clientEndpoint, RemoteService remoteService, Object attachment)
	{
		this.clientEndpoint = clientEndpoint;
		this.remoteService = remoteService;
		this.attachment = attachment;
	}
}
