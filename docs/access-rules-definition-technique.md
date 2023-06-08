---
layout: default
title: 15. Access rules definition technique
nav_order: 15
---

## 15. Access rules definition technique

Let's remember that a Softnet domain contains two built-in users – Owner and Guest. The administrator can also create Private users and Contact users. Owner is associated with the project owner. Guest is a generalized user that represents all anonymous clients. Guest clients can be stateful or stateless. The latter connect to the site without authentication using a shared guest URI. The users membership of the service consists of domain users added to the site by assigning them access rights. When the service connects to the site, Softnet loads the users membership into the service application. Every client on the site is associated with a particular user from the users membership and inherits its access rights. With each request, Softnet provides the service with a membership user object corresponding to the client from which the request is received. This object contains the name of the user as well as its roles if the application employs role-based access control (RBAC).  

Finally, let's see why do we need stateful and stateless guest clients. The only reason is the ability to receive Private events. A stateful client can receive Private events, but requires to have an account and a unique URI, which is inconvenient for anonymous users. In contrast, a stateless client does not have an account and use a shared guest URI, but unable to receive Private events. They are both guest clients, but differ in terms of capabilities and convenience. If a request handler you define can raise Private events and is expected to serve guest clients, you must deny stateless guest clients in the handler's access rule while allowing stateful guest clients. To do this, specify the StatelessGuestDenied value from the GuestAccess enumeration. Of course, along with stateful guests, this rule also grants access to all named users.  

Softnet implements four communication patterns, and all of them support access control. The platform uses a unified technique for defining access rules. Each of the Softnet library methods designed to handle client requests has three overloaded variants that differ in the way of defining access rules. The first variant applies no access restrictions to clients. The second overloaded method has at the end one more parameter of <span class="datatype">GuestAccess</span> enumeration type. This parameter specifies what kind of guest clients are denied. The enumeration contains two elements:
```java
public enum GuestAccess { GuestDenied, StatelessGuestDenied }
```
If you provide <span class="field">GuestDenied</span>, all guest clients will be denied, but all clients from named membership users will be allowed. If you provide <span class="field">StatelessGuestDenied</span>, stateless guest clients will be denied, but stateful guest clients and clients from named membership users will be allowed. The third variant of the method also has one more parameter at the end, but it is of string type. In this parameter, you provide the list of authorized roles delimited with a semicolon. Of course, the third variant of the method is only applicable if your application employs RBAC. Note that the entire list of user roles must be defined in advance using the <span class="datatype">SiteStructure</span>.<span class="method">setRoles</span> method.  

The following list presents five built-in Softnet API methods that apply the described technique. Each of them has three overloads for different ways of defining access rules:
*	<span class="datatype">SiteStructure</span>.<span class="method">addReplacingEvent(…)</span> – defines a Replacing event in the site structure;
*	<span class="datatype">SiteStructure</span>.<span class="method">addQueueingEvent(…)</span> – defines a Queueing event in the site structure;
*	<span class="datatype">SoftnetEndpoint</span>.<span class="method">tcpListen(…)</span> – creates a TCP listener bound to a virtual port that handles requests for establishing TCP connections;
*	<span class="datatype">SoftnetEndpoint</span>.<span class="method">udpListen(…)</span> – creates a UDP listener bound to a virtual port that handles requests for establishing UDP connections;
*	<span class="datatype">SoftnetEndpoint</span>.<span class="method">registerProcedure(…)</span> – registers a procedure for handling RPC requests.  

Let's see how it all looks with examples.

**Example 1**. The <span class="datatype">SiteStructure</span> interface has three overloaded methods for defining a Replacing event. They differ in the way of defining access rules:
```java
void addReplacingEvent(String eventName)
void addReplacingEvent(String eventName, GuestAccess guestAccess)
void addReplacingEvent(String eventName, String roles)
```
Corresponding use cases:  

1) A Replacing event with no access restrictions – all users including Guest as well as stateless Guest are authorized to subscribe to the event:
```java
siteStructure.addReplacingEvent("Current Temperature");
```

2) A Replacing event "Soil Moisture". All named users and Guest are authorized, but stateless guest is denied:
```java
siteStructure.addReplacingEvent("Soil Moisture", GuestAccess.StatelessGuestDenied);
```

3) A Replacing event "Current Pressure". Here, only two user roles are authorized to subscribe to the event:
```java
siteStructure.addReplacingEvent("Current Pressure", "Administrator; Operator");
```

**Example 2**. The <span class="datatype">ServiceEndpoint</span> class has three overloaded methods for listening a TCP virtual port. They follow the same technique described above for defining access rules:
```java
void tcpListen(int virtualPort, TCPOptions tcpOptions, int backlog)
void tcpListen(int virtualPort, TCPOptions tcpOptions, int backlog, GuestAccess guestAccess)
void tcpListen(int virtualPort, TCPOptions tcpOptions, int backlog, String roles)
```

Corresponding use cases:  

1) The TCP listener accepts connection requests on virtual port 10. All users including Guest are authorized:
```java
serviceEndpoint.tcpListen(10, null, 5);
```

2) The listener binds to the virtual port 11. All named membership users are authorized to the method. Guest and Stateless Guest are denied:
```java
serviceEndpoint.tcpListen(11, null, 5, GuestAccess.GuestDenied);
```

3) The listener accepts connection requests on virtual port 12. Only two roles, "Administrator " and "Operator ", are authorized:
```java
serviceEndpoint.tcpListen(12, null, 5, "Administrator; Operator");
```
