---
layout: default
title: 13.2.4. Service Group event handlers
parent: 13.2. Implementing event handlers
grand_parent: 13. Platform events related to clients
nav_order: 4
---

## 13.2.4. Service Group event handlers

Interface <span class="datatype">ClientEventListener</span> declares 6 event handlers associated with Service Group. The first 5 of them have a parameter of type <span class="datatype">RemoteServiceEvent</span>:
```java
void onServiceOnline(RemoteServiceEvent e)
void onServiceOffline(RemoteServiceEvent e)
void onServiceIncluded(RemoteServiceEvent e)
void onServiceRemoved(RemoteServiceEvent e)
void onServiceUpdated(RemoteServiceEvent e)
```
They are invoked when only one remote service has its properties changed. Parameter e contains the affected remote service in the service field. The following example demonstrates this idea:
```java
public void onServiceOnline(RemoteServiceEvent e) {
	System.out.println("Remote service online!");					
	System.out.println("Hostname: " + e.service.getHostname());
	System.out.println("Service Version: " + e.service.getVersion());
}
```

If two or more remote services could have their properties changed the following handler is invoked:
```java
void onServicesUpdated(ClientEndpointEvent e)
```

The parameter e does not convey information about affected services. The application can only get an updated list of <span class="datatype">RemoteService</span> objects from the endpoint and look through the properies, as shown in the following example:
```java
public void onServicesUpdated(ClientEndpointEvent e) {
    ClientEndpoint clientEndpoint = e.getEndpoint();
    RemoteService[] remoteServices = clientEndpoint.getServices();
    System.out.println("Remote service list updated!");					
    for(RemoteService service: remoteServices) {
        System.out.println("Hostname: " + service.getHostname());
        System.out.println("Version: " + service.getVersion());
        System.out.println();
    }
}
```