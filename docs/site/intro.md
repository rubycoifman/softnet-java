---
layout: default
title: 7. Site and Site Structure
nav_order: 7
has_children: true
---

## 7. Site and Site Structure

**Site** is a key abstraction in Softnet. The owner creates a site to host one or multiple identical services and their clients. The site runs on the Softnet server and ensures interaction of the connected endpoints. The site synchronizes states of the endpoints, communicates commands between them, receives events from the services and delivers them to the clients, transmits RPC requests and responses, controls the authority of users and associated clients, supports the endpoint accounts, etc. Each site has properties specific for a given service type. The service application defines these properties in a structure called **Site Structure**. When the service connects to a blank site for the first time, the service provides the structure to the Softnet server, and it constructs the site according to these properties.
Softnet provides an implementation of the SiteStructure interface – the object that developers use to define the site structure.
