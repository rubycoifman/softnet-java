---
layout: default
title: 13.3. Adapter class ClientEventAdapter
parent: 13. Platform events related to clients
nav_order: 3
---

## 13.3. Adapter class ClientEventAdapter

As with services, Softnet provides a <span class="datatype">ClientEventAdapter</span> class designed to relieve listener classes from the burden of implementing handlers they don't need. The adapter class contains empty implementations of the interface methods. You can derive your listener class from the adapter and implement only those handlers that you need. Here is the adapter class definition:
```java
public class ClientEventAdapter implements ClientEventListener {
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
```

The following is an example of implementing a few of the handlers using an anonymous class derived from <span class="datatype">ClientEventAdapter</span>:
```java
clientEndpoint.addEventListener(new ClientEventAdapter() {
    @Override
    public void onStatusChanged(ClientEndpointEvent e)
    {
        ClientEndpoint clientEndpoint = e.getEndpoint();
        if(clientEndpoint.getStatus() == ClientStatus.Online) {
            System.out.println("The client is online!");
					
            if(clientEndpoint.isSingleService()) {						
                RemoteService remoteService = clientEndpoint.findService(0);
                if(remoteService.isOnline()) {
                    System.out.println("The remote service is online!");
                    System.out.println("Hostname: " + remoteService.getHostname());
                    System.out.println("Version: " + remoteService.getVersion());
                }
            }
            else {
                RemoteService[] remoteServices = clientEndpoint.getServices();
                for(RemoteService remoteService: remoteServices) {
                    if(remoteService.isOnline() == false)
                        continue;
                    System.out.println("The remote service is online!");
                    System.out.println("Hostname: " + remoteService.getHostname());
                    System.out.println("Version: " + remoteService.getVersion());
                }
            }
        }
    }
				
    @Override
    public void onServiceOnline(RemoteServiceEvent e) {
        System.out.println("The remote service is online!");			
        System.out.println("Hostname: " + e.service.getHostname());
        System.out.println("Version: " + e.service.getVersion());
    }
				
    @Override
    public void onServiceUpdated(RemoteServiceEvent e) {
        System.out.println("The remote service has been updated!");	
        System.out.println("Hostname: " + e.service.getHostname());
        System.out.println("Version: " + e.service.getVersion());
    }
});
```