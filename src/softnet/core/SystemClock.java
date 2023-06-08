package softnet.core;

public class SystemClock
{
	static long startTime;
	static
	{
		startTime = System.nanoTime();
	}

	public static long milliSeconds()
	{
		return (System.nanoTime() - startTime) / 1000000L;
	}

	public static long seconds()
	{
		return (System.nanoTime() - startTime) / 1000000000L;
	}
}
