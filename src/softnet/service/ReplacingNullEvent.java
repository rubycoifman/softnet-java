package softnet.service;

public class ReplacingNullEvent extends ReplacingEvent 
{
	public ReplacingNullEvent(String name)
	{
		super(name, true);
	}
}
