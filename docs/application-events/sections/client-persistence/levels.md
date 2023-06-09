---
layout: default
title: 18.4. Client Persistence
parent: 18. Application Events
has_children: true
nav_order: 4
---

## 18.4. Client Persistence

The client persistence mechanism stores only information about the last received event per subscription. The following are the definitions of two persistence levels:  

**CPL-1**: if information about the last received events is retained during a network outage, but is lost across an application restart or system reboot, then this is Client Persistence Level 1, abbreviated as CPL-1. Softnet implements it using RAM-based storage. The requirement for storage capacity is negligible and the capacity as a parameter is not used at all;  

**CPL-2**: if information about the last received event per subscription is retained both during a network outage and across an application restart or system reboot, then this is Client Persistence Level 2, abbreviated as CPL-2. Softnet implements it using file-based storage. CPL-2 ensures better persistence but requires the underlying platform to support the file system. Its capacity requirement is also negligible and the capacity as a parameter is not used. Softnet provides a <span class="datatype">ClientPersistence</span> interface that developers must implement when developing their own CPL-2 mechanism.  

The quality of receiving events depends on the persistence level of the client, but it also depends on whether the client is stateful or stateless. As you know, a stateful client has a registration on the site, while a stateless client connects using a shared guest URI and does not have an account. This circumstance affects the quality of receiving events.
