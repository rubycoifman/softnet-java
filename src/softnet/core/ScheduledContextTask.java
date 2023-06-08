package softnet.core;

public class ScheduledContextTask extends ScheduledTask
{
	public ScheduledContextTask(Acceptor<Object> acceptor, STaskContext context)
	{
		super(acceptor, null);
		this.context = context;
	}
	
	public ScheduledContextTask(Acceptor<Object> acceptor, STaskContext context, Object state)
	{
		super(acceptor, state);
		this.context = context;
	}
	
	private STaskContext context = null;

	@Override
	public boolean cancel()
    {	
		if (this.context.isClosed())
            return false;
		return super.completed.compareAndSet(0, 1);
    }

	@Override
    public boolean complete()
    {    	
        if (this.context.isClosed())
            return false;
        return super.completed.compareAndSet(0, 1);
    }
}
