---
layout: default
title: 12.2.1. Connectivity status event handler
parent: 12.2. Implementing event handlers
grand_parent: 12. Platform events related to services
nav_order: 1
---

## 12.2.1. Connectivity status event handler 

The handler signature:
```java
void onConnectivityChanged(ServiceEndpointEvent e)
```

To get an updated connectivity status do the following. In the handler’s body call the <span class="method">getEndpoint</span> method on the e parameter. It returns the endpoint of type <span class="datatype">ServiceEndpoint</span> which invoked the handler. Then call the endpoint’s <span class="method">getConnectivity</span> method. It has the following signature:
```java
public EndpointConnectivity getConnectivity()
```

The method returns an object of type <span class="datatype">EndpointConnectivity</span> with the following members:
```java
public class EndpointConnectivity
{
    public final ConnectivityStatus status;
    public final SoftnetError error;
    public final String message;
}
```
- <span class="field">status</span> of enumeration type <span class="datatype">ConnectivityStatus</span>. It is the current connectivity status of the service endpoint. Possible values and their meanings are described below;
- <span class="field">error</span> of enumeration type <span class="datatype">SoftnetError</span>. The possible connectivity errors are given below. If there is no error, its value is NoError;
- <span class="field">message</span> contains the error message if error differs from NoError.  

The described steps may look like the following:
```java
public void onConnectivityChanged(ServiceEndpointEvent e)
{
    ServiceEndpoint serviceEndpoint = e.getEndpoint();
    EndpointConnectivity connectivity = serviceEndpoint.getConnectivity();

    System.out.println(
        String.format("Connectivity Status: %s, Error: %s, Message: %s",
        connectivity.status,
        connectivity.error,
        connectivity.message));
}
```

The next is the <span class="datatype">ConnectivityStatus</span> enumeration:
```java
public enum ConnectivityStatus
{
    Disconnected,
    AttemptToConnect,
    Connected,
    Down,
    Closed
}
```

When the service endpoint is just instantiated, the connectivity status is Disconnected. The endpoint is inactive. Calling the <span class="method">disconnect</span> method also sets the status to Disconnected. Calling the <span class="method">close</span> method on the endpoint sets the status to Closed, making the endpoint unusable. The remaining possible status values reflect the current stage of the control channel establishment procedure. Calling the <span class="method">connect</span> method brings the endpoint to the active state. Once in this state, the endpoint starts a procedure of establishing the control channel. Also, this procedure starts when the existing channel has been detected as broken and has to be re-established. Let’s consider how this procedure works.  

**Stage 1**. The procedure starts from setting the connectivity parameters to the following values and raising a connectivity event:  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">status</span>: <span class="datatype">ConnectivityStatus</span>.AttemptToConnect;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">error</span>: <span class="datatype">SoftnetError</span>.NoError;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">message</span>: null.  

Then the endpoint makes a request to the Softnet Balancer to locate a Softnet Tracker running the service’s site. Depending on the result, there are three possible scenarios:
1.	The Balancer returns an IP-address of the Tracker. What happens next is described below in stage 2;
2.	The Balancer does not find the service account for a service URI provided to the method <span class="method">ServiceEndpoint.create</span>. The endpoint completes operating with a fatal error. The connectivity parameters take the following values:  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">status</span>: <span class="datatype">ConnectivityStatus</span>.Down;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">error</span>: <span class="datatype">SoftnetError</span>.ServiceNotRegistered;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">message</span>: &lt;error description&gt;.  
To notify the application of this status, the endpoint raises a connectivity event again;
3.	The endpoint fails to make the request due to a network error. The connectivity parameters take the following values:  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">status</span>: <span class="datatype">ConnectivityStatus</span>.AttemptToConnect;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">error</span>: <span class="datatype">SoftnetError</span>.NetworkError;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">message</span>: &lt;error description&gt;.  
The endpoint raises a connectivity event so that the application could get a new status. After a certain period of time, the endpoint repeats the procedure. As for waiting time between two consecutive attempts, it starts from one second and doubles at each unlucky attempt until it reaches one minute. Then each subsequent attempt is made with a one-minute wait. This process continues until the Softnet Balancer returns some result described in the first two cases.  

**Stage 2**. Here, we consider what happens when the Softnet Balancer returns an IP-address of the Tracker. This is the first case described in stage 1. The endpoint makes attempt to establish a connection with the Softnet Tracker and perform the service authentication. Here, we have three possible scenarios:
1.	The endpoint establishes a connection with the Tracker and performs authentication. The connectivity parameters take the following values:  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">status</span>: <span class="datatype">ConnectivityStatus</span>.Connected;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">error</span>: <span class="datatype">SoftnetError</span>.NoError;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">message</span>: null.  
The endpoint raises a connectivity event. As the connectivity status is Connected, the endpoint starts establishing the service status. This process is described in the next section;

2.	The endpoint establishes a connection with the Tracker but fails to perform authentication. The endpoint completes operating with a fatal error. The connectivity parameters take the following values:  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">status</span>: <span class="datatype">ConnectivityStatus</span>.Down;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">error</span>: <span class="datatype">SoftnetError</span>.PasswordNotMatched;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">message</span>: &lt;error description&gt;.  
Then the endpoint raises a final connectivity event;

3.	The endpoint fails to establish a connection with the Tracker due to a network error. The connectivity parameters take the following values:  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">status</span>: <span class="datatype">ConnectivityStatus</span>.AttemptToConnect;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">error</span>: <span class="datatype">SoftnetError</span>.NetworkError;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="field">message</span>: &lt;error description&gt;.  

Then the endpoint raises a connectivity event. After a certain period of time, the endpoint repeats the procedure as it was described earlier for the case 3 of the procedure’s stage 1.  

The procedure works until the connectivity status is Connected or a fatal error occurs. After the channel has been successfully created, the endpoint starts establishing the service status. The service becomes available for clients only if it gets the Online status. However, the idea behind Softnet is such that regardless of the service status, the endpoint must still be connected to the Tracker. Thanks to this feature, the service restarts automatically with a new status when the administrator changes its status through the management panel.  

The endpoint can be temporarily deactivated, i.e., disconnected from the site, by calling the <span class="method">disconnect</span> method:
```java
public void disconnect()
```

Later, the endpoint can be activated again by calling the <span class="method">connect</span> method. When the application terminates, it must call the endpoint's <span class="method">close</span> method, which releases all resources associated with it:
```java
public void close()
```
