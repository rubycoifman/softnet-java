---
layout: default
title: 12.2.4. User Membership event handlers
parent: 12.2. Implementing event handlers
grand_parent: 12. Platform events related to services
nav_order: 4
---

## 12.2.4. User Membership event handlers

Interface <span class="datatype">ServiceEventListener</span> declares 5 event handlers associated with User Membership. Three of them have a parameter of type <span class="datatype">MembershipUserEvent</span>:
```java
void onUserIncluded(MembershipUserEvent e)
void onUserUpdated(MembershipUserEvent e)
void onUserRemoved(MembershipUserEvent e)
```
They are invoked when a management action taken by the owner on users has affected only one membership user, excluding Guest and Stateless Guest. Parameter 'e' contains the affected membership user in the <span class="field">user</span> field. The following example demonstrates this idea:
```java
public void onUserUpdated(MembershipUserEvent e)
{
    System.out.println(String.format("User updated -----------------"));
    System.out.println(String.format("ID: %d", e.user.getId()));
    System.out.println(String.format("Name: %s", e.user.getName()));
    if(e.user.hasRoles())
    {
        System.out.println("User roles:");
        for(String role: e.user.getRoles()) {
            System.out.println("   " + role);
        }
    }
}
```

If the management action could have affected two or more membership users, except for Guest and Stateless Guest, the following handler is invoked:
```java
void onUsersUpdated(ServiceEndpointEvent e)
```
This handler has a parameter of type <span class="datatype">ServiceEndpointEvent</span> that does not convey any information about affected users. The application can only get an updated list of users from the endpoint, as shown in the following example:
```java
public void onUsersUpdated(ServiceEndpointEvent e)
{
    ServiceEndpoint serviceEndpoint = e.getEndpoint();
    MembershipUser[] users = serviceEndpoint.getUsers();
    System.out.println(String.format("User list:"));
    for(MembershipUser user: users)
    {
        System.out.println(user.getName());
        for(String role: user.getRoles()) {
            System.out.println("  " + role);
        }
    }
}
```

We have one handler left to consider. It is invoked when the Guest status, allowed/denied, has changed:
```java
void onGuestAccessChanged(ServiceEndpointEvent e)
```

As usual, to get the updated Guest status do the following. In the handler’s body, call the <span class="method">getEndpoint</span> method on parameter 'e'. It returns the endpoint that invoked the handler. Then call the endpoint’s <span class="method">isGuestAllowed</span> method. It has the following signature:
```java
public boolean isGuestAllowed()
```

The example below demonstrates these steps:
```java
public void onGuestAccessChanged(ServiceEndpointEvent e)
{					
    ServiceEndpoint serviceEndpoint = e.getEndpoint();
    if(serviceEndpoint.isGuestAllowed())
        System.out.println(String.format("Guest Allowed"));
    else
        System.out.println(String.format("Guest Denied"));	
}
```