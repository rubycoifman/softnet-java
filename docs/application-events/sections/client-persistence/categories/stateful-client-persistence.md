---
layout: default
title: 18.4.1. Stateful Client Persistence
parent: 18.4. Client Persistence
grand_parent: 18. Application Events
nav_order: 1
---

## 18.4.1. Stateful Client Persistence

Softnet stores on the server the event subscriptions of a stateful client, along with the ID of the last event delivered to the endpoint for each subscription. When a client connects to the site, the platform creates a client agent on the broker and initializes it with the subscription data stored on the server. After synchronizing with the client, the agent checks each authorized subscription for the next event in the queue after the last one delivered. If such an event exists, it sends it and waits for an acknowledgment. The agent sends the next event only after receiving an acknowledgment for the last event sent. Of course, the agent repeats the event message if an acknowledgement was not received within the timeout. On the endpoint side, this model requires a persistence mechanism to store the ID of the last event received for each subscription. This allows the endpoint to recognize and acknowledge duplicate events received from the broker, without calling the application's event handler again. This protects client from handling the same event multiple times.  

Next, we consider the quality of receiving events by a stateful client at various levels of persistence. If a stateful client uses CPL-1 then we have the following two cases:
1.	If the client connects to the site for the first time since the client application restarted and previously had received an event from the broker that was not acknowledged due to application termination, then if the event is still kept on the broker, the client receives it again. For a stateful client, such unacknowledged event can be only one per subscription. Thus, CPL-1 unable to protect client from handling the same event multiple times. This is due to the fact that CPL-1 loses information about the last received event per subscription when the application terminates;
2.	For all subsequent reconnections without restarting the application, CPL-1 ensures that each event is received once and only once.  

If a client uses CPL-2 then it is an ideal case where each event is received once and only once regardless of application restarts. If CPL-2 encounters an I/O error during storage operations, the platform switches the endpoint to CPL-1.
