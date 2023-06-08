package softnet.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPool
{
	private ExecutorService cachedThreadPool;
	private boolean shutdown = false;
	
	public void init()
	{
		cachedThreadPool = Executors.newCachedThreadPool();
	}
	
	public void execute(Runnable runnable)
	{
		try
		{
			cachedThreadPool.execute(runnable);
		}
		catch(java.util.concurrent.RejectedExecutionException e)
		{
			if(shutdown == true)
				return;
			throw e;
		}		
	}
	
	public void shutdown()
	{
		shutdown = true;
		cachedThreadPool.shutdownNow();
	}	
}
