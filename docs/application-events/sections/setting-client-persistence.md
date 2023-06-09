---
layout: default
title: 18.6. Setting up the client persistence
parent: 18. Application Events
nav_order: 6
---

## 18.6. Setting up the client persistence

Unlike in the case with services, the client persistence is set to level 1 by default. If you want to set it to the layer 2 (CPL-2), you must do so before the initial call to the endpoint's <span class="method">connect</span> method. The <span class="datatype">ClientEndpoint</span> class has a method <span class="method">setPersistenceL2</span> to set CPL-2:
```java
public void setPersistenceL2()
```

The storage file will be created in the directory where the Softnet library file "softnet.jar" is located. The platform uses the following scheme for naming client persistence storage files:
```
softnet.client.persistence_<serverAddress>_<clientKey>.scp
```

The platform takes the values of &lt;serverAddress&gt; and &lt;clientKey&gt; from the client URI provided to the endpoint, which has the following form:
```
<clientScheme>://<clientKey>@<serverAddress>
```

The second overload of the <span class="method">setPersistenceL2</span> method sets the persistence with a different location of the storage file:
```java
public void setPersistenceL2(String fileBasedStorageDirectory)
```

There is a third overload of the <span class="method">setPersistenceL2</span> method designed to set a custom implementation of CPL-2:
```java
public void setPersistenceL2(ClientPersistence clientPersistence)
```

This was the last of the sections on event persistence and quality of event delivery. The following sections of the chapter describe the technique for raising and handling application events.
