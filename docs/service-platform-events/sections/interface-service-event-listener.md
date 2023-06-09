---
layout: default
title: 12.1. Interface ServiceEventListener
parent: 12. Platform events related to services
nav_order: 1
---

## 12.1. Interface ServiceEventListener

Softnet provides the <span class="datatype">ServiceEventListener</span> interface that listener class implements to intercept service related events raised by the platform. An application provides the listener to the following method of the service endpoint:
```java
public void addEventListener(ServiceEventListener listener)
```
An application can provide multiple event listeners with different implementations. If you do not want to implement all methods of the interface, you can derive your listener from the <span class="datatype">ServiceEventAdapter</span> class which contains empty implementations of the handlers. This allows you to implement only those handlers that you need. Below is the listener interface:
```java
public interface ServiceEventListener extends EventListener
{	
	void onConnectivityChanged(ServiceEndpointEvent e);
	void onStatusChanged(ServiceEndpointEvent e);
	void onHostnameChanged(ServiceEndpointEvent e);
	void onUserIncluded(MembershipUserEvent e);
	void onUserUpdated(MembershipUserEvent e);
	void onUserRemoved(MembershipUserEvent e);
	void onUsersUpdated(ServiceEndpointEvent e);
	void onGuestAccessChanged(ServiceEndpointEvent e);
	void onPersistenceFailed(ServicePersistenceFailedEvent e);
}
```

Let’s consider each of the interface handlers:  

The model of Service Endpoint has two abstraction layers. The lower one is Channel Layer and the upper one is Functional Layer. The former is responsible for establishing a control channel and transmitting messages between the endpoint and the site. The upper abstraction layer consists of functional modules that provide communication services to applications. The first two methods of <span class="datatype">ServiceEventListener</span> intercept the status change events raised at these two abstraction layers:  
- <span class="method">onConnectivityChanged</span> is invoked whenever the connectivity status of the channel layer changes. After calling the connect method by the application, the channel layer makes successive attempts to establish a control channel. In case of success, the connectivity status of the service endpoint is Connected;
- <span class="method">OnStatusChanged</span> interceptes the status change event of the functional layer. The functional layer status is the status of the service itself. The site assigns it to the service after the connectivity status becomes Connected. If the service gets the online status, clients can interact with it;
The following 5 methods intercept the User Membership events that can be triggered by changes to domain users and their permissions to access the service:
- <span class="method">onUserIncluded</span> receives an event about the inclusion of a new user in the User Membership registry with some permissions to access the service. The event’s argument is a MembershipUser object. It contains the username, user ID, and if the application employs RBAC, it contains a list of roles assigned to the user;
- <span class="method">onUserUpdated</span> is invoked when the user has been updated. This could be changing the username or list of roles if your service employs RBAC;
- <span class="method">onUserRemoved</span> is invoked when the user loses all access permissions to the service or is removed from the domain at all;
- <span class="method">onUsersUpdated</span> is invoked when more than one user could have been updated. The event does not provide information on which users were affected. The only way to detect changes is to check the entire list of users;
- <span class="method">onGuestAccessChanged</span> is invoked when the enabled/disabled status of the guest is changed in the domain user settings or the allowed/denied status of the guest is changed in the site settings.  

The <span class="method">onHostnameChanged</span> event handler is invoked when the service hostname is changed by the owner on the site management panel.  

And the last handler, <span class="method">onPersistenceFailed</span>, intercepts error notifications raised by the service persistence component. The detailed guidance on service persistence is given in chapter "[18. Application Events]({{ site.baseurl }}{% link docs/application-events/intro.md %})".

