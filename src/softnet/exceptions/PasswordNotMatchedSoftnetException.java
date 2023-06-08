package softnet.exceptions;

public class PasswordNotMatchedSoftnetException extends SoftnetException
{
	private static final long serialVersionUID = -5480331399931335846L;

	public PasswordNotMatchedSoftnetException()
	{
		super(SoftnetError.PasswordNotMatched, "The password is not matched.");
	}
}
