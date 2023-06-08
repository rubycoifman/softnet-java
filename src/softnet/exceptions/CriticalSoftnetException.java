package softnet.exceptions;

public class CriticalSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = 4885778789360474365L;
	
	public CriticalSoftnetException(SoftnetError error) {
		super(error);
	}
	public CriticalSoftnetException(SoftnetError error, String message) {
		super(error, message);
	}
}
