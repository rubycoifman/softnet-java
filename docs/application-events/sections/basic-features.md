---
layout: default
title: 18.1. Basic features
parent: 18. Application Events
nav_order: 1
---

## 18.1. Basic features

Softnet supports three categories of events – Replacing, Queueing, and Private. Events must be defined in the site structure, where, along with a category, you specify such parameters as the name, lifetime, max queue size, and access rule. This technique is described in [section 7.4]({{ site.baseurl }}{% link docs/site/sections/application-events.md %}). An event broker hosted on the site implements a queue for each event defined in the site structure. The queue of Replacing events can contain only one last event. Each new event replaces the old one. In contrast, each new Queueing event joins the queue of previously received instances. If the queue is full when a new event is received, the oldest one is removed from the tail to make room. The maximum size of a Queueing event queue is specified in the event definition. The queue of Private events works in a similar way. Its maximum size is fixed at 1000.  

An event can have arguments. For this purpose, event classes have an arguments field of the <span class="datatype">SequenceEncoder</span> type, provided by Softnet ASN.1 Codec, through which services can pass data organized in complex hierarchical structures to subscribers. Accordingly, on the receiving side, events have an arguments field of type <span class="datatype">SequenceDecoder</span>. The data size in arguments is limited to 2 kilobytes. Before raising an event, an application can check the size by calling the <span class="method">getSize</span> method on arguments.  

On the client side, each event has two additional properties that can be useful in event handling. These are <span class="field">age</span> and <span class="field">createdDate</span>. In the <span class="field">age</span> property, Softnet specifies the time elapsed since the event has been received by the broker. This value is zero if the event is sent to the client without delay as soon as it is received by the broker. You can use <span class="field">age</span>, for example, to ignore events that are older than a certain age. In the <span class="field">createdDate</span> property, Softnet specifies the date and time when the event has been received by the broker. Note that Softnet uses the timeline of the Softnet server, not the service that generated the event, to assign values to both properties.  

Application Events is a quite complex mechanism. To use it correctly, it is desirable that you understand what event persistence is and how it affects the quality of event delivery. The next sections describe this issue in detail.
