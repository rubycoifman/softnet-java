---
layout: default
title: 16.4. Making UDP connection requests
parent: 16. TCP and UDP in dynamic IP environments
nav_order: 4
---

## 16.4. Making UDP connection requests

This section describes the technique of making UDP connection requests. As with TCP, the client sends a request to the virtual port on which the remote service is listening. Before making a request, it is desirable to check whether the remote service is online by calling <span class="method">isOnline</span> on the <span class="datatype">RemoteService</span> object representing the service. This is not required if your application sends a request at receiving the service online event for the target service.  

Let's see the method for making connection requests:
```java
public void udpConnect(
    RemoteService remoteService,
    int virtualPort,
    TCPResponseHandler responseHandler, 
    Object attachment)
```
*	<span class="param">remoteService</span> is the first parameter of any request method of the client endpoint. Before making a request, your app can check the online status and version of the service against it. Note that a single-service endpoint has overloaded request methods without this parameter;
*	<span class="param">virtualPort</span> is a virtual port on which the remote service is listening;
*	<span class="param">responseHandler</span> is an implementation of the <span class="datatype">UDPResponseHandler</span> interface that client app provides to the method. It is discussed below;
*	<span class="param">attachment</span> is an optional parameter that contains any attached data you want to have in the respone handler.  

The following is the <span class="datatype">UDPResponseHandler</span> interface:
```java
public interface UDPResponseHandler {
    void onSuccess(
        ResponseContext context,
        DatagramSocket datagramSocket, 
        InetSocketAddress remoteSocketAddress,
        ConnectionMode mode);
    void onError(
        ResponseContext context,
        SoftnetException exception);
}
```
The method <span class="method">onSuccess</span> is called if the request is successfully processed and the connection is established. It has the following parameters:
*	<span class="param">context</span> is of type <span class="datatype">ResponseContext</span>, which is the first parameter of any response handler of the platform. The type is described below;
*	<span class="param">datagramSocket</span> is an instance of java.net.DatagramSocket that represents a socket for sending and receiving datagram packets;
*	<span class="param">remoteSocketAddress</span> is an IP address and port of the remote UDP endpoint;
*	<span class="param">mode</span> provides the mode of the connection – P2P or Proxy.  

The <span class="datatype">ResponseContext</span> class has the following fields:
```java
public class ResponseContext {
    public final ClientEndpoint clientEndpoint;
    public final RemoteService remoteService;
    public final Object attachment;	
    // the constructor is omitted
}
```
*	<span class="field">clientEndpoint</span> specifies the client endpoint that sent the connection request;
*	<span class="field">remoteService</span> represents a remote service to which the request has been made;
*	<span class="field">attachment</span> is state data provided to the <span class="method">udpConnect</span> call.  

The <span class="method">onError</span> method of <span class="datatype">UDPResponseHandler</span> is invoked if the connection request fails. The second parameter of type <span class="exception">SoftnetException</span> specifies an error. Possible exceptions are listed below:
*	<span class="exception">ServiceOfflineSoftnetException</span> – the remote service is offline;
*	<span class="exception">ConnectionAttemptFailedSoftnetException</span> – the connection attempt failed. The details are provided in the exception message;
*	<span class="exception">AccessDeniedSoftnetException</span> – the client does not have enough permissions to establish this connection;
*	<span class="exception">PortUnreachableSoftnetException</span> – the remote service is not listening on the virtual port specified in the connection request; 
*	<span class="exception">ServiceBusySoftnetException</span> – the server's backlog of pending connections is full;
*	<span class="exception">TimeoutExpiredSoftnetException</span> – the connection request timeout expired.  

Below is an example of using the <span class="method">udpConnect</span> method to make a UDP connection request. The client endpoint is assumed to be single-service. The <span class="datatype">RemoteService</span> object is retrieved by calling <span class="method">findService(0)</span> and used as the first argument to the <span class="method">udpConnect</span> call. Before calling it, the code checks whether the service is online. The second argument is the virtual port, specified as 25. The remote service is expected to be listening on this port as it was demonstrated in the previous section. The example implements the <span class="datatype">UDPRequestHandler</span> interface and uses it as a third argument to the method call:
```java
public static void main(String[] args) 
{
    // the client endpoint creation code is omitted
    if(softnetClient.isSingleService()) {
        RemoteService remoteService = softnetClient.findService(0);
        if(remoteService.isOnline()) {
            softnetClient.udpConnect(remoteService, 10, new MyUDPResponseHandler());
        }
    }
    // the rest of the code
}

// custom implementation of the UDPResponseHandler interface
class MyUDPResponseHandler implements UDPResponseHandler
{
    public void onSuccess(
        ResponseContext context, 
        DatagramSocket datagramSocket, 
        InetSocketAddress remoteSocketAddress,
        ConnectionMode mode) 
    {
        System.out.println(String.format(
            "The UDP connection has been established with
             the service hosted on '%s'. 
             The connection mode is '%s'.", context.remoteService.getHostname(),
             mode));
        // the rest of the code
    }
		
    public void onError(ResponseContext context, SoftnetException exception) {
        System.out.println(String.format(
            "The UDP connection attempt with the service
            hosted on '%s' has failed with an error '%s'.",
            context.remoteService.getHostname(),
            exception.getMessage()));	
    }
}
```