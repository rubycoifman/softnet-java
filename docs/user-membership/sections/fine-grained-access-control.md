---
layout: default
title: 10.3. Fine-grained access control
parent: 10. User Membership
nav_order: 3
---

## 10.3. Fine-grained access control

Perhaps your application requires access control to be implemented at a higher level than the native Softnet request handling methods. This may be the case if your application uses REST or some RPC mechanism built on top of TCP. Then access control will most likely be implemented at the level of REST or RPC handlers, respectively.
Let's take a look at the approach used to implement the described scenario. In either case, the client request first goes to the platform’s native request handler. It can be a TCP or UDP listener, or an RPC procedure. In the first step, your application must accept the request without access restrictions. This is done by the first overload of the native request handler. The handler takes the membership user from the <span class="datatype">RequestContext</span> object and passes it to the upper layer protocol.
Also in the first step, your handler can apply an access rule to filter out requests that do not have the minimum permissions required by the upper layer protocol.
Here is an example with a TCP listener. It shows how to take the <span class="datatype">MembershipUser</span> object from the request context. In the first step, it defines the listener with no access restrictions:
```java
serviceEndpoint.tcpListen(10, null, 5);
```
In the second step, it defines the request handler:
```java
serviceEndpoint.tcpAccept(10, new TCPRequestHandler() {
    @Override
    public void accept(RequestContext context, SocketChannel socketChannel, ConnectionMode mode) {
        MembershipUser membershipUser = context.user;
        // your code
    }
});
```