---
layout: default
title: 18.12. Handling Private events
parent: 18. Application Events
nav_order: 12
---

## 18.12. Handling Private events

Handling Private events is almost identical to handling Replacing or Queueing events. In this description, most things are repeated, and the differences are noted.  

The client application subscribes to a Private event by calling the following method of the <span class="datatype">ClientEndpoint</span> class:
```java
public void subscribeToPEvent(String eventName, RemoteEventListener listener)
```

The difference from the subscription method of Replacing or Queueing events is only in the name of the method. It has the same two parameters:
*	<span class="param">eventName</span> is the event name described in the previous section;
*	<span class="param">listener</span> is an event listener of type <span class="datatype">RemoteEventListener</span>.  

The application provides the event name and implementation of the <span class="datatype">RemoteEventListener</span> interface to the method call. The interface declares two methods to implement:
```java
public interface RemoteEventListener {
    void accept(ClientEndpoint clientEndpoint, RemoteEvent remoteEvent);
    void acceptError(ClientEndpoint clientEndpoint, SoftnetException exception);
}
```

The first method, <span class="method">accept</span>, is an event handler. It is called by the endpoint when it receives an event. As with Replacing events, the endpoint receives the next event only after the current call to the handler has completed. The method has two parameters:
*	<span class="param">clientEndpoint</span> is the endpoint that calls the handler;
*	<span class="param">remoteEvent</span> of type <span class="datatype">RemoteEvent</span> is the received event.  

The second method, <span class="method">acceptError</span>, is called in case of an error. Currently, the only possible error can be caused by subscribing to a non-existent event.  

The next is a description of the <span class="datatype">RemoteEvent</span> fields. In contrast to another two categories of events, here the category field is set to Private and the <span class="field">isNull</span> field is not used:
```java
public class RemoteEvent {
    public final long instanceId;
    public final String name;
    public final EventCategory category;
    public final boolean isNull;
    public final long serviceId;
    public final long age;
    public final GregorianCalendar createdDate;
    public final SequenceDecoder arguments;
}
```
*	<span class="field">instanceId</span> is the event ID used internally by the platform;
*	<span class="field">name</span> is the event name specified in the event subscription;
*	<span class="field">category</span> is of type <span class="datatype">EventCategory</span> enumeration. For Private events, it is set to Private;
*	<span class="field">isNull</span> is not used;
*	<span class="field">serviceId</span> is an ID of the remote service raised the event. To get the appropriate object of type <span class="datatype">RemoteService</span>, call the client endpoint's <span class="method">findService</span> method that expects the service ID as an argument;
*	<span class="field">age</span> specifies the time in seconds elapsed since the event has been received by the broker. This value is zero if the event is sent to the client without delay as soon as it is received by the broker;
*	<span class="field">createdDate</span> specifies the date and time when the event has been received by the broker;
*	<span class="field">arguments</span> is of type <span class="datatype">SequenceDecoder</span> provided by Softnet ASN.1 Codec. It contains data attached to the event by the service.  

We conclude this section with an example that demonstrates how to both subscribe to and handle the Private event raised in the example of the previous section.  

The code below creates the client endpoint using the arguments provided in the <span class="param">args</span> parameter of the main method. Then it creates a subscription to the "WaterTemperatureSet" event and provides the event handler to the <span class="method">subscribeToPEvent</span> method. The handler's <span class="method">accept</span> method has a <span class="param">remoteEvent</span> parameter that represents the event itself. It has an <span class="field">arguments</span> field that provides the current temperature passed in by the service. This is the actual temperature after the target temperature setting process is completed. After creating the subscription and defining the event handler, the example code calls the "SetWaterTemperature" RPC method, which starts the process of setting the target temperature on the remote service. In the RPC response handler, the client immediately receives the current temperature at the time the process is started.
```java
public static void main(String[] args) {
    ClientSEndpoint clientSEndpoint = null;
    try {
        String client_uri = args[0];
        String password = args[1];
			
        ClientURI clientURI = new ClientURI(client_uri);
        clientSEndpoint = ClientSEndpoint.create("Water Boiler", "Softnet Team", clientURI, password);
						
        clientSEndpoint.setPersistenceL2();
			
        clientSEndpoint.subscribeToPEvent("WaterTemperatureSet", new RemoteEventListener() {					
            public void accept(ClientEndpoint clientEndpoint, RemoteEvent remoteEvent) {
                try {
                    System.out.println(String.format(
                        "The event '%s' is received.",
                        remoteEvent.name));
                    System.out.println(String.format(
                        "The temperature is set to '%d' degrees Celsius.",
                        remoteEvent.arguments.Int32()));
                }
                catch(Exception ex) {
                    System.out.println(String.format(
                        "Event data format error: %s", 
                        ex.getMessage()));
                }
            }
				
            public void acceptError(ClientEndpoint clientEndpoint, SoftnetException exception) {
                System.out.println(String.format(
                    "Subscription error: %s",
                    exception.getMessage()));					
            }
        });
			
        clientSEndpoint.addEventListener(new ClientEventAdapter() {
            @Override
            public void onStatusChanged(ClientEndpointEvent e) {
                ClientSEndpoint clientSEndpoint = (ClientSEndpoint)e.getEndpoint();					
                if(clientSEndpoint.isOnline() && clientSEndpoint.isServiceOnline())
                    makeRpcRequest(clientSEndpoint);
            }
				
            @Override
            public void onServiceOnline(RemoteServiceEvent e) {
                ClientSEndpoint clientSEndpoint = (ClientSEndpoint)e.getEndpoint();
                makeRpcRequest(clientSEndpoint);
            }
				
            void makeRpcRequest(ClientSEndpoint clientSEndpoint) {
                RemoteProcedure remoteProcedure = new RemoteProcedure("SetWaterTemperature");  
                clientSEndpoint.call(remoteProcedure, new RPCResponseHandler() {
                    public void onSuccess(ResponseContext context, SequenceDecoder result) {
                        try {
                            System.out.println("Establishing the target temperature is started.");
                            System.out.println("The service will notify you when the process is completed.");
                            System.out.println(String.format(
                                "The current temperature is '%d' degrees Celsius.",
                                result.Int32()));
                        }
                        catch(Exception ex) {
                            System.out.println(String.format(
                                "Event data format error: %s", 
                                ex.getMessage()));
                        }
                    }
						
                    public void onError(ResponseContext context, int errorCode, SequenceDecoder error) { }
                    public void onError(ResponseContext context, SoftnetException exception) { }
                });
            }
        });

        clientSEndpoint.connect();			
        System.in.read();			
    }		
    catch(java.io.IOException e) {
        e.printStackTrace();
    }
    finally {
	    if(clientSEndpoint != null) {
            clientSEndpoint.close();
            System.out.println("Client closed!");			    
        }
    }
}
```