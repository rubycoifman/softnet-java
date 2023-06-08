---
layout: default
title: 11. Service Group
nav_order: 11
---

## 11. Service Group

This chapter describes the functionality called Service Status Detection, which is one of the basic features offered by Softnet.  

Clients, in order to interact with services registered on the site, must know the service API, which is indicated by the two main properties of the site – Service Type and Contract Author. These properties are static and never change until the owner does it manually. But there is a set of additional service properties that can change over time and clients need to know. These are service version, hostname, service ID, and online/offline status. The service version may change with software updates. The hostname can be changed by the owner. The service ID never changes for a given service instance, but the list of services on a multi-service site can change. Finally, the online/offline status of the service can change at any time. Clients, in order to make a request to the service, must know some of these features in advance. The platform's ability to provide clients with access to the service status is known as Service Status Detection. Softnet uses a dynamic structure called Service Group to implement this functionality. Hosted on the site, this structure contains a record for each service with properties necessary for clients, including the online/offline status. If the site is single-service, the Service Group is trivial and always contains one record for the only service. It is called a single-service group. And if the site is multi-service, then the Service Group is multi-service, accordingly.  

As with User Membership, when the client application hosted on the device connects to the site, Softnet loads the Service Group into the application and keeps it synchronized with the site. Any change to the service properties made on the site is immediately propagated to the application’s copy. The Service Group also keeps track of the online/offline status of the services.  

The application’s copy of the Service Group contains <span class="datatype">RemoteService</span> objects – one for each remote service. <span class="datatype">RemoteService</span> is an interface with the following members:
```java
public interface RemoteService {
	long getId();
	String getHostname();
	String getVersion();
	boolean isOnline();
	boolean isRemoved();
}
```
The first method, <span class="method">getId</span>, returns the service ID of the service. It is mostly used by Softnet internally. If the service group is single-service, this method always returns 0. The <span class="method">getHostname</span> method returns the service’s hostname. The <span class="method">getVersion</span> method returns the version of the service. It has already been discussed in chapter "[9. Client Endpoint]({{ site.baseurl }}{% link docs/client-endpoint.md %})". <span class="method">isOnline</span> returns the online/offline status. Use it to check the online status of the service before making a request to it.  

The <span class="datatype">RemoteService</span> object is used in remote service request methods as the destination. For example, the method for making an RPC call has the following parameters:
```java
public void call(
    RemoteService remoteService, 
    RemoteProcedure remoteProcedure, 
    RPCResponseHandler responseHandler, 
    Object attachment)
```
And the following is a method for establishing a TCP connection:
```java
public void tcpConnect(
    RemoteService remoteService,
    int virtualPort,
    TCPOptions tcpOptions,
    TCPResponseHandler responseHandler,
    Object attachment)
```
Both methods have a <span class="datatype">RemoteService</span> object as the first parameter. Note that at the moment of making the request, the service is supposed to be online. The method <span class="datatype">RemoteService</span>.<span class="method">isOnline</span> allows the app to check this status in advance. The checking is not required, if a client makes a request on receiving a <span class="datatype">ServiceOnline</span> event from the platform. [Section 13.1]({{ site.baseurl }}{% link docs/client-platform-events/sections/interface-client-event-listener.md %}) gives more details on this question.  

A single-service group contains only one object of the type <span class="datatype">RemoteService</span>, while a multi-service group contains multiple objects – one for each remote service. This is where <span class="datatype">ClientEndpoint</span> and <span class="datatype">ClientSEndpoint</span> differ from each other. Let’s see those differences. The following is a view of the <span class="datatype">ClientEndpoint</span> class that shows signatures of the methods for accessing elements of a multi-service group:
```java
public class ClientEndpoint {
    public RemoteService findService(long serviceId)
    public RemoteService findService(String hostname)
    public RemoteService[] getServices()
    // the rest of the members are omitted
}
```
The class provides methods to find a <span class="datatype">RemoteService</span> object by ID or by hostname. And the <span class="method">getServices</span> method returns the entire list of objects.  

The <span class="datatype">ClientSEndpoint</span> class is derived from <span class="datatype">ClientEndpoint</span>. It has two more methods to make manipulations with the only <span class="datatype">RemoteService</span> object easier. Below is the class with the method signatures:
```java
public class ClientSEndpoint extends ClientEndpoint {
    public boolean isServiceOnline()
    public RemoteService getService()
    // the rest of the members are omitted
}
```
Since this is a single-service endpoint class, the <span class="method">getService</span> method returns the only <span class="datatype">RemoteService</span> object. If you only need to check its online status, you can call the <span class="method">isServiceOnline</span> method. As for three methods of the parent class, the way they work with a single-service group is trivial. The <span class="method">findService</span> method that finds an object by the service ID returns the only <span class="datatype">RemoteService</span> object if the argument is 0, otherwise it returns null; the <span class="method">findService</span> overload that finds an object by the hostname returns the only <span class="datatype">RemoteService</span> object if the argument’s value is equal to the service’s hostname; the method <span class="method">getServices</span> always returns an array of a single item, that is the only <span class="datatype">RemoteService</span> object.  

The <span class="datatype">ClientSEndpoint</span> class also overloads all parent methods for making requests to the remote services. The overloaded methods exclude the first parameter of the <span class="datatype">RemoteService</span> type. For example, the overloaded <span class="method">call</span> method has the following view:
```java
public void call(
    RemoteProcedure remoteProcedure,
    RPCResponseHandler responseHandler,
    Object attachment)
```

And the <span class="method">tcpConnect</span> method also lost the first parameter:
```java
public void tcpConnect(
    int virtualPort,
    TCPOptions tcpOptions,
    TCPResponseHandler responseHandler,
    Object attachment)
```

In your code, if you have a reference of type <span class="datatype">ClientEndpoint</span> and you want to cast it to <span class="datatype">ClientSEndpoint</span> in order to use its overloaded methods, check the return value of the <span class="method">isSingleService</span> method and if it is true, make the type casting:
```java
if(clientEndpoint.isSingleService()) {
    ClientSEndpoint clientSEndpoint = (ClientSEndpoint)clientEndpoint;
    clientSEndpoint.call(remoteProcedure, responseHandler);
    // the rest of your code
}
```

If you do not want to make the type casting, there is a simple way to use a reference of type <span class="datatype">ClientEndpoint</span> to work with a single-service endpoint. As in the previous example, you can check whether the reference points to a single-service endpoint by calling <span class="method">isSingleService</span> and if it is true, you can get the only <span class="datatype">RemoteService</span> object by calling the <span class="method">findService</span> method with 0 as an argument. Then you can use the <span class="datatype">RemoteService</span> object as the first argument in the request methods. For example:
```java
if(clientEndpoint.isSingleService()) {
    RemoteService remoteService = clientEndpoint.findService(0);
    clientEndpoint.call(remoteService, remoteProcedure, responseHandler);
    // the rest of your code
}
```

