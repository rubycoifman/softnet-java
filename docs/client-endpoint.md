---
layout: default
title: 9. Client Endpoint
nav_order: 9
---

## 9. Client Endpoint

There are four categories of client endpoints. Two of them are multi-service and single-service endpoints, and, accordingly, two classes that implement them – <span class="datatype">ClientEndpoint</span> and <span class="datatype">ClientSEndpoint</span> derived from the first. Another two categories are multi-service and single-service endpoints for stateless guest clients. These categories are implemented by the same classes – <span class="datatype">ClientEndpoint</span> and <span class="datatype">ClientSEndpoint</span>, but have restricted capabilities – they unable to receive Private events. Also, they differ in the quality of receiving events. See [section 18.6]({{ site.baseurl }}{% link docs/application-events/sections/client-persistence/categories/stateless-client-persistence.md %}) for details.  

You know that each client connects to the site using a URI. Softnet defines 4 client URI formats, one for each category of clients. These formats differ only in URI schemes:  

 &nbsp;&nbsp; 1) softnet-s://&lt;clientKey&gt;@&lt;serverAddress&gt;  
 &nbsp;&nbsp; 2) softnet-m://&lt;clientKey&gt;@&lt;serverAddress&gt;  
 &nbsp;&nbsp; 3) softnet-ss://&lt;clientKey&gt;@&lt;serverAddress&gt;  
 &nbsp;&nbsp; 4) softnet-ms://&lt;clientKey&gt;@&lt;serverAddress&gt;  

Here, &lt;clientKey&gt; is a client key used as an account name to identify the client on the site,  and &lt;serverAddress&gt is the Softnet server address.  

1) 'softnet-s' denotes a single-service endpoint for a client. This client can communicate only with a single remote service;  

2) 'softnet-m' denotes a multi-service endpoint for a client. This client can communicate with multiple remote services of the same type and hosted on a single site;  

3) 'softnet-ss' denotes a single-service stateless endpoint for a guest client. This client can communicate only with a single service and as a stateless client unable to receive Private events fired on the service;  

4) 'softnet-ms' denotes a multi-service stateless endpoint for a guest client. This client can communicate with multiple services of the same type, and it also unable to receive Private events.  

A client app starts by creating an instance of <span class="datatype">ClientURI</span>. This class parses a client URI string into its components. The following are the class members:
```java
public class ClientURI
{
	public final String scheme;
	public final String clientKey;
	public final String server;
	public final ClientCategory category;
	public final String value;
	
	public boolean isSingleService()
	public boolean isMultiService()
	public boolean isStateless() 
	public boolean isStateful() 

	public ClientURI(String value)
}
```
Here, we are interested in a category field of type <span class="datatype">ClientCategory</span>, which is an enumeration with the following elements:
```java
public enum ClientCategory {
	SingleService,
	MultiService,
	SingleServiceStateless,
	MultiServiceStateless
}
```
Based on the category field of <span class="datatype">ClientURI</span>, at the very beginning, your client app decides which category endpoint to create and which code branch to execute - a branch that works with multiple remote services or just one service. Or perhaps the URI denotes a stateless guest client. If your application does not support the category denoted by the URI, it may inform the user of this.  

For example, if your client app is supposed to communicate with a single remote service and does not allow for the functional limitations of stateless clients, your app can do the following check and if everything is ok, create the endpoint:
```java
if(clientURI.isSingleService() && clientURI.isStateful()) {
    // your code
}
```
Now let's consider how to create the endpoint itself. The following is a static method of the <span class="datatype">ClientEndpoint</span> class for creating a multi-service endpoint:
```java
public static ClientEndpoint create(
    String serviceType,
    String contractAuthor,
    ClientURI clientURI,
    String password,
    String clientDescription)
```
The second class, <span class="datatype">ClientSEndpoint</span>, derived from <span class="datatype">ClientEndpoint</span> has identical method that differs only in the type of the return value. Pay attention to the first two parameters – serviceType and contractAuthor. They are identical to those of the method for creating the site structure. The values must also be identical. Suppose you are a member of the T-800 development team and you are building your project on Softnet. If you had specified the names "T-800" and "Cyberdyne Systems" when creating the site structure, then you would have to specify the same values for clients designed to interact with this machine :). If the names provided by a client differ in any way, it goes down with status &lt;<span class="text-error">service type conflict</span>&gt; when it connects to the site. This guaranties that all services and clients on the site conform to the same service contract.  

The next two parameters, *clientURI* and *password*, are the client URI and the password that the administrator takes on the site page in Softnet MS.
The final fifth parameter, *clientDescription*, is optional. You can provide a short description name denoting the client's functionality, the version of the client, and the name of the client app developer (a person or organization). This description is printed on the site page, which helps administrators in performing management tasks as it makes client registrations visually more informative.  

The length of *serviceType* and *contractAuthor* should be in the range [1, 256]. The length of *password* and *clientDescription* must not be greater than 256. The latter can be null. Both parameters are ignored for stateless clients.  

Now let’s see what clients can do with the version of the service. The service app provides the version to the create method when it instantiates the <span class="datatype">ServiceEndpoint</span> class. On the client side, the service is represented by the <span class="datatype">RemoteService</span> object. The following is the <span class="datatype">RemoteService</span> interface definition:
```java
public interface RemoteService
{
	long getId();
	String getHostname();
	String getVersion();
	boolean isOnline();
	boolean isRemoved();
}
```
It has a method <span class="method">getVersion</span> that returns the same version string provided by the service. When the service gets online, Softnet notifies the client about it by raising an event with the <span class="datatype">RemoteService</span> object as an argument. For more information on this, see chapter "[13. Platform events related to clients]({{ site.baseurl }}{% link docs/client-platform-events/intro.md %})". Before making a request to the service, the client can get the version string by calling <span class="method">getVersion</span>, parse it into the components, and check for version compatibility.

