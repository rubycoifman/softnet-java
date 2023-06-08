---
layout: default
title: 13.2.5. Persistence event handler
parent: 13.2. Implementing event handlers
grand_parent: 13. Platform events related to clients
nav_order: 5
---

## 13.2.5. Persistence event handler

This handler intercepts error notifications raised by the client persistence component. Apart from notifying the user or logging errors, there is no way for an application to handle this error. The handler signature:
```java
void onPersistenceFailed(ClientPersistenceFailedEvent e)
```

Parameter 'e' contains a field <span class="field">exception</span>. It can be an object of one of the following exception classes derived from <span class="exception">PersistenceSoftnetException</span>:
*	<span class="exception">PersistenceIOSoftnetException</span> – CPL-2 (client persistence level 2) encountered an I/O error and the endpoint switched to CPL-1 (client persistence level 1);
*	<span class="exception">PersistenceDataFormatSoftnetException</span> – CPL-2 encountered a data format error when reading stored information about events already received. This error causes the storage to be cleared. The endpoint continues to use CPL-2;

