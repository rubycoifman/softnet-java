---
layout: default
title: 16.1. Handling TCP connection requests
parent: 16. TCP and UDP in dynamic IP environments
nav_order: 1
---

## 16.1. Handling TCP connection requests

The service endpoint accepts TCP requests on a virtual port. It is not associated with a physical port in any way. First, the service app creates a binding to the port. There are three overloaded methods to create a binding. They differ in the way they define access rules:  
1. The first method does not apply access restrictions. It authorizes any request:
```java
void tcpListen(int virtualPort, TCPOptions tcpOptions, int backlog)
```
2. The second method applies access restrictions to the guest clients:
```java
void tcpListen(int virtualPort, TCPOptions tcpOptions, int backlog, GuestAccess guestAccess)
```
The first three parameters are the same as of the previous method. The fourth parameter is of enum type <span class="datatype">GuestAccess</span>. It specifies what kind of guest clients are denied. The enumeration contains two elements:
```java
public enum GuestAccess { GuestDenied, StatelessGuestDenied }
```
If you provide <span class="enum">GuestDenied</span>, all guest clients will be denied, but all clients associated with named membership users will be allowed. If you provide <span class="enum">StatelessGuestDenied</span>, stateless guest clients will be denied, but stateful guest clients and clients associated with named membership users will be allowed.
3. The third method creates an access rule based on the list of user roles specified in the fourth parameter:
```java
void tcpListen(int virtualPort, TCPOptions tcpOptions, int backlog, String roles)
```
Again, the first three parameters are the same as of the first method. The role names in the '<span class="param">roles</span>' parameter must be delimited with a semicolon. This overloaded method is only applicable if your application employs RBAC.  

More details on defining access rules declaratively you can find in chapter '[15. Access rules definition technique]({{ site.baseurl }}{% link docs/access-rules.md %}). Now let’s consider the first three parameters of <span class="method">tcpListen</span>:
*	<span class="param">virtualPort</span> is a virtual port on which the service accepts requests. It is not associated with a physical port which is not used at all. The possible values are not restricted;
*	<span class="param">tcpOptions</span> is an object of the following type:
```java
public class TCPOptions {
   public int receiveBufferSize = 0;
   public int sendBufferSize = 0;
}
```
The two fields of <span class="datatype">TCPOptions</span> represent the receive and send buffer sizes that will be assigned to the TCP connection at the service side;
*	<span class="param">backlog</span> specifies the maximum number of queued connections that are in the process of being established or have already been established but not yet accepted by the service application. The app accepts estableshed connections by the <span class="method">tcpAccept</span> method, which is described below. If the queue is full, clients get the service busy exception. The value of backlog should be chosen such that it is not too small so as not to reject client requests with the service busy exception due to the randomness of requests that could actually be processed. The backlog is designed to smooth out peaks in client requests. But on the other hand, it should not be large so that client requests that cannot be processed due to a heavy load were not queued, but were rejected with the service busy exception. For most applications, 5 will be a reasonable value.  

The next method we have to consider is <span class="method">tcpAccept</span>. It provides an accept handler to the listener that was earlier bound to the virtual port. If there is an established connection queued in the backlog, the handler is called back immediately. To accept the next connection from the backlog, <span class="method">tcpAccept</span> should be called again. If the backlog is empty, the handler remains idle. You can control the consumption of the host resources by calling <span class="method">tcpAccept</span> only when the resources are released for clients. The following is the signature of the method:
```java
public void tcpAccept(int virtualPort, TCPAcceptHandler acceptHandler)
```
*	<span class="param">virtualPort</span> is a virtual port to which the TCP listener has been bound earlier;
*	<span class="param">acceptHandler</span> is a handler that implements the <span class="datatype">TCPAcceptHandler</span> interface.  

```java
public interface TCPAcceptHandler {
	public void accept(RequestContext context, SocketChannel socketChannel, ConnectionMode mode);
}
```
In your handler, you have to implement the only accept method. Let’s consider its parameters:
*	<span class="param">context</span> is of type <span class="datatype">RequestContext</span>, which is the first parameter of any request handler of the platform. The type is described below;
*	<span class="param">socketChannel</span> is a Java NIO SocketChannel object that represents the established TCP connection;
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
*	<span class="field">serviceEndpoint</span> – the endpoint that handled the connection request. You can use it to call tcpAccept again;
*	<span class="field">user</span> is a <span class="datatype">MembershipUser</span> object that contains the name, type, and permissions of the user with which the client is associated;
*	<span class="field">clientId</span> – ID of the client that made the request. It is used when, after an indefinite period of time, your application needs to send a Private event back to the client. The details are given in section "[18.13. Raising Private events]({{ site.baseurl }}{% link docs/application-events/sections/raising-private-events.md %})".  

Below is an example of using the described two methods to handle TCP connection requests. The <span class="method">tcpListen</span> method binds to the virtual port 10, sets the backlog to 5, and creates an access rule with user roles "Editor" and "Operator". The example implements the <span class="datatype">TCPAcceptHandler</span> interface and uses it as a second argument to the <span class="method">tcpAccept</span> method. Inside the <span class="method">accept</span> method, the method <span class="method">tcpAccept</span> is called again:
```java
public static void main(String[] args) {
	// the service endpoint creation code is omitted	
	serviceEndpoint.tcpListen(10, null, 5, "Editor; Operator");
	serviceEndpoint.tcpAccept(10, new MyTCPAcceptHandler());	
	// the rest of your code
}

// custom implementation of the TCPAcceptHandler interface
class MyTCPAcceptHandler implements TCPAcceptHandler {
	public void accept(RequestContext context, SocketChannel socketChannel, ConnectionMode mode) {
		context.serviceEndpoint.tcpAccept(10, new MyTCPAcceptHandler());
		System.out.println(String.format(
			"The TCP connection is established with user rights '%s'.
 			The connection mode is '%s'.",
			context.user.getName(),
			mode));		
		// the rest of your code
	}
}
```