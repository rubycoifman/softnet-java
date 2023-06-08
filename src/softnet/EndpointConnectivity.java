package softnet;

import softnet.exceptions.*;

public class EndpointConnectivity
{
	public final ConnectivityStatus status;
    public final SoftnetError error;
    public final String message;
    
    public EndpointConnectivity(ConnectivityStatus status)
    {
    	this.status = status;
        this.error = SoftnetError.NoError;
        this.message = null;
    }
    
    public EndpointConnectivity(ConnectivityStatus status, SoftnetError error, String message)
    {
    	this.status = status;
    	this.error = error;
    	this.message = message;
    }
    
    public EndpointConnectivity(ConnectivityStatus status, SoftnetException exception)
    {
    	this.status = status;
    	this.error = exception.Error;
    	this.message = exception.getMessage();
    }
}
