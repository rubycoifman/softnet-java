---
layout: default
title: 18.7. Raising Replacing events
parent: 18. Application Events
nav_order: 7
---

## 18.7. Raising Replacing events

Replacing events are so called because each new event of this kind received by the broker replaces the old one in the queue, if any. Thus, the queue of Replacing event can contain only one the most recent instance. Those clients that haven’t yet received the previous event will no longer be able to receive it, but only the last one. An event can remain in the queue for an arbitrarily long time until it is replaced by a new event. Those familiar with MQTT may notice that Replacing events are similar to MQTT retained messages.  

Replacing events are convenient for representing changes in the parameters of an electro-mechanical object over time. For example, such parameters can be the readings of temperature, humidity, pressure, etc. The service can generate a Replacing event whenever some reading reaches a certain value or changes by a certain delta.  

It is possible that a Replacing event is used to notify clients when a given state parameter takes values outside the range of nominal values. Then the question arises, if the parameter has returned to normal, how to notify clients about it. That is, how to notify clients about absence of the event. For this purpose, Softnet introduced a Replacing null-event which has the same name as the event and replaces it in the queue. Upon receiving a null-event, subscribed clients know that the event itself is no longer relevant, i.e., the value of the state parameter is now in the nominal range, no matter what that value is.  

Let’s remember that a Replacing event must first be defined in the site structure. See [section 7.4]({{ site.baseurl }}{% link docs/site/sections/application-events.md %}) for details. The <span class="datatype">SiteStructure</span> implementation has the following method for this:
```java
void addReplacingEvent(String eventName)
```
This method defines an event with no access restrictions. There are two more overloads to apply an access rule.  

After the event is defined, it can be raised by the following method of the <span class="datatype">ServiceEndpoint</span> class:
```java
public void raiseEvent(ReplacingEvent event)
```

The parameter is of type <span class="datatype">ReplacingEvent</span> with the following members:
```java
public class ReplacingEvent {
    public final UUID uid;
    public final String name;
    public final SequenceEncoder arguments;		
    public final boolean isNull;
	
    public ReplacingEvent(String name)
    public byte[] getEncoding()
}
```

If your app needs to raise a Replacing null-event, it instantiates the <span class="datatype">ReplacingNullEvent</span> class derived from <span class="datatype">ReplacingEvent</span>:
```java
public class ReplacingNullEvent extends ReplacingEvent {
	public ReplacingNullEvent(String name)
}
```

The next is a description of the <span class="datatype">ReplacingEvent</span> members:
*	<span class="field">uid</span> is the event ID used internally by the platform. However, if you want to provide it to subscribed clients, you can add it to the arguments field of the class;
*	<span class="field">name</span> is the name of the event, provided to the constructor when the class instance is created, and also specified in the event definition in the site structure;
*	<span class="field">arguments</span> is a field of type <span class="datatype">SequenceEncoder</span> provided by Softnet ASN.1 Codec. The service can pass data organized in complex structures through this field to subscribers. Accordingly, on the client side, events have an arguments field of type <span class="datatype">SequenceDecoder</span>. The data size in arguments is limited to 2 kilobytes. Before raising an event, an application can check the size of data by calling the getSize method of <span class="datatype">SequenceEncoder</span>;
*	<span class="field">isNull</span> indicates if the event is null-event. It is always false for <span class="datatype">ReplacingEvent</span> instances and true for <span class="datatype">ReplacingNullEvent</span> instances;
*	<span class="method">ReplacingEvent</span> is a constructor that takes the name of the event;
*	<span class="method">getEncoding</span> is a method that returns the ASN.1 DER encoding of data provided to arguments.  

To create a null-event, your application calls the <span class="datatype">ReplacingNullEvent</span> constructor with the name of the Replacing event as an argument. The arguments field of this object is null, that is, the null-event cannot have arguments.  

This section concludes with an example of defining and raising a replacing event.
1.	The following code defines a "WaterTemperature" Replacing event using the third overload of the <span class="method">addReplacingEvent</span> method. The access rule specifies the user roles "Administrator" and "Operator" as authorized to subscribe to the event:
```java
siteStructure.addReplacingEvent("WaterTemperature", "Administrator; Operator");
```

2.	The next code instantiates the <span class="datatype">ReplacingEvent</span> class and populates the arguments field with a temperature value of 70 degrees Celsius, and raises the event:
```java
ReplacingEvent event = new ReplacingEvent("WaterTemperature");
event.arguments.Int32(70);
serviceEndpoint.raiseEvent(event);
```