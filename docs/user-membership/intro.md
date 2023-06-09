---
layout: default
title: 10. User Membership
nav_order: 10
has_children: true
---

## 10. User Membership

As mentioned in the very beginning of this guide, Softnet offers two mechanisms, Access Control and Service Status Detection, which make the platform convenient for IoT development. To ensure this functionality, Softnet implements two dynamic structures: **User Membership** and **Service Group**, respectively. The former is used by services and the latter by clients. In this section, we'll consider User Membership.  

The Softnet site implements a structure that contains permissions for domain users to access services registered on the site. This structure is called User Membership and is managed by the administrator through the site management panel. When the service application connects to the site, Softnet loads the User Membership data into the service endpoint and keeps it synchronized with the site. Any change to the User Membership made on the site is immediately propagated to the application’s copy. Whenever the service application receives a request from one of the clients, Softnet provides the application with a membership user associated with the client. This object contains the user’s permissions. The application can check if the user is allowed to access the requested resource. You can define access rules declaratively for the Softnet native request handling methods (see [section 10.2]({{ site.baseurl }}{% link docs/user-membership/sections/declarative-access-rules.md %})) or pass the membership user to application layer protocols for more granular access control (see [section 10.3]({{ site.baseurl }}{% link docs/user-membership/sections/fine-grained-access-control.md %})). The former is applicable in definitions of TCP and UDP listeners and RPC procedures. It is also the only way to define access rules for application events in the site structure. In this case, access control is applied to client subscriptions.
