---
layout: default
title: 18.9. Raising Queueing events
parent: 18. Application Events
nav_order: 9
---

## 18.9. Raising Queueing events

According to the name, each new Queueing event joins the queue of previously received instances. This continues until the queue is full. Then each new event pushes the oldest one out of the queue. The event is also removed from the queue at the end of the lifetime. Event parameters are defined in the site structure. These are the event name, lifetime, maximum queue size, and access rule. See [section 7.4]({{ site.baseurl }}{% link docs/site/sections/application-events.md %}) for details. Queuing events are used when clients need all raised events.  

There are no clear similarities between Softnet queuing events and MQTT topics. This is because MQTT topics cannot contain more than one message at a time. However, if a subscriber needs to receive all messages published on the MQTT topic while it is offline, the subscriber can create a stateful subscription.  

Let’s recall how to define a Queueing event. To do this, the <span class="datatype">SiteStructure</span> implementation has three overloaded methods that differ in the way access rules are defined. See [chapter 15]({{ site.baseurl }}{% link docs/access-rules.md %}) for details. The following method defines a Queueing event without access restrictions:
```java
void addQueueingEvent(String eventName, int lifeTime, int queueSize);
```

The first parameter takes the event name. The second parameter takes the event’s lifetime in seconds. And, the third parameter takes the queue size.  

After the event is defined, it can be raised by the following method of the ServiceEndpoint class: 
```java
public void raiseEvent(QueueingEvent event)
```

The parameter is of type <span class="datatype">QueueingEvent</span> with the following members:
```java
public class QueueingEvent {
    public final UUID uid;
    public final String name;
    public final SequenceEncoder arguments;		
	
    public byte[] getEncoding()
    public QueueingEvent(String name)
}
```
Here is a description of the <span class="datatype">QueueingEvent</span> members:
*	<span class="field">uid</span> is the event ID used internally by the platform. However, if you want to provide it to subscribed clients, you can add it to the <span class="field">arguments</span> field of the class;
*	<span class="field">name</span> is the name of the event, provided to the constructor when the class instance is created, and also specified in the event definition in the site structure;
*	<span class="field">arguments</span> is a field of type <span class="datatype">SequenceEncoder</span> provided by Softnet ASN.1 Codec. Accordingly, on the client side, events have an arguments field of type <span class="datatype">SequenceDecoder</span>. The data size in arguments is limited to 2 kilobytes;
*	<span class="method">getEncoding</span> is a method that returns an ASN.1 DER encoding of data provided to arguments.
*	<span class="method">QueueingEvent</span> is a constructor that takes the name of the event;  

This section concludes with an example of defining and raising a Queueing event.
1.	The following code defines "CriticalWaterTemperature" as a Queueing event using the second overload of the <span class="method">addQueueingEvent</span> method. It specifies the lifetime as 10 hours, the maximum queue size as 50, and the access rule denies guest clients:
```java
siteStructure.addQueueingEvent(
        "CriticalWaterTemperature", // event name
        TimeSpan.fromHours(10),     // lifetime in seconds
        50,                         // maximum queue size
        GuestAccess.GuestDenied);   // guest is denied
```
2.	The next code instantiates the <span class="datatype">QueueingEvent</span> class and populates the arguments field with a temperature value of 95 degrees Celsius, and raises the event:
```java
QueueingEvent event = new QueueingEvent("CriticalWaterTemperature");
event.arguments.Int32(95);
serviceEndpoint.raiseEvent(event);
```