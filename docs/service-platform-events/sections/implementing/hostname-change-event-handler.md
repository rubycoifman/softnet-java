---
layout: default
title: 12.2.3. Hostname change event handler
parent: 12.2. Implementing event handlers
grand_parent: 12. Platform events related to services
nav_order: 3
---

## 12.2.3. Hostname change event handler

The handler signature: 
```java
void onHostnameChanged(ServiceEndpointEvent e)
```

The procedure of getting the updated hostname is similar to the previous ones. In the handler’s body, call the <span class="method">getEndpoint</span> method on the 'e' parameter. It returns the endpoint that invoked the handler. Then call the endpoint’s <span class="method">getHostname</span> method. It has the following signature:
```java
public String getHostname()
```

The example below demonstrates these steps:
```java
public void onHostnameChanged(ServiceEndpointEvent e)
{
    ServiceEndpoint serviceEndpoin = e.getEndpoint();
    System.out.println(String.format("The updated service hostname: %s", serviceEndpoin.getHostname()));
}
```
