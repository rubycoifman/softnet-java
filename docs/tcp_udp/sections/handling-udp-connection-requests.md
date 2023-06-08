---
layout: default
title: 16.3. Handling UDP connection requests
parent: 16. TCP and UDP in dynamic IP environments
nav_order: 3
---

## 16.3. Handling UDP connection requests

The Softnet technique for working with UDP is almost identical to that described in the previous sections for TCP. However, it is necessary to clarify what it means to establish a UDP connection. After all, UDP doesn't use anything like TCP’s three-way handshake, and a UDP server is able to receive packets from any source with a valid IP address. However, in a dynamic IP environment, the UDP client and server have to establish the Internet path through which UDP packets will traverse. The network maintains state along the path as long as packets continue to traverse.  

As with TCP, the service endpoint accepts UDP connection requests on a virtual port. It is not associated with a physical port in any way. First, the service app creates a binding to the port. As with TCP, there are three overloaded methods to create a binding. They differ in the way they define access rules:
1. The first method does not apply access restrictions. It authorizes any request:
```java
void udpListen(int virtualPort, int backlog)
```
2. The second method creates an access rule for guest clients using the value specified in the third parameter:
```java
void udpListen(int virtualPort, int backlog, GuestAccess guestAccess)
```
3. The third method creates an access rule based on the list of user roles specified in the third parameter:
```java
void udpListen(int virtualPort, int backlog, String roles)
```

The first two parameters are identical to those of the <span class="method">tcpListen</span> method. Below is a short description:
*	<span class="param">virtualPort</span> is a virtual port on which the service accepts requests. It is not associated with a physical port which is not used at all. The possible values are not restricted;
*	<span class="param">backlog</span> specifies the maximum number of queued connections that are in the process of being established or have already been established but not yet accepted by the service application. The app accepts estableshed connections by the <span class="method">udpAccept</span> method, which is described below. If the queue is full, clients get the service busy exception. For most applications, 5 will be a reasonable value.  

The next method we have to consider is <span class="method">udpAccept</span>. As with TCP, it provides an accept handler to the listener that was earlier bound to the virtual port. If there is an established connection queued in the backlog, the handler is called back immediately. To accept the next connection from the backlog, <span class="method">udpAccept</span> should be called again. If the backlog is empty, the handler remains idle. You can control the consumption of the host resources by calling <span class="method">udpAccept</span> only when the resources are released for clients. The following is the signature of the method:
```java
public void udpAccept(int virtualPort, UDPAcceptHandler acceptHandler)
```
*	<span class="param">virtualPort</span> is a virtual port to which the UDP listener has been bound earlier;
*	<span class="param">acceptHandler</span> is a handler that implements the <span class="datatype">UDPAcceptHandler</span> interface. It has the only method <span class="method">accept</span> to be implemented. Its parameters are different from those of the analogous method of the TCP accept handler:  
```java
public interface UDPAcceptHandler {
    public void accept(
        RequestContext context,
        DatagramSocket datagramSocket, 
        InetSocketAddress remoteSocketAddress,
        ConnectionMode mode);
}
```
Let’s consider the method’s parameters:
*	<span class="param">context</span> is of type <span class="datatype">RequestContext</span>. It is the first parameter of any request handler of the platform. The type is described below;
*	<span class="param">datagramSocket</span> is an instance of java.net.DatagramSocket that represents a socket for sending and receiving datagram packets;
*	<span class="param">remoteSocketAddress</span> is an IP address and port of the remote UDP endpoint;
*	<span class="param">mode</span> provides the mode of the connection – P2P or Proxy.  
The <span class="datatype">RequestContext</span> class has the following fields:
```java
public class RequestContext {
    public final ServiceEndpoint serviceEndpoint;
    public final MembershipUser user;
    public final long clientId;	
    // the constructor is omitted
}
```
*	<span class="field">serviceEndpoint</span> – the endpoint that handled the connection request. You can use it to call <span class="method">udpAccept</span> again;
*	<span class="field">user</span> is a <span class="datatype">MembershipUser</span> object that contains the name, type, and permissions of the user with which the client is associated;
*	<span class="field">clientId</span> – ID of the client that made the request. It is used when, after an indefinite period of time, your application needs to send a Private event back to the client.  

Below is an example of using the UDP methods of the service endpoint. The <span class="method">udpListen</span> method binds to the virtual port 25, sets the backlog to 5, and creates an access rule that denies guest clients. The example implements the <span class="datatype">UDPAcceptHandler</span> interface and uses it as a second argument to the <span class="method">udpAccept</span> method. Inside the <span class="method">accept</span> method, the method <span class="method">udpAccept</span> is called again:
```java
public static void main(String[] args) {
    // the service endpoint creation code is omitted	
    serviceEndpoint.udpListen(25, 5, GuestAccess.GuestDenied);
    serviceEndpoint.udpAccept(25, new MyUDPAcceptHandler());	
    // the rest of the code is omitted
}
// custom implementation of the UDPAcceptHandler interface
class MyUDPAcceptHandler implements UDPAcceptHandler {
    public void accept(
        RequestContext context,
        DatagramSocket datagramSocket, 
        InetSocketAddress remoteSocketAddress,
        ConnectionMode mode) {
            context.serviceEndpoint.udpAccept(25, new MyUDPAcceptHandler());
            System.out.println(String.format(
                "A UDP connection is established for user '%s'.
                The connection mode is '%s'.",
                context.user.getName(),
                mode));
    }
}
```