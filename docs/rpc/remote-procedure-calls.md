---
layout: default
title: 17. Remote Procedure Calls
has_children: true
nav_order: 17
---

## 17. Remote Procedure Calls

Most applications use application layer protocols based on TCP and UDP for client/server communication scenarios. However, some IoT tasks can be solved simply using a request-response pattern. In such cases, you can employ Softnet RPC instead of messing around with third party application layer protocols.  

In Softnet, all messages are in ASN.1 DER format. Applications can also take advantages of using Softnet ASN.1 Codec. Clients can pass data organized in complex hierarchical structures to the remote procedure up to 64 kilobytes in size. The service, in turn, returns the result also in ASN.1 DER format and up to 64 kilobytes. There is always a chance that an RPC request may result in an error. The handler can return structured information about the error in ASN.1 format. You can learn the specification for the ASN.1 codec in «[The Developer’s Guide to Softnet ASN.1 Codec (Java)](https://robert-koifman.github.io/asncodec-java){:target="_blank"}{:rel="noopener noreferrer"}».  

Note that apart from ASN.1, you can use any other format such as JSON to represent application data. In this case, before sending data, your application can serialize the data and provide it as an array of bytes to the Softnet ASN.1 sequence encoder. ASN.1 uses the OctetString universal type to represent an array of bytes. On the receiving side, your app can get the array of bytes from the ASN.1 sequence decoder and deserialize the binary data.