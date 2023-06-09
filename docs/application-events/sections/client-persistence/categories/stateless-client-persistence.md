---
layout: default
title: 18.4.2. Stateless Client Persistence
parent: 18.4. Client Persistence
grand_parent: 18. Application Events
nav_order: 2
---

## 18.4.2. Stateless Client Persistence

A stateless client is a guest client without an account on the site. Therefore, the site does not save the client's state when the connection to the client is lost. When a stateless client connects to the site, it provides information about each event subscription and the ID of the last event received under that subscription, if any. The broker creates a client agent and initializes it using the information provided. From this point on, the agent works in the same way as the agent of a stateful client as described in the previous section. That is, the agent checks each authorized subscription for the next event in the queue after the last one delivered. If such an event exists, it sends it and waits for an acknowledgment. The agent sends the next event only after receiving an acknowledgment for the last event sent. The agent repeats the event message if an acknowledgement was not received within the timeout.  

The critical difference from the stateful model is that this agent does not initially have information about the last delivered event per subscription. If the client persistence mechanism is unable to provide this information, the temporary agent starts sending events from the first event in the queue under each subscription.  

Let’s consider the quality of receiving events by a stateless client at various levels of persistence. If a stateless client uses CPL-1 then we have the following two cases:
*	If the client connects to the site for the first time since the client application was launched, then it receives all events in the queue under each subscription, even if they were received earlier. This is because CPL-1 loses information about the last received events when the application terminates, and the broker also does not save information about the last delivered events when the connection with a stateless client is lost. In contrast, a stateful client in such a case could have only one unacknowledged event to receive per subscription;
*	For all subsequent reconnections without restarting the application, CPL-1 ensures that each event is received once and only once.  

If a stateless client uses CPL-2 then it is an ideal case where each event is received once and only once regardless of application restart or system reboot. If CPL-2 encounters an I/O error during storage operations, the platform switches the endpoint to CPL-1.
