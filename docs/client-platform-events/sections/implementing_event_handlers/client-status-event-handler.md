---
layout: default
title: 13.2.2. Client status event handler
parent: 13.2. Implementing event handlers
grand_parent: 13. Platform events related to clients
nav_order: 2
---

## 13.2.2. Client status event handler

After the control channel is established, the site checks the connected client against the set of parameters provided by the endpoint as well as those set by the administrator on the site. If the client is eligible, it installs on the site. Then, the site assigns it an online status, and it is ready to interact with services. Otherwise, based on the reason for ineligibility, the site assigns it a status from the <span class="datatype">ClientStatus</span> enumeration. The whole process ends with a call to the following handler:
```java
void onStatusChanged(ClientEndpointEvent e)
```

To get the updated client status, follow these steps. In the handler’s body, call the <span class="method">getEndpoint</span> method on the 'e' parameter. It returns the endpoint, which is of type <span class="datatype">ClientEndpoint</span>, that invoked the handler. Then call the endpoint’s <span class="method">getStatus</span> method. It has the following signature:
```java
public ClientStatus getStatus()
```

The example below demonstrates these steps:
```java
public void onStatusChanged(ClientEndpointEvent e)
{
    ClientEndpoint clientEndpoint = e.getEndpoint();
    ClientStatus clientStatus = clientEndpoint.getStatus();
    System.out.println(String.format("The client status: %s", clientStatus)); 
}
```

Let's consider the status names from the <span class="datatype">ClientStatus</span> enumeration and their interpretation.
```java
public enum ClientStatus
{
    Offline,
    Online,
    ServiceTypeConflict,
    AccessDenied,    
    ServiceOwnerDisabled,
    SiteDisabled,
    CreatorDisabled
}
```
*	<span class="text-monospace">Offline</span> – the client is offline while the endpoint is not connected to the site;
*	<span class="text-monospace">Online</span> – the client is ready to communicate with services;
*	<span class="text-monospace">ServiceTypeConflict</span> – the Service Type and Contract Author provided by the client are different from those declared on the site;
*	<span class="text-monospace">AccessDenied</span> – the client does not have any access rights to the service, while the Guest is not allowed or is not supported by the service;
*	<span class="text-monospace">ServiceOwnerDisabled</span> – the service owner is disabled by the Softnet MS administrator;
*	<span class="text-monospace">ServiceDisabled</span> – the service is disabled by the owner;
*	<span class="text-monospace">CreatorDisabled</span> – the client’s creator is disabled by the Softnet MS administrator. The creator can be the Owner or one of their contacts.
