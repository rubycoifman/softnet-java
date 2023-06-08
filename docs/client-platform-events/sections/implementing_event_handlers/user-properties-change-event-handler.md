---
layout: default
title: 13.2.3. User properties change event handler
parent: 13.2. Implementing event handlers
grand_parent: 13. Platform events related to clients
nav_order: 3
---

## 13.2.3. User properties change event handler

The handler signature: 
```java
void onUserUpdated(ClientEndpointEvent e)
```

In the handler’s body, call the <span class="method">getEndpoint</span> method on the 'e' parameter. It returns the endpoint that invoked the handler. Then call the endpoint’s <span class="method">getUser</span> method which has the following signature:
```java
public MembershipUser getUser()
```
Using the <span class="datatype">MembershipUser</span> API, you can check if the user is a named user with certain permissions, Guest or Stateless Guest. The example below demonstrates the handler implementation:
```java
public void onUserUpdated(ClientEndpointEvent e) {
    ClientEndpoint clientEndpoint = e.getEndpoint();
    MembershipUser user = clientEndpoint.getUser();
    System.out.println(String.format("Membership User: %s ", user.getName()));
    if(user.hasRoles()) {
        System.out.println("Roles:");
        for(String role: user.getRoles())
            System.out.println("  " + role);
    }			
}
```