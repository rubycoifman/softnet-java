---
layout: default
title: 7.2. Defining the guest support
parent: 7. Site and Site Structure
nav_order: 2
---

## 7.2. Defining the guest support

Some service applications may support guest access. It is convenient for users as do not require registration. Traditionally, a guest client has no state on the server as it has no account. However, some client-server interaction scenarios require that clients preserve the state on the server between reconnections. Imagine that some client makes a request to the service, but the response might be ready after an indefinite period of time. The request-response session cannot last such a long time and completes before session timeout expires. When the requested data is ready, the service needs to deliver it to the client. In Softnet, developers can use Private events for this purpose. However, Private events can only be sent to stateful (registered) clients. So that guest clients could also receive Private events, they are implemented as stateful clients, each with an account on the server and unique URI. If a Softnet service supports a guest access mode, it is called a public service. Any person can find any public service in a Softnet network and create a stateful guest client without even being logged into the Softnet management system (Softnet MS). If your service is expected to support stateful guests, specify this in the site structure:
```java
siteStructure.setGuestSupport();
```
Possibly, your service does not necessarily require for guest clients to be able to receive Private events. If so, you are advised to declare the support for stateless guests. This is convenient for users as it does not require the creation of an account for each guest client. Stateless clients connect to the site using a shared guest URI. Users can find it on the site management page in Softnet MS. You declare the support for stateless guests by the following call:
```java
siteStructure.setStatelessGuestSupport();
```
Please note that this call must be <u>preceded by setGuestSupport</u>.
