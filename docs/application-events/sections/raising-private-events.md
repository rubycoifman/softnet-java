---
layout: default
title: 18.11. Raising Private events
parent: 18. Application Events
nav_order: 11
---

## 18.11. Raising Private events

At times, a service may not be able to immediately respond to a client request and may take an indefinite period of time to do so. Since the client request cannot last longer than a short timeout interval, the service can complete the request-response session by informing the client that it will be notified when the result is ready. And when the result is ready, the service can then send a Private event to the client, either notifying it that the result is ready or that the interaction can continue.  

Comparing Softnet Private events with MQTT topics, you can hardly find similarities because MQTT follows a loose coupling model and does not support messages addressed to specific subscribers.  

The queue of Private events on the broker works similar to the queue of Queueing events. Each new event joins the queue of previously received instances. This continues until the queue is full. Then each new event pushes the oldest one out of the queue. The difference is that the maximum queue size is fixed at 1000. The event is also removed from the queue at the end of the lifetime. Event parameters are defined in the site structure. There are only two of them - the name of the event and the lifetime. Access rules are not used since each event is addressed to a specific client specified by the service. See [section 7.4]({{ site.baseurl }}{% link docs/site/sections/application-events.md %}) for details. Any client, except stateless, can subscribe to a Private event, but each client receives only those events that were addressed to it.  

To raise a Private event, the service needs a client ID. The service takes it from the client's request. This can be an RPC request, a TCP connection request, or a UDP connection request. 
First, a Private event must be defined in the site structure. The <span class="datatype">SiteStructure</span> implementation has the following method for this:
```java
void addPrivateEvent(String eventName, int lifeTime); 
```

The first parameter takes the event name. The second parameter takes the event lifetime in seconds.
After the event is defined, it can be raised by the following method of the <span class="datatype">ServiceEndpoint</span> class: 
```java
public void raiseEvent(PrivateEvent event)
```

The parameter is of type <span class="datatype">PrivateEvent</span> with the following members:
```java
public class PrivateEvent {
    public final UUID uid;
    public final String name;
    public final long clientId;
    public final SequenceEncoder arguments;		
	
    public byte[] getEncoding()
    public PrivateEvent(String name, long clientId)
}
```
Here is a description of the <span class="datatype">PrivateEvent</span> members:
*	<span class="field">uid</span> is the event ID used internally by the platform;
*	<span class="field">name</span> is the name of the event, provided to the constructor when the class instance is created, and also specified in the event definition in the site structure;
*	<span class="field">clientId</span> is the client ID taken from the client request earlier;
*	<span class="field">arguments</span> is a field of type <span class="datatype">SequenceEncoder</span> provided by Softnet ASN.1 Codec. The data size in arguments is limited to 2 kilobytes;
*	<span class="method">getEncoding</span> is a method that returns an ASN.1 DER encoding of data provided to arguments.
*	<span class="method">PrivateEvent</span> is a constructor that takes the event name and the client ID;  

Finally, we'll look at an example of defining and raising a Private event. Let's say we have a Softnet service "WaterTempController" that controls the temperature of the water in the boiler. We also have a Java class with the same name that implements the service. The service allows you to remotely set the water temperature. To do this, it implements an RPC procedure that accepts a target temperature. Since the temperature cannot be set immediately, the procedure starts the requested process, writes the current temperature to the result parameter and completes. When the target temperature is set, the controller raises a Private event to notify the client.  

1)	The first part of the example demonstrates simplified implementation of the Java class <span class="datatype">WaterTempController</span>. The method <span class="method">setTemperature</span> starts the process of setting the target temperature. When it is set, the method calls another method <span class="method">raisePrivateEvent</span> with the current temperature of water and the client ID as arguments. This method, in turn, raises the Private event "WaterTemperatureSet" with the current temperature written to the <span class="field">arguments</span> field:
```java
import softnet.service.*;

public class WaterTempController {		
    private ServiceEndpoint serviceEndpoint;
    private int currentTemperature;

    public WaterTempController(ServiceEndpoint serviceEndpoint) {
        this.serviceEndpoint = serviceEndpoint; 
    }
	
    public int getCurrentTemperature(){
        return currentTemperature;
    }

    public void setTemperature(int targetValue, long clientId) {
        // The method starts the process 
        // of setting the target temperature.
        // When the temperature is set, the process
        // calls the method raisePrivateEvent.
    }
	
    private void raisePrivateEvent(int currentTemp, long clientId) {
        PrivateEvent event = new PrivateEvent("WaterTemperatureSet", clientId); 
        event.arguments.Int32(currentTemp);
        serviceEndpoint.raiseEvent(event);
    }
	
    public static void main(String[] args) {
        // The method sets up the Softnet service.
        // Its implementation is given below 
        // in the second part of the example.
    }
}
```
2)	The second part of the example demonstrates the code of the main method that sets up the Softnet service. It creates the site structure and defines the Private event "WaterTemperatureSet". Then it creates the service endpoint and registers an RPC procedure named as "SetWaterTemperature". The procedure takes the target temperature provided in the <span class="param">arguments</span> parameter. Also, it takes the client ID from the <span class="param">context</span> parameter of type <span class="datatype">RequestContext</span>:
```java
public static void main(String[] args) {
    ServiceEndpoint serviceEndpoint = null;
    try {
        SiteStructure siteStructure = ServiceEndpoint.createStructure("Water Boiler", "Softnet Team");
        siteStructure.addPrivateEvent("WaterTemperatureSet", TimeSpan.fromMinutes(30));

        String service_uri = args[0]; 
        String password = args[1]; 
        ServiceURI serviceURI = new ServiceURI(service_uri); 
			
        serviceEndpoint = ServiceEndpoint.create(
            siteStructure, 
            null, 
            serviceURI, 
            password);
        serviceEndpoint.setPersistenceL2();
			
        final WaterTempController waterTempController = new WaterTempController(serviceEndpoint);
			
        serviceEndpoint.registerProcedure("SetWaterTemperature", new RPCRequestHandler() {				
            public int execute(
                RequestContext context,
                SequenceDecoder parameters,
                SequenceEncoder result,
                SequenceEncoder error) {
                try {								
                    result.Int32(waterTempController.getCurrentTemperature());
                    int targetTemp = parameters.Int32();
                    waterTempController.setTemperature(targetTemp, context.clientId);

                    return 0;
                }
                catch(AsnException e) {
                    error.UTF8String(e.getMessage());
                    return -1;						
                }
            }
        }, 1 /* Concurrency Limit */);
			
        serviceEndpoint.connect();			
        System.in.read();			
    }
    catch (NotSupportedSoftnetException e) {			
        e.printStackTrace();
    }		
    catch(java.io.IOException e) {
        e.printStackTrace();
    }
    finally {
        if(serviceEndpoint != null) {
            serviceEndpoint.close();
            System.out.println("Service closed!");			
        }
    }
}
```