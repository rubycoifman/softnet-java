---
layout: default
title: 6. Softnet as a network of software endpoints
nav_order: 6
---

## 6. Softnet as a network of software endpoints

Softnet is all about client-service interaction. We use the terms **Service** and **Client** to refer to the software representations of an autonomous device and a client that consumes the device, respectively. The name Softnet itself comes from the words Software Network. Softnet defines **Service Endpoint** as an abstraction that enables service applications to employ networking functionality, while **Client Endpoint** is defined as an abstraction that enables client applications to employ networking functionality. To create a client endpoint, an application instantiates one of the two classes – <span class="datatype">ClientEndpoint</span> or <span class="datatype">ClientSEndpoint</span>. The first one implements a client endpoint to interact with multiple services of the same type. It is called a multi-service client endpoint. In turn, <span class="datatype">ClientSEndpoint</span> implements a client endpoint to interact with a single service. It is called a single-service client endpoint.
