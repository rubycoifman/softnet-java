package softnet.client;

import java.util.EventListener;

public interface ClientEventListener extends EventListener
{
	void onConnectivityChanged(ClientEndpointEvent e);
	void onStatusChanged(ClientEndpointEvent e);
	void onUserUpdated(ClientEndpointEvent e);
	void onServiceOnline(RemoteServiceEvent e);
	void onServiceOffline(RemoteServiceEvent e);
	void onServiceIncluded(RemoteServiceEvent e);
	void onServiceRemoved(RemoteServiceEvent e);
	void onServiceUpdated(RemoteServiceEvent e);
	void onServicesUpdated(ClientEndpointEvent e);
	void onPersistenceFailed(ClientPersistenceFailedEvent e);
}