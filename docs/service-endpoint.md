---
layout: default
title: 8. Service Endpoint
nav_order: 8
---

## 8. Service Endpoint

After you have defined the site structure, you can move on to designing the service endpoint. This is a gateway through which the service application communicates with clients. The service endpoint is implemented by the <span class="datatype">ServiceEndpoint</span> class. It has a static method to create an instance. Let's look at the signature of the method and see how it works:
```java
public static ServiceEndpoint create(
    SiteStructure siteStructure, 
    String serviceVersion, 
    ServiceURI serviceURI, 
    String password) throws HostFunctionalitySoftnetException
```
The first parameter, *siteStructure*, is the site structure. This is how you bind it to the endpoint. Earlier, we overviewed the static method of the same class for creating the site structure:
```java
public static SiteStructure createStructure(
	String serviceType,
	String contractAuthor)
```
This method takes the names of Service Type and Contract Author which together with high probability uniquely identify the service’s interface contract. Later, you’ll see how these names are also used in instantiating a client endpoint. But now we continue with the parameters of the <span class="method">create</span> method.  

The second parameter, *serviceVersion*, is the version of the service’s interface contract. It's just a string representation of the version in the format you've chosen for your application. For example, you can use a &lt;Major&gt;.&lt;Minor&gt;.&lt;Patch&gt; format. Softnet does not interpret the version string in any way, but simply presents it to the client. Usually, the version changes with software update. It is quite possible that the site structure remains unchanged while the service version changes. This is why the version is not a part of the site structure and the method takes it as a separate parameter. If you are not using version control, you can specify null for this parameter.  

And we have the last two parameters – *serviceURI* and *password* – the account data that you will most likely prefer to store in the app configuration file. The service owner takes this data on the site management page in Softnet MS. The service URI, which is of the form 'softnet-srv://&lt;serviceUuid&gt;@&lt;serverAddress&gt;', is used to instantiate the <span class="datatype">ServiceURI</span> class. An application passes it as an argument to the <span class="method">create</span> method's third parameter.  

This method has one requirement to the underlying platform – it is expected to support the SHA-1 hash algorithm, otherwise the method throws an exception <span class="exception">HostFunctionalitySoftnetException</span>. Softnet uses SHA-1 to calculate the hash from the site structure attached to the endpoint. Whenever the service connects to the site, Softnet compares the structure attached to the endpoint with one from which the site has been constructed by comparing their hashes. The difference in just one parameter or in a single character of any name can result in a difference in hashes. Therefore, please note that all names, such as role names, event names, and others, are case sensitive as well.

