package softnet.client;

public class ClientEventAdapter implements ClientEventListener
{
	public void onConnectivityChanged(ClientEndpointEvent e) {}
	public void onStatusChanged(ClientEndpointEvent e) {}
	public void onUserUpdated(ClientEndpointEvent e) {}
	public void onServiceOnline(RemoteServiceEvent e) {}
	public void onServiceOffline(RemoteServiceEvent e) {}
	public void onServiceIncluded(RemoteServiceEvent e) {}
	public void onServiceRemoved(RemoteServiceEvent e) {}
	public void onServiceUpdated(RemoteServiceEvent e) {}	
	public void onServicesUpdated(ClientEndpointEvent e) {}
	public void onPersistenceFailed(ClientPersistenceFailedEvent e) {}
}
