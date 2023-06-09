---
layout: default
title: 7.5. How it works
parent: 7. Site and Site Structure
nav_order: 5
---

## 7.5. How it works

Let's see what happens when the service connects to the site. If the site is not constructed yet (blank site with status &lt;site not constructed&gt;), it will be constructed in accordance with data in the SiteStructure object attached to the endpoint. Then, the service goes online. However, if the site has already been constructed, Softnet compares the attached structure with one from which the site has been constructed (in truth, Softnet compares hashes from those structures). As a result, there are three possible outcomes:
1.	The two structures are equal –> the service goes online and becomes available for clients to consume; 
2.	The service types are different –> the service goes down with status &lt;<span class="text-error">service type conflict</span>&gt;;
3.	The service types are equal, but the site structures are not –> the service goes down with status &lt;<span class="text-error">site structure mismatch</span>&gt;.
A Softnet client should also specify the type of service it was designed to consume. When the client connects to the site, there are two possible outcomes:
1.	The service types are equal –> the client goes online;
2.	The service types are different –> the client goes down with status &lt;<span class="text-error">service type conflict</span>&gt;.
