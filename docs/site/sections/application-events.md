---
layout: default
title: 7.4. Defining application events
parent: 7. Site and Site Structure
nav_order: 4
---

## 7.4. Defining application events

IoT applications often employ some kind of inter-device eventing model. Softnet offers a Pub/Sub eventing model where publishers are services and subscribers are clients, all registered on the same site. Each event has a category, name and other parameters and must be declared in the site structure. Softnet uses this information to configure the event broker hosted on the site. Softnet supports three categories of events – Replacing, Queueing, and Private.  

**Replacing Events** are convenient for representing changes in the parameters of electro-mechanical objects over time. For example, such parameters can be the readings of temperature, humidity, pressure, etc. The service can raise a Replacing event whenever the corresponding parameter reaches a certain value or changes by a certain delta. Each time an event is received by the broker, the previous instance is replaced with a new one. Thus, at any given time, a subscribed client can only receive the latest event.  

To define a Replacing event, <span class="datatype">SiteStructure</span> has three overloads of the addReplacingEvent method. They differ in the way of defining access rules. See chapter "[15. Access rules definition technique]({{ site.baseurl }}{% link docs/access-rules.md %})" for details. It is important to note that despite access restrictions, any client can subscribe to any Replacing (or Queueing) event, but only an authorized client can receive it.  

The first overload of <span class="method">addReplacingEvent</span> implies no access restrictions, that is, any client can receive the subscribed event. It has the following signature:
```java
void addReplacingEvent(String eventName)
```
The name length must be in the range [1, 256].  

For the other two overloads of the method, see "[chapter 15]({{ site.baseurl }}{% link docs/access-rules.md %})". The following three examples show three different ways of defining access rules:  

1) Definition of some Replacing event named "Current Temperature" with no access restrictions:
```java
siteStructure.addReplacingEvent("Current Temperature");
```

2) Definition of some event named "Air Humidity". Here, all clients except guests are allowed to subscribe to the event:
```java
siteStructure.addReplacingEvent("Air Humidity", GuestAccess.GuestDenied);
```

3) Definition of some event named "Current Pressure". Here, only two of the four earlier defined roles are allowed to subscribe. The rest two roles are denied. Guest clients, of course, are also denied:
```java
siteStructure.addReplacingEvent("Current Pressure", "Administrator; Operator");
```

P.S. Remember that user roles must be defined in advance (using the <span class="method">SiteStructure.setRoles</span> method) before they can be used in access rule definitions.  

**Queueing Events** are used when clients need all events of a certain type to be received in chronological order. Every new event joins the queue of events created before. It is obvious that a Queueing event, in contrast to Replacing event, has two additional properties – the lifetime and the queue size. The event lifetime prescribes how long an event should be stored on the Softnet server before being removed, and the maximum queue size – the maximum number of events in the queue. That is, if the queue is full and the broker receives a new event, the oldest one will be removed and the new one added to the queue.  

To define a Queueing event, <span class="datatype">SiteStructure</span> has three overloads of the <span class="method">addQueueingEvent</span> method. As with a Replacing event, they differ in the way of defining access rules. The first overload with no access restrictions has the following signature:
```java
void addQueueingEvent(String eventName, int lifetime, int maxQueueSize)
```
The first parameter takes the event name. The second parameter takes the event lifetime in seconds. The third parameter takes the maximum queue size.  
Parameters have the following restrictions: name length: [1, 256]; lifetime: [1 minute, 30 days]; maximum queue size: [1, 1000].  
The other two overloads of the method have the fifth parameter, which is either a list of authorized roles or a guest denying level. For details, see [chapter 15]({{ site.baseurl }}{% link docs/access-rules.md %}).  

The example below defines a Queueing event with the following parameters: name: "CriticalPressure"; lifetime: 2 hours and 30 minutes; max queue size: 50. All users authorized.
```java
siteStructure.addQueueingEvent("CriticalPressure", TimeSpan.fromHM(2,30), 50);
```
To provide the life time in usual time units, the example uses the <span class="datatype">TimeSpan</span> convertor. It is described in [Annex 1]({{ site.baseurl }}{% link docs/annex-1.md %}) of this guide.  

**Private Events** are designed for sending notifications to particular clients. The mechanism is useful in implementing asynchronous interaction scheme. Imagine that a client sends a request to the service that may take an indefinitely long time to be processed. In that case, the service can complete the request-response session by informing the client that it will be notified when the result is ready. And when the result is ready, the service can send a Private event to the client containing either the result itself or the notification that the result is ready. Any client, except stateless, can subscribe to Private events, but each client receives only those events that were addressed to it.  

The method for defining a Private event has two parameters. The first one takes the name of the event, and the next parameter takes the lifetime. A Private event is stored on the Softnet server until the lifetime expires or it is deleted as the oldest one when the event queue overflows. The maximum queue size is fixed at 1000. The method signature:
```java
void addPrivateEvent(String eventName, int lifetime)
```
Parameters have the following restrictions: name length: [1, 256]; lifetime: [1 minute, 30 days].  

The example below defines a Private event with the following parameters: name: "UnloadCompleted"; lifetime: 30 minutes.
```java
siteStructure.addPrivateEvent("UnloadCompleted",  TimeSpan.fromMinutes(30));
```
