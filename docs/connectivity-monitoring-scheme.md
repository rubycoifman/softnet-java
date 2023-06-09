---
layout: default
title: 14. Connectivity monitoring scheme
nav_order: 14
---

## 14. Connectivity monitoring scheme

When an endpoint connects to the Softnet server, it establishes a control channel with the site. The channel is built on TCP. As is known, a TCP connection has fixed source IP:Port and destination IP:Port. However, if the source or destination host changes the IP address, the TCP connection becomes broken. Often, the underlying host platform does not notify applications that the host has changed its IP, and idle TCP connections can remain unaware that they are broken for quite a long time. And if the socket sends a message in such a situation, different TCP implementations may behave differently. One implementation can detect the error right away. Another might send a message with a stale source IP address and wait for an acknowledgment for the time defined as TCP Timeout, and then drop the connection as broken. By default, TCP Timeout is often 60 seconds. The third implementation can even discard the message without any error. Nowadays, there are a dozen TCP/IP implementations for industrial microcontrollers. Now imagine that a mobile unmanned vehicle, controlled remotely via such a connection, changes its IP address. As a result, the operator may lose the control of the vehicle for an unacceptable time.  

Softnet implements a connectivity monitoring scheme to solve the task of detecting broken channels as well as two more tasks. First, it preserves the state of the Internet path that the channel's TCP connection runs through. Second, it allows the Softnet server to detect hanging endpoints. Softnet employs a keep-alive mechanism to solve the last two tasks. To solve the problem of broken channels, Softnet employs a ping-pong mechanism. The two mechanisms work together in the following way. If in the last 5 minutes the endpoint has not sent any message to the site, it sends a keep-alive message which does not require a response. The interval is fixed and equal to 5 minutes. On the server side, if there are has been no messages from the endpoint in the last 6 minutes, the site qualifies it as hanging and closes the channel. If there have been no messages from the site during the last time specified by the ping period, the endpoint sends a ping message and waits for a pong message from the site for a period of time calculated as follows: if the ping period is between 1 and 5 minutes, it waits 1 minute. If the ping period is between 10 seconds and 1 minute, it waits for a pong message for the time equal to the ping period. If the pong message has not been received in this time, the endpoint closes the channel and starts successive attempts to establish a new one.  

The value of the ping period can be vital for applications that use devices with high mobility, which can change IP address quite often. Therefore, the ping period is made changeable both programmatically and through the site management panel.
Both the service and client endpoint classes have a method to set the ping period:
```java
public void setPingPeriod(int seconds)
```

The value is expected in seconds. It must be between 10 seconds and 300 seconds. If you provide 0, the period will be set to the default value, which is 300 seconds. However, if the ping period is set through the management panel, it overrides the value set programmatically. For example, the image below shows a print screen of the site management panel, where the service has a ping period set to 15 seconds:  

![]({{ site.baseurl }}{% link assets/images/image_14.1.png %})
