---
layout: default
title: 7.3. Defining user roles
parent: 7. Site and Site Structure
nav_order: 3
---

## 7.3. Defining user roles

A service application in Softnet can employ one of the two access control schemes – plain access control (PAC) or role-based access control (RBAC). If your service employs plain access control, you don’t have to do anything with the site structure. Domain users will have a full access to your service by the fact of being added to the site. If your service employs RBAC, you have to define the list of roles in the site structure. Later on, when the service connects to a blank site and the structure is applied to the site, the roles will be presented to the service owner so that he/she will be able to distribute them among domain users.
The roles you have specified can be used in the app code when defining access rules for service events, RPC procedures, and TCP and UDP virtual port listeners. 
To define the list of roles, <span class="datatype">SiteStructure</span> has the following method:
```java
void setRoles(String roles)
```
The method expects the string of role names delimited with semicolons. The names can be arranged in such an order that you want to see on the site management page. The name length must be in the range [1, 256].  The following example specifies four user roles, and sets one of them as a default role for Owner: 
```java
siteStructure.setRoles("Administrator; Editor; Operator; User");
siteStructure.setOwnerRole("Administrator");
```
