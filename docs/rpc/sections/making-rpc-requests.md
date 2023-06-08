---
layout: default
title: 17.2. Making RPC requests
parent: 17. Remote Procedure Calls
nav_order: 2
---

## 17.2. Making RPC requests

This section describes the technique of making RPC requests. Again, before making a request, it is desirable to check whether the remote service is online. See section "[13.1 Interface ClientEventListener]({{ site.baseurl }}{% link docs/client-platform-events/sections/interface-client-event-listener.md %})" for details.  

There are three overloaded methods to make an RPC call:  

1) The first method has three parameters that are the same for all three methods. They are described after the list of methods:
```java
public void call(
    RemoteService remoteService,
    RemoteProcedure remoteProcedure,
    RPCResponseHandler responseHandler)
```

2) The next method has the fourth parameter, attachment, which is used to pass state data to the response handler:
```java
public void call(
    RemoteService remoteService,
    RemoteProcedure remoteProcedure,
    RPCResponseHandler responseHandler,
    Object attachment)
```

3) The final method has the fifth parameter, waitSeconds, which is used to specify a custom time to wait for the request to complete before a wait timeout error occurs. The default wait timeout is 30 seconds:
```java
public void call(
    RemoteService remoteService,
    RemoteProcedure remoteProcedure,
    RPCResponseHandler responseHandler,
    Object attachment,
    int waitSeconds)
```
Here is a description of the first three parameters of the method:
*	<span class="param">remoteService</span> represents a service to which the request is addressed;
*	<span class="param">remoteProcedure</span> contains the name and arguments of the procedure;
*	<span class="param">responseHandler</span> is an implementation of the <span class="datatype">RPCResponseHandler</span> interface that the client app provides to the method;  

<span class="datatype">RemoteProcedure</span> has an <span class="field">arguments</span> field where the client provides arguments to call the method. It is of type <span class="datatype">SequenceEncoder</span> and the maximum data size is limited to 2 kilobytes. To learn the size of data in the <span class="field">arguments</span> field, you can call the <span class="method">getSize</span> method provided by <span class="datatype">SequenceEncoder</span>. 

When instantiating the <span class="datatype">RemoteProcedure</span> class, your application provides the procedure's name to the constructor. The public members of the class are shown below:
```java
public class RemoteProcedure {	
    public final String name;
    public final SequenceEncoder arguments;

    public RemoteProcedure(String name)
    // non-public members are omitted
}
```
And here is the <span class="datatype">RPCResponseHandler</span> interface:
```java
public interface RPCResponseHandler {
	void onSuccess(ResponseContext context, SequenceDecoder result);
	void onError(ResponseContext context, int errorCode, SequenceDecoder errorData);
	void onError(ResponseContext context, SoftnetException exception);
}
```

The interface has three methods to implement. Each request ends with a call to one of these methods by the endpoint. In case of success, it calls the <span class="method">onSuccess</span> method. Here is a description of its parameters:
*	<span class="param">context</span> is, as usual, the context of any response handler of the platform;
*	<span class="param">result</span> is an object of type <span class="datatype">SequenceDecoder</span> that contains the ASN.1 DER encoded data returned as a result by the remote procedure.  

The interface has two overloaded methods, <span class="method">onError</span>, to handle requests completed with an error. The first overloaded method, <span class="method">onError</span>, is called if an error is returned by the remote procedure. Here is a description of its parameters:
*	<span class="param">context</span> is the context of the response;
*	<span class="param">errorCode</span> is an error code returned by the remore procedure;
*	<span class="param">error</span> contains additional data that describes an error.  

The second overloaded method is called if an error is detected by the platform. It provides the error in the second parameter of type <span class="exception">SoftnetException</span>. Possible exceptions are listed below:
*	<span class="exception">ServiceOfflineSoftnetException</span> – the remote service is offline; 
*	<span class="exception">AccessDeniedSoftnetException</span> – the client does not have enough permissions to call the remote procedure;
*	<span class="exception">MissingProcedureSoftnetException</span> – the procedure name you specified when calling the RPC is incorrect;
*	<span class="exception">ServiceBusySoftnetException</span> – the number of current procedure calls on the server has reached the maximum concurrency limit;
*	<span class="exception">TimeoutExpiredSoftnetException</span> – RPC response timeout expired.  

