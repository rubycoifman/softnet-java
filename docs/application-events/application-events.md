---
layout: default
title: 18. Application Events
nav_order: 18
has_children: true
---

## 18. Application Events

Interaction with a remote device is often triggered by an event on that device. This is called event-driven interaction. The popular eventing model in IoT is Pub/Sub. For example, it is employed by MQTT. Pub/Sub implements loose coupling between publishers and subscribers. That is, subscribers have no idea who the publishers are. Softnet also implements the Pub/Sub model. Services publish events on the site, and clients subscribe to the events. But unlike the traditional model, clients know which service raised each event they received. This is because a suite of events that a given service is able to raise is part of its interface contract. Use the Softnet eventing pattern to react to the state changes on the remote autonomous devices.