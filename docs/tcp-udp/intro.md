---
layout: default
title: 16. TCP and UDP in dynamic IP environments
has_children: true
nav_order: 16
---

## 16. TCP and UDP in dynamic IP environments

It’s worth explaining why the name of the pattern emphasizes that TCP and UDP protocols are supported in dynamic IP environments. TCP and UDP are the most adopted transport protocols. However, establishing connections in dynamic IP environments is a challenging task. When the device moves from one network to another, it is often assigned a dynamic IP address. Also, the device may be situated behind a firewall or NAT. As a consequence, clients either do not know the device’s current address or the device becomes unreachable even when the address is known. There is also an IPv4/IPv6 inter-stack communication issue. Under these conditions, Softnet employs peer-to-peer and proxy techniques to establish connections. Everything is done transparently for applications. A client simply requests Softnet to establish a connection. If the remote service authorizes the request, Softnet does the job behind the scenes and returns the connected sockets to the client and the service. Softnet also supports IPv4/IPv6 inter-stack connections for both protocols, i.e., when connection endpoints are in different IPv4 and IPv6 networks. Services listen for TCP and UDP connection requests on virtual ports. Bindings to fixed physical ports aren't used. This approach ensures reliable protection against Brute-Force and DDoS attacks on services.