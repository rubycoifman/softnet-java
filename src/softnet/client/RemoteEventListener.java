package softnet.client;
import softnet.exceptions.SoftnetException;

public interface RemoteEventListener
{
	void accept(ClientEndpoint clientEndpoint, RemoteEvent remoteEvent);
	void acceptError(ClientEndpoint clientEndpoint, SoftnetException exception);
}