package softnet.client;

interface EventController {
	void setPersistenceL2();
	void setPersistenceL2(String fileBasedPersistenceDirectory);
	void onConnectCalled();
	void onEndpointConnected(Channel channel);
	void onEndpointDisconnected();
	void onEndpointClosed();
	void subscribeToREvent(String eventName, RemoteEventListener listener);
	void subscribeToQEvent(String eventName, RemoteEventListener listener);
	void subscribeToPEvent(String eventName, RemoteEventListener listener);
	boolean removeSubscription(String eventName);
	void addEventListener(ClientEventListener listener);
	void removeEventListener(ClientEventListener listener);
}
