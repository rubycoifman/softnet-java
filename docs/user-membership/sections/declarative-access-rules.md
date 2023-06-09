---
layout: default
title: 10.2. Declarative definition of access rules
parent: 10. User Membership
nav_order: 2
---

## 10.2. Declarative definition of access rules

Softnet uses a unified technique for defining access rules declaratively. [Chapter 15]({{ site.baseurl }}{% link docs/access-rules.md %}) gives a detailed description on this topic. Each of the Softnet native request handling methods is implemented in three overloads that differ in the way of defining access rules. The first overload applies no access restrictions to clients. The second overload applies restrictions only to guest clients. And the third overload is applicable if your service employs the role-based access control provided by Softnet. This method applies an access rule that allows access only to clients that have at least one of the roles specified in the access rule definition.  

Let’s see how access control works using rules applied declaratively. Any client request is supplied with two parameters - user kind and user ID. The request controller makes a call to User Membership to resolve the user on these parameters. The first parameter, user kind, specifies whether the user is a named user, Guest, or Stateless Guest. In the last two cases, the membership user is already known. However, if the user kind specifies a named user, User Membership looks for it in the registry. If successful, the controller gets the membership user. If the user has not been found, it is resolved as Guest. Having the membership user, the request controller can easily check the user's permissions against the access rule specified in the handler. If successfully, it places the membership user into the user field of the <span class="datatype">RequestContext</span> object and passes it to the handler. The handler, in turn, can pass it to higher-level communication protocols for more granular access control. This is a topic of the [next section]({{ site.baseurl }}{% link docs/user-membership/sections/fine-grained-access-control.md %}).  

Below you are presented with signatures of three overloaded methods for binding to a TCP virtual port. For simplicity, the first three parameters are not shown:
```java
void tcpListen(...)
void tcpListen(..., GuestAccess guestAccess)
void tcpListen(..., String roles)
```

And the following is a use case for the third method. It shows the creation of a TCP binding to virtual port 10. The access rule specifies two user roles that are authorized to establish TCP connections. If the client has none of these roles, it gets an access denied exception:
```java
serviceEndpoint.tcpListen(10, null, 5, "Administrator; Operator");
```

The next is a use case of the second overloaded method that denies stateless guest clients:
```java
serviceEndpoint.tcpListen(10, null, 5, GuestAccess.StatelessGuestDenied);
```