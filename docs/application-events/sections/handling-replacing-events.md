---
layout: default
title: 18.8. Handling Replacing events
parent: 18. Application Events
nav_order: 8
---

## 18.8. Handling Replacing events

The client application subscribes to a Replacing event by calling the following method of the <span class="datatype">ClientEndpoint</span> class:
```java
public void subscribeToREvent(String eventName, RemoteEventListener listener)
```

The method has two parameters:
*	<span class="param">eventName</span> is the event name described in the previous section;
*	<span class="param">listener</span> is an event listener of type <span class="datatype">RemoteEventListener</span>.  

The application provides the event name and implementation of the <span class="datatype">RemoteEventListener</span> interface to the method call. The interface declares two methods to implement:
```java
public interface RemoteEventListener {
    void accept(ClientEndpoint clientEndpoint, RemoteEvent remoteEvent);
    void acceptError(ClientEndpoint clientEndpoint, SoftnetException exception);
}
```
The first method, <span class="method">accept</span>, is an event handler. It is called by the endpoint when it receives an event. Note that the endpoint receives the next event only after the current call to the handler has completed. The method has two parameters:
*	<span class="param">clientEndpoint</span> is an endpoint that calls the handler;
*	<span class="param">remoteEvent</span> of type <span class="datatype">RemoteEvent</span> is the event itself. The type is described below.  

The second method <span class="method">acceptError</span> is called in case of an error. Currently, the only possible error can be caused by subscribing to a non-existent event.  

Let’s consider the <span class="datatype">RemoteEvent</span> type. Note that it is used to represent events from all three categories. It has the following read-only fields:
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
*	<span class="field">category</span> is of type <span class="datatype">EventCategory</span> enumeration. For Replacing events, it is set to Replacing;
*	<span class="field">isNull</span> is true for Replacing null-events, false otherwise;
*	<span class="field">serviceId</span> is an ID of the remote service raised the event. To get the appropriate object of type <span class="datatype">RemoteService</span>, call the client endpoint's <span class="method">findService</span> method that expects the service ID as an argument;
*	<span class="field">age</span> specifies the time in seconds elapsed since the event has been received by the broker. This value is zero if the event is sent to the client without delay as soon as it is received by the broker;
*	<span class="field">createdDate</span> specifies the date and time when the event has been received by the broker;
*	<span class="field">arguments</span> is of type <span class="datatype">SequenceDecoder</span> provided by Softnet ASN.1 Codec. It contains data attached to the event by the service.  

This section concludes with an example of subscribing to the Replacing event defined in the example of the previous section:
```java
softnetClient.subscribeToREvent("WaterTemperature", new RemoteEventListener() {			
    public void accept(ClientEndpoint clientEndpoint, RemoteEvent remoteEvent) {
        try {
            if(!remoteEvent.isNull) {
                System.out.println(String.format(
                    "The event '%s' is received.",
                    remoteEvent.name));					
                System.out.println(String.format(
                    "Water temperature changed to '%d' degrees Celsius.", 
                    remoteEvent.arguments.Int32()));
            }
            else {
                System.out.println(String.format(
                    "The null-event '%s' is received.", 
                    remoteEvent.name));
                System.out.println("Water temperature is normal.");
            }
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
            exception.getMessage)));					
    }
});
```
