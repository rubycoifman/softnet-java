---
layout: default
title: 17.1. Handling RPC requests
parent: 17. Remote Procedure Calls
nav_order: 1
---

## 17.1. Handling RPC requests

To handle RPC requests, your application must first register procedures. The service endpoint has three overloaded methods for this, which differ in how they define access rules:  

1) The first method applies no access restrictions and authorizes any request:
```java
void registerProcedure(
    String procedureName,
    RPCRequestHandler requestHandler,
    int concurrencyLimit)
```

2) The second method creates an access rule for guest clients using the value specified in the fourth parameter. Clients from all named users are allowed:
```java
public void registerProcedure(
    String procedureName,
    RPCRequestHandler requestHandler,
    int concurrencyLimit,
    GuestAccess guestAccess)
```

3) The third method creates an access rule based on the list of user roles specified in the fourth parameter:
```java
public void registerProcedure(
    String procedureName,
    RPCRequestHandler requestHandler,
    int concurrencyLimit,
    String roles)
```

See [chapter 15]({{ site.baseurl }}{% link docs/access-rules.md %}) for the technique of defining access rules. Now let’s consider the first three parameters of the method:
*	<span class="param">procedureName</span> is the name of the procedure that clients specify when making requests;
*	<span class="param">requestHandler</span> is an instance of the <span class="datatype">RPCRequestHandler</span> interface implementation. The interface is described below;
*	<span class="param">concurrencyLimit</span> determines how many request handler instances can run concurrently. By choosing the appropriate value for this parameter, you can control the consumption of the host resources.  

Here is the <span class="datatype">RPCRequestHandler</span> interface. You have to implement the only method <span class="method">execute</span>:
```java
public interface RPCRequestHandler {
    int execute(
        RequestContext context,
        SequenceDecoder parameters,
        SequenceEncoder result,
        SequenceEncoder error);
}
```
The following list is the execute method’s parameters:
*	<span class="param">context</span> is, as usual, the first parameter of any native request handler of Softnet;
*	<span class="param">parameters</span> is of type <span class="datatype">asn.SequenseDecoder</span> and contains the data passed by the caller in ASN.1 DER format. It can have 64 kilobytes at most. If it contains a serialized data in any other format, your handler gets it as an ASN.1 OctetString or UTF8String element and deserializes;
*	<span class="param">result</span> is an output parameter of type <span class="datatype">asn.SequenseEncoder</span>. Your handler can return the result in it. The maximum result data size is 64 kilobytes. If your handler uses any other format to transfer data, it can serialize the data and provide it to <span class="param">result</span> as an ASN.1 OctetString or UTF8String element;
*	<span class="param">error</span> is an output parameter of type <span class="datatype">asn.SequenseEncoder</span>. Your handler can return additional data in case of error. For example, it could be an error message. Note that Softnet only accepts this data for delivery to the client if the execute method returns a non-zero value.  

The <span class="method">execute</span> method returns an integer value. If the method succeeds, it must return zero, otherwise non-zero. In case of an error, your application can use the return value as an error code. Along with this, the handler can return additional data in the error parameter.  

Below is an example of handling an RPC request. The <span class="datatype">RPCRequestHandler</span> interface is implemented using an anonymous class. The procedure transponses a matrix provided in <span class="param">parameters</span>. In the first two elements, the <span class="param">parameters</span> sequence provides the size of the original matrix. The next element is of type SEQUENCE OF Raw, where Raw is of type SEQUENCE OF INTEGER. The number of raws and colomns are restricted by the range [2, 32]. The following is an ASN.1 definition for both input and output data structures:
```
parameters ::= SEQUENCE {
	rawsCount ::= INTEGER (2..32)
	colomnsCount ::= INTEGER (2..32)
	rows ::= SEQUENCE (SIZE (2..32)) OF Raw
}
Raw ::= SEQUENCE (SIZE (2..32)) OF INTEGER
```

And then the code itself:
```java
serviceEndpoint.registerProcedure("TransposeMatrix", new RPCRequestHandler() {				
    public int execute(
        RequestContext context,
        SequenceDecoder parameters,
        SequenceEncoder result,
        SequenceEncoder error) {
        try 
        {
            int rowsCount = parameters.Int32(2,32);
            int colomnsCount = parameters.Int32(2,32);

            SequenceOfDecoder asnRows = parameters.SequenceOf(UType.Sequence);
            if(asnRows.count() != rowsCount) {
                error.UTF8String(String.format(
                    "The actual number of rows %d does not
                    correspond to the declared %d", 			
                    asnRows.count(),
                    rowsCount));
                return -1;
            }
						
            result.Int32(colomnsCount);
            result.Int32(rowsCount);						
            SequenceOfEncoder asnResultRows = result.SequenceOf(UType.Sequence);

            SequenceOfEncoder[] resultRows = new SequenceOfEncoder[colomnsCount];	
            for(int i=0; i < colomnsCount; i++)
                resultRows[i] = asnResultRows.SequenceOf(UType.Integer);
	
            for(int j = 0; j < asnRows.count(); j++) 
            {							
                SequenceOfDecoder asnRow = asnRows.SequenceOf(UType.Integer);
                if(asnRow.count() != colomnsCount) 
                {
                    error.UTF8String(String.format(
                        "The actual number of colomns %d in the row %d 
                        does not correspond to the declared %d",
                        asnRow.count(),
                        j,
                        colomnsCount));
                    return -1;
                }                
                for(int t=0; t < colomnsCount; t++)
                    resultRows[t].Int32(asnRow.Int32());
            }					
            return 0;
        }
        catch(RestrictionAsnException e) {
            error.UTF8String(e.getMessage());
            return -1;						
        }
        catch(AsnException e) {
            error.UTF8String(e.getMessage());
            return -2;						
        }
    }
},
5 /* Concurrency Limit */,
"Operator; Editor" /* Authorized Roles */);
```