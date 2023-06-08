---
layout: default
title: 18.3. Service Persistence
parent: 18. Application Events
nav_order: 3
---

## 18.3. Service Persistence

The service persistence mechanism is designed to persist events during a network outage, application restart, or system reboot. The following are the definitions of service persistence levels:  

**SPL-1**: if events persist during a network outage but are lost across an application restart or system reboot, then this is Service Persistence Level 1, abbreviated as SPL-1. Softnet implements it using RAM-based storage. That is, events are simply kept in memory until they are delivered. Because memory is a limited resource, the default storage capacity is small;  

**SPL-2**: if events persist both during a network outage and across an application restart or system reboot, then this is Service Persistence Level 2, abbreviated as SPL-2. Softnet implements it using file-based storage. Along with better persistence, its obvious advantage is a big capacity. But this requires the underlying platform to support the file system. Of course, operating systems capable of running Java Virtual Machine are likely to support a file system as well. However, this can be a problem with Softnet libraries intended for firmware-based platforms. It is also possible to use a custom SPL-2 mechanism that implements the ServicePersistence interface. For example, it could be designed to use a database as storage.  

As you already know, an event broker implements a queue for each event defined in the site structure. When a service endpoint sends an event to the broker, it waits for an acknowledgment from the broker before sending the next event. The service agent on the broker always keeps the ID of the last event received from the endpoint and for which an acknowledgment has been sent back. The service agent decides that the acknowledgment has been delivered to the endpoint when it receives the next event. If it receives the same event, it simply repeats the acknowledgment. This model requires that the persistence mechanism on the endpoint also keeps the ID of the last acknowledged event. On receiving an acknowledgment, the event is removed from the persistence storage.  

Let's consider the quality of delivering events from a service to the broker at various levels of persistence.  If the service app uses SPL-1, each event is delivered to the broker once and only once, however if the app restarts, any undelivered events are lost. If the service app uses SPL-2, it is an ideal case where every event raised by the service is delivered to the broker once and only once, and even when the app is restarted, events aren't lost. If the persistence storage is full and new events continue to arrive, the old ones are removed from the storage and, as a result, are lost. This is true for both levels of persistence. Therefore, it is recommended to set an acceptable storage capacity. If the SPL-2 mechanism encounters an IO error on storage operations, all events in the storage are lost and the platform switches the endpoint to the SPL-1 persistence.