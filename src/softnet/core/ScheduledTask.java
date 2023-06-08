package softnet.core;

import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledTask
{
	public final Acceptor<Object> acceptor;
    public final Object state;
    
	public ScheduledTask(Acceptor<Object> acceptor, Object state)
	{
		this.acceptor = acceptor;
		this.state = state;
		completed = new AtomicInteger(0);
	}

	protected AtomicInteger completed;

	public boolean cancel()
    {	
		return completed.compareAndSet(0, 1);
    }

    public boolean complete()
    {    	
    	return completed.compareAndSet(0, 1);
    }
}
