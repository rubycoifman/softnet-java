package softnet.client;

interface ServiceGroup 
{
	RemoteService findService(long serviceId);
	RemoteService findService(String hostname);
	RemoteService[] getServices();
	
	void onEndpointConnected(Channel channel);
	void onEndpointDisconnected();
	void onClientOnline();
	void addEventListener(ClientEventListener listener);
	void removeEventListener(ClientEventListener listener);
	byte[] getHash()  throws softnet.exceptions.HostFunctionalitySoftnetException;
}
