---
layout: default
title: 18.2. Event Persistence
parent: 18. Application Events
nav_order: 2
---

## 18.2. Event Persistence

Typically, a Softnet service raises an event in response to a change in the state of the electromechanical device hosting the service. This can happen regardless of whether or not the service is connected to the site. If the service is not connected, the event cannot be sent to the broker at that time. Depending on the implementation and current settings, the service endpoint can persist events until the connection is re-established and then deliver them to the broker. On the other hand, a client subscribed to the event may also be disconnected from the site when a new event is received by the broker. However, the broker can deliver the event later when the client connects to the site. A client endpoint does not persist events, unlike a service endpoint, but only information about the last events received. On the broker, each event is persisted in a queue for the lifetime of the event or until it is pushed out by a new received event. An ability of the platform to store events while network failures or endpoint disconnections is called Event Persistence. The persistence mechanism of the service endpoint is called **Service Persistence**. It can have one or two levels depending on the underlying platform. Each level implements the <span class="datatype">ServicePersistence</span> interface. Accordingly, the persistence mechanism of the client endpoint is called **Client Persistence**. It can also have one or two levels depending on the underlying platform.  Each level implements the <span class="datatype">ClientPersistence</span> interface.  

The level of persistence directly affects the quality of event delivery. Ideally, each event raised by the service must be delivered to the broker once and only once. And further, each event stored on the broker must be delivered to each subscribed client once and only once. However, there are variations depending on the persistence level on both communicating parts – service and client. In the following sections, we'll take a closer look at endpoint persistence mechanisms.