Let's continue the example from the previous section and design a client that calls the "TransponseMatrix" RPC procedure. This client builds an NxM matrix from random integers, sends it to the procedure in the call arguments, and expects a transposed MxN matrix in response. It implements the <span class="datatype">RPCResponseHandler</span> interface using an anonymous class. In the first two elements, the arguments sequence provides the size of the original matrix. The next element, as in the example in the previous section, is of type SEQUENCE OF Raw, where Raw is of type SEQUENCE OF INTEGER. The number of rows and columns is limited by the range [2, 32]. The following is the ASN.1 definition for both input and output data structures:
```
parameters ::= SEQUENCE {
	rawsCount ::= INTEGER (2..32)
	colomnsCount ::= INTEGER (2..32)
	rows ::= SEQUENCE (SIZE (2..32)) OF Raw
}
Raw ::= SEQUENCE (SIZE (2..32)) OF INTEGER
```

And then the client code:
```java
static void makeTransponseMatrixCall(ClientEndpoint clientEndpoint, RemoteService remoteService) 
{		
    System.out.println("-------------------------------------------");
    System.out.println("Original Matrix:");
    System.out.println();
	
    int rowsCount = 17;
    int colomnsCount = 10;
		
    Random Rnd = new Random();
    int[][] matrix = new int[rowsCount][colomnsCount]; 
    for(int i=0; i<rowsCount; i++) {			
        for(int j=0; j<colomnsCount; j++) {
            matrix[i][j] = Rnd.nextInt();
            System.out.print(matrix[i][j]);
            System.out.print(" ");
        }
        System.out.println();
    }
		
    RemoteProcedure remoteProcedure = new RemoteProcedure("TransposeMatrix");		
    SequenceEncoder asnArguments = remoteProcedure.arguments;		
		
    asnArguments.Int32(rowsCount);
    asnArguments.Int32(colomnsCount);				
    SequenceOfEncoder asnRows = asnArguments.SequenceOf(UType.Sequence);
		
    for(int i=0; i<rowsCount; i++) {
        SequenceOfEncoder asnRow = asnRows.SequenceOf(UType.Integer);
        for(int j=0; j<colomnsCount; j++)
            asnRow.Int32(matrix[i][j]);
    }		
	
    Pair<Integer,Integer> attachment = new Pair<Integer,Integer>(rowsCount, colomnsCount);

    clientEndpoint.call(remoteService, remoteProcedure, new RPCResponseHandler()
    {
        @SuppressWarnings("unchecked")
        public void onSuccess(ResponseContext context, SequenceDecoder result)
        {
            try
            {				
                int origRowsCount = ((Pair<Integer,Integer>)context.attachment).First;
                int origColomnsCount = ((Pair<Integer,Integer>)context.attachment).Second;
			
                int rowsCount = result.Int32(2,32);
                int colomnsCount = result.Int32(2,32);

                if(rowsCount != origColomnsCount)					
                    throw new FormatException(String.format(
                        "The transponsed matrix has an invalid number of rows %d",
                        rowsCount));
										
                if(colomnsCount != origRowsCount)
                    throw new FormatException(String.format(
                        "The transponsed matrix has an invalid number of colomns %d", 
                        colomnsCount));
					
                System.out.println();
                System.out.println("------------------------------------------");
                System.out.println("Transponsed Matrix:");
                System.out.println();
                SequenceOfDecoder asnRows = result.SequenceOf(UType.Sequence); 
                for(int i=0; i<rowsCount; i++) {
                    SequenceOfDecoder asnRow = asnRows.SequenceOf(UType.Integer);
                    for(int j=0; j<colomnsCount; j++) {
                        System.out.print(asnRow.Int32());
                        System.out.print(" ");
                    }
                    System.out.println();						
                }					
            }
            catch(AsnException e) {
                System.out.println(e.getMessage());					
            }	
            catch(FormatException e) {
                System.out.println(e.getMessage());
            }	
        }
			
        public void onError(ResponseContext context, int errorCode, SequenceDecoder errorData) {
            try
            {
                System.out.println("The remote call to 'TransposeMatrix' failed.");
                System.out.print("Error Code: ");
                System.out.println(errorCode);
                System.out.print("Error message: ");
                System.out.println(errorData.UTF8String());			
            }
            catch(AsnException e) {
                System.out.println(e.getMessage());					
            }
        }
			
        public void onError(ResponseContext context, SoftnetException exception) {
            System.out.println("The remote call to 'TransposeMatrix' failed.");
            System.out.print("Error message: ");
            System.out.println(exception.getMessage());			
        }
    }, attachment);
}
```