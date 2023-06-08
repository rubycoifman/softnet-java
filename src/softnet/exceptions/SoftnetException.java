package softnet.exceptions;

public class SoftnetException extends java.lang.Exception
{
	private static final long serialVersionUID = -1174278860322623465L;
	
    public final SoftnetError Error;

    public SoftnetException(SoftnetError error)
    {
        Error = error;
    }

    public SoftnetException(SoftnetError error, String message)        
    {
    	super(message);
        Error = error;
    }
}