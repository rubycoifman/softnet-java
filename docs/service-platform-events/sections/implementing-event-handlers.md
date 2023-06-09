---
layout: default
title: 12.2. Implementing event handlers
parent: 12. Platform events related to services
has_children: true
nav_order: 2
---

## 12.2. Implementing event handlers

Some handlers have a parameter 'e' of type <span class="datatype">ServiceEndpointEvent</span>. Its only member of our interest is the <span class="method">getEndpoint</span> method with the following signature:
```java
public ServiceEndpoint getEndpoint()
```
The method returns an endpoint, which is of type <span class="datatype">ServiceEndpoint</span>, that invoked the handler. In such case, your application gets the updated object, that caused an event to be raised, from the endpoint as the handler’s parameter e does not contain it.  

Some of the handlers associated with Users Membership have a parameter e of type <span class="datatype">MembershipUserEvent</span> derived from <span class="datatype">ServiceEndpointEvent</span>. Along with a derived method <span class="method">getEndpoint</span>, it has a field <span class="field">user</span> of type <span class="datatype">MembershipUser</span>. This is a membership user that caused the event to be raised by the platform.  

Let's look at how to work with each of the event handlers declared by the <span class="datatype">ServiceEventListener</span> interface.