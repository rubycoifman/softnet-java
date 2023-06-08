---
layout: default
title: 10.1. The User Membership API
parent: 10. User Membership
nav_order: 1
---

## 10.1. The User Membership API

The User Membership supported by the service endpoint contains a list of <span class="datatype">MembershipUser</span> objects. Each object represents a domain user and its permissions to access the service. Let’s take a look at the <span class="datatype">MembershipUser</span> interface members:
```java
public interface MembershipUser
{
    long getId();
    String getName();
    String[] getRoles();
    boolean hasRoles();
    boolean isInRole(String role);
    boolean isGuest();
    boolean isStatelessGuest();
    boolean isRemoved();
}
```
- <span class="method">getId</span> – returns the user’s ID. For a guest, this is 0, otherwise a positive value. Unlike the username, it never changes;
- <span class="method">getName</span> – returns the username. It can be changed by the administrator on the domain management page;
- <span class="method">getRoles</span> – if the service employs a role-based access control, it returns the list of roles assigned to the user on the site;
- <span class="method">hasRoles</span> – returns true if the user has any role;
- <span class="method">isInRole</span> – checks if the user in the given role;
- <span class="method">isGuest</span> – returns true if the membership user is a guest, otherwise false;
- <span class="method">isStatelessGuest</span> – returns true if the membership user is a stateless guest, otherwise false. If it is a stateless guest, it is also a guest;
- <span class="method">isRemoved</span> – returns true if the user has been removed from the list of authorized users on the site.  

<span class="datatype">ServiceEndpoint</span> has three API methods to access membership users other than Guest and Stateless Guest. The <span class="method">getUsers</span> method returns all users:  
```java
public MembershipUser[] getUsers()
```

The next method returns a membership user by user ID:
```java
public MembershipUser findUser(long userId)
```

The third method is a findUser overload to look up a user by name:
```java
public MembershipUser findUser(String userName)
```

If Guest or Stateless Guest users are declared as supported in the site structure, then the User Membership module contains the corresponding users of type <span class="datatype">MembershipUser</span>. Explicit access to them does not make any practical sense. Therefore, the methods described above do not return them. However, the request context, which is the first parameter of any request handler, can contain a guest user in the user field. This is described in [section 10.3]({{ site.baseurl }}{% link docs/user-membership/sections/fine-grained-access-control.md %}).  

A service application can check if Guest is allowed by calling the following method of <span class="datatype">ServiceEndpoint</span> class:
```java
public boolean isGuestAllowed()
```

It's worth noting that Guest and Stateless Guest are both guest users, and the return value of <span class="method">isGuestAllowed</span> is valid for both.  

In the end, Let’s look at how to enumerate the entire list of membership users:
```java
MembershipUser[] users = serviceEndpoint.getUsers();
for(MembershipUser user: users)
{
    System.out.println(user.getName());
    for(String role: user.getRoles()) {
        System.out.println("  " + role);
    }
}
```