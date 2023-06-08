---
layout: default
title: 13.1. Interface ClientEventListener
parent: 13. Platform events related to clients
nav_order: 1
---

## 13.1. Interface ClientEventListener

Softnet provides the <span class="datatype">ClientEventListener</span> interface that listener class implements to intercept client related events raised by the platform. An application provides the listener to the following method of the client endpoint: 
```java
public void addEventListener(ClientEventListener listener)
```

As with services, an application can provide multiple event listeners with different implementations. If you do not want to implement all methods of the interface, you can derive your handler from the <span class="datatype">ClientEventAdapter</span> class which contains empty implementations of the handlers. This allows you to implement only those handlers that you need.  
Below is the listener interface:
```java
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
```
Let’s consider each of the interface handlers. As with services, the model of Client Endpoint has two abstraction layers – Channel Layer and Functional Layer. The first two methods of <span class="datatype">ClientEventListener</span> intercept the status change events raised at these two abstraction layers.

*	<span class="method">onConnectivityChanged</span> is invoked whenever the connectivity status of the channel layer changes. After calling the endpoint’s connect method, the channel layer makes successive attempts to establish a control channel. In case of success, the connectivity status of the client endpoint is Connected;
*	<span class="method">onStatusChanged</span> interceptes the status change event of the functional layer. The functional layer status is the status of the client itself. The site assigns it to the client after the connectivity status becomes Connected. If the client gets the Online status, it can interact with the service. If the remote service has already been online at the time when the client gets online, the platform does not raise the <span class="datatype">ServiceOnline</span> event in order to avoid multiple events raising at the same time. If necessary, the client can check in the context of this handler whether the remote service is online. In the case of a multi-service site, the client may need to check the online status of each <span class="datatype">RemoteService</span> object in the Service Group. However, if the remote service gets online when the client has already been online, the platform raises the <span class="datatype">ServiceOnline</span> event. The client intercepts it by the <span class="method">onServiceOnline</span> handler.

The next 6 handlers are designed to intercept events raised by Service Group as a result of changing the list of services on the site, changing the service parameters or updating the online/offline status of the services:

*	<span class="method">onServiceOnline</span> – if your client is waiting for the service to get online, it can start to communicate on receiving this event. As already said, if the service has already been online at the time when the client gets online, the platform does not raise the ServiceOnline event in order to avoid multiple events raising at the same time. In this case, the client needs to check the online status of the service in the onStatusChanged handler;
*	<span class="method">onServiceOffline</span> is invoked when an online service becomes offline;
*	<span class="method">onServiceIncluded</span> is invoked when a new service is registered on the multi-service site. The event is raised only at a multi-service client endpoint;
*	<span class="method">onServiceRemoved</span> is invoked when the service is disabled, or removed from a multi-service site. The event is raised only at a multi-service client endpoint;
*	<span class="method">onServiceUpdated</span> is invoked when the service parameters has been updated;
*	<span class="method">onServicesUpdated</span> is invoked when the client becomes online and the Service Group detects that one o more services have been updated since the client’s last online session. The event is raised only at a multi-service client endpoint;  

The <span class="method">onUserUpdated</span> handler is called whenever the properties of a domain user that this client is associated with or its permissions to access the service change. You can use the information about the user’s permissions to make only those requests that are currently allowed to the user.  

The last handler, <span class="method">onPersistenceFailed</span>, is similar to that of the service’s one. It intercepts error notifications raised by the client persistence component.
