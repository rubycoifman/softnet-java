package softnet.discovery;

import softnet.core.*;
import softnet.utils.*;
import softnet.exceptions.*;

import java.nio.channels.SocketChannel;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.ArrayList;

public class BConnector implements STaskContext
{
	private String serverAddress;
	private ThreadPool threadPool;
	private Scheduler scheduler;
	private Acceptor<SocketChannel> resultAcceptor;
	private Acceptor<SoftnetException> errorNotificationAcceptor;	
	
	private boolean is_closed = false;
	public boolean isClosed()
	{
		return is_closed;
	}
	
	public void close()
	{
		synchronized(mutex)
		{
			is_closed = true;

			if(socketChannel1 != null)
				closeChannel(socketChannel1);

			if(socketChannel2 != null)
				closeChannel(socketChannel2);
		}
	}
	
	public BConnector(String serverAddress, ThreadPool threadPool, Scheduler scheduler)
    {
        this.serverAddress = serverAddress;     
        this.threadPool = threadPool;
        this.scheduler = scheduler;
        IPv6List = new ArrayList<InetAddress>();
		IPv4List = new ArrayList<InetAddress>();
    }
	
	public void connect(Acceptor<SocketChannel> resultAcceptor, Acceptor<SoftnetException> errorNotificationAcceptor)
	{
		this.resultAcceptor = resultAcceptor;
		this.errorNotificationAcceptor = errorNotificationAcceptor;		
		
		threadPool.execute(new Runnable()
		{
			public void run() { 
				executeDnsRequest();
			}
		});
	}
	
	private ArrayList<InetAddress> IPv6List;
	private ArrayList<InetAddress> IPv4List;

	private int attemptsCounter = 0;
	private long nextDnsRequestTime = 0;
	
	private Object mutex = new Object();	
	private SocketChannel socketChannel1 = null;
	private SocketChannel socketChannel2 = null;
	
	private void repeatDnsRequest()
	{
		if(attemptsCounter > 5)
		{
			Acceptor<Object> acceptor = new Acceptor<Object>()
			{
				public void accept(Object noData) { executeDnsRequest(); }
			};
	        ScheduledTask task = new ScheduledContextTask(acceptor, this);
	        scheduler.add(task, 60);			
		}
		else
		{
			long waitTime = 30;
			if(attemptsCounter < 5)
			{
				if(attemptsCounter == 1)
					waitTime = 2;
				else if(attemptsCounter == 2)
					waitTime = 5;
				else if(attemptsCounter == 3)
					waitTime = 10;
				else // attemptsCounter == 4
					waitTime = 20;
			}

			Acceptor<Object> acceptor = new Acceptor<Object>()
			{
				public void accept(Object noData) { executeDnsRequest(); }
			};
	        ScheduledTask task = new ScheduledContextTask(acceptor, this);
	        scheduler.add(task, waitTime);			
		}
	}
	
	private void executeDnsRequest()
	{		
		try
        {
			InetAddress[] ipList = InetAddress.getAllByName(serverAddress);
			if (ipList.length > 0)
			{
				nextDnsRequestTime = SystemClock.seconds() + 600;
				
				IPv6List.clear();
				IPv4List.clear();
				
				for(InetAddress addr: ipList)
				{
					if (addr instanceof Inet6Address)
					{
						IPv6List.add(addr);
					}
					else
					{
						IPv4List.add(addr);
					}
				}
				
				startConnectionAttempt();
			}
			else
			{
				Acceptor<Object> acceptor = new Acceptor<Object>()
				{
					public void accept(Object noData) { executeDnsRequest(); }
				};
                ScheduledTask task = new ScheduledContextTask(acceptor, this);
                scheduler.add(task, 600);

                errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(String.format("The DNS has not provided any IP address for the domain name %s", serverAddress)));
			}
        }
		catch(UnknownHostException ex)
		{
			if(attemptsCounter <= 5)
				attemptsCounter++;

			errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(String.format("Unknown host '%s'", serverAddress)));
            repeatDnsRequest();
		}		
		catch(SecurityException ex)
		{
			if(attemptsCounter <= 5)
				attemptsCounter++;

			errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(ex.getMessage()));
            repeatDnsRequest();
		}
	}

	private void repeatConnectionAttempt()
	{
		if(attemptsCounter > 5)
		{
	        if (SystemClock.seconds() < nextDnsRequestTime)
	        {
	        	Acceptor<Object> acceptor = new Acceptor<Object>()
	    		{
	    			public void accept(Object noData) { startConnectionAttempt(); }
	    		};
	    		scheduler.add(new ScheduledContextTask(acceptor, this), 60);
	        }
	        else
	        {
	        	threadPool.execute(new Runnable()
	    		{
	    			public void run() { 
	    				executeDnsRequest();
	    			}
	    		});
	        }
		}
		else
		{
			long waitTime;
			if(attemptsCounter == 1)
				waitTime = 2;
			else if(attemptsCounter == 2)
				waitTime = 5;
			else if(attemptsCounter == 3)
				waitTime = 10;
			else if(attemptsCounter == 4)
				waitTime = 20;
			else 
				waitTime = 30;
			
        	Acceptor<Object> acceptor = new Acceptor<Object>()
    		{
    			public void accept(Object noData) { startConnectionAttempt(); }
    		};
    		scheduler.add(new ScheduledContextTask(acceptor, this), waitTime);
		}		
	}	

	private void startConnectionAttempt()
	{
		if(attemptsCounter <= 5)
			attemptsCounter++;
		
		InetAddress serverIPv6 = null;
		InetAddress serverIPv4 = null;

        if (IPv6List.size() > 0)
        {
            if (IPv6List.size() == 1)
            {
            	serverIPv6 = IPv6List.get(0);
            }
            else
            {
            	serverIPv6 = IPv6List.get(Randomizer.int32(IPv6List.size()));
            }
        }

        if (IPv4List.size() > 0)
        {
            if (IPv4List.size() == 1)
            {
            	serverIPv4 = IPv4List.get(0);
            }
            else
            {
            	serverIPv4 = IPv4List.get(Randomizer.int32(IPv4List.size()));
            }
        }
        
        if (serverIPv6 != null)
        {
            if (serverIPv4 == null)
            {
   				executeConnectionAttempt(serverIPv6);
            }
            else
            {
            	startConcurrentConnectionAttempts(serverIPv6, serverIPv4);
            }
        }
        else
        {
			executeConnectionAttempt(serverIPv4);
        }
	}
		
	private void executeConnectionAttempt(InetAddress serverIP)
	{
		try
		{		
			SocketChannel socketChannel = null;
			synchronized(mutex)
			{
				if(is_closed)
					return;
				socketChannel = SocketChannel.open();
				socketChannel1 = socketChannel;
			}
		
			Acceptor<Object> acceptor = new Acceptor<Object>()
			{
				public void accept(Object state) { onConnectTimeoutExpired(state); }
			};
			final ScheduledTask timeoutControlTask = new ScheduledContextTask(acceptor, this, new Pair<SocketChannel, InetAddress>(socketChannel, serverIP));
			scheduler.add(timeoutControlTask, 18);
			
			try
			{
				socketChannel.configureBlocking(true);
				socketChannel.connect(new InetSocketAddress(serverIP, Constants.ServerPorts.Balancer));
				if(timeoutControlTask.cancel())
				{
					resultAcceptor.accept(socketChannel);
				}
			}
			catch(SecurityException ex)
			{
				if(timeoutControlTask.cancel() == false)
					return;
				
				closeChannel(socketChannel);
				errorNotificationAcceptor.accept(new SecuritySoftnetException(ex.getMessage()));
				repeatConnectionAttempt();				
			}			
			catch(IllegalArgumentException ex)
			{
				if(timeoutControlTask.cancel() == false)
					return;
				
				closeChannel(socketChannel);
				errorNotificationAcceptor.accept(new HostFunctionalitySoftnetException(ex.getMessage()));
				repeatConnectionAttempt(); 
			}
			catch(java.nio.channels.ClosedChannelException ex)
			{ }
			catch(IOException ex)
			{
				if(timeoutControlTask.cancel() == false)
					return;
				
				closeChannel(socketChannel);			
				errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(ex.getMessage()));
				repeatConnectionAttempt();    
			}
			catch(Throwable ex)
			{
				if(timeoutControlTask.cancel() == false)
					return;
				
				closeChannel(socketChannel);
				errorNotificationAcceptor.accept(new UnexpectedErrorSoftnetException(ex.getMessage()));
				repeatConnectionAttempt();   
			}
		}
		catch(IOException ex)
		{
			errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(ex.getMessage()));
			repeatConnectionAttempt();
		}
	}
	
	private void onConnectTimeoutExpired(Object state)
	{
		@SuppressWarnings("unchecked")
		Pair<SocketChannel, InetAddress> pair = (Pair<SocketChannel, InetAddress>)state;
		closeChannel(pair.First);		
		errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(String.format("No response from the remote endpoint '%s:%d'.", pair.Second.toString(), Constants.ServerPorts.Balancer)));
		repeatConnectionAttempt();
	}
	
	private void startConcurrentConnectionAttempts(InetAddress serverIPv6, InetAddress serverIPv4)
	{
		try
		{
			SocketChannel socketChannelV6 = null;
			SocketChannel socketChannelV4 = null;
			synchronized(mutex)
			{
				if(is_closed)
    				return;
				socketChannelV6 = SocketChannel.open();
				socketChannelV4 = SocketChannel.open();
				socketChannel1 = socketChannelV6;
				socketChannel2 = socketChannelV4;
			}
			
			final ConcurrentChannels concurrentChannels = new ConcurrentChannels();
			concurrentChannels.socketChannelV6 = socketChannelV6;
			concurrentChannels.socketChannelV4 = socketChannelV4;
			concurrentChannels.serverIPv6 = serverIPv6;
			concurrentChannels.serverIPv4 = serverIPv4;
			
	        Acceptor<Object> acceptor = new Acceptor<Object>()
			{
				public void accept(Object state) { onConcurrentConnectTimeoutExpired(state); }
			};
			concurrentChannels.timeoutControlTask = new ScheduledContextTask(acceptor, this, concurrentChannels);
			scheduler.add(concurrentChannels.timeoutControlTask, 18);
	        	        
    		threadPool.execute(new Runnable()
    		{
    			public void run() { 
    				executeConnectionAttemptV6(concurrentChannels);
    			}
    		});

    		threadPool.execute(new Runnable()
    		{
    			public void run() { 
    				executeConnectionAttemptV4(concurrentChannels);
    			}
    		});
		}
		catch(IOException ex)
		{
			errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(ex.getMessage()));
			repeatConnectionAttempt();
		}
	}
	
	private void executeConnectionAttemptV6(ConcurrentChannels concurrentChannels)
	{
		try
		{
			concurrentChannels.socketChannelV6.configureBlocking(true);
			concurrentChannels.socketChannelV6.connect(new InetSocketAddress(concurrentChannels.serverIPv6, Constants.ServerPorts.Balancer));
			if(concurrentChannels.timeoutControlTask.cancel() == false)
				return;

			resultAcceptor.accept(concurrentChannels.socketChannelV6);				
		}		
		catch(Throwable ex)
		{
			synchronized (concurrentChannels)
	        {
	            if (concurrentChannels.completed)
	                return;

	    		closeChannel(concurrentChannels.socketChannelV6);
	    		concurrentChannels.socketChannelV6 = null;
	    		concurrentChannels.exceptionV6 = ex;

	            if (concurrentChannels.socketChannelV4 != null)
	                return;

	            concurrentChannels.completed = true;
	        }
			concurrentChannels.timeoutControlTask.cancel();

			errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(BuildErrorMessage(concurrentChannels)));
			repeatConnectionAttempt();       
		}
	}
	
	private void executeConnectionAttemptV4(ConcurrentChannels concurrentChannels)
	{
		try
		{
			concurrentChannels.socketChannelV4.configureBlocking(true);
			concurrentChannels.socketChannelV4.connect(new InetSocketAddress(concurrentChannels.serverIPv4, Constants.ServerPorts.Balancer));
			if(concurrentChannels.timeoutControlTask.cancel() == false)
				return;

			resultAcceptor.accept(concurrentChannels.socketChannelV4);				
		}		
		catch(Throwable ex)
		{
			synchronized (concurrentChannels)
	        {
	            if (concurrentChannels.completed)
	                return;

	    		closeChannel(concurrentChannels.socketChannelV4);
	    		concurrentChannels.socketChannelV4 = null;
	    		concurrentChannels.exceptionV4 = ex;

	            if (concurrentChannels.socketChannelV6 != null)
	                return;

	            concurrentChannels.completed = true;
	        }
			concurrentChannels.timeoutControlTask.cancel();

			errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(BuildErrorMessage(concurrentChannels)));
			repeatConnectionAttempt();
		}
	}
			
	private void onConcurrentConnectTimeoutExpired(Object state)
	{
		ConcurrentChannels concurrentChannels = (ConcurrentChannels)state;

		synchronized (concurrentChannels)
        {
            if (concurrentChannels.completed)
                return;
            concurrentChannels.completed = true;
        }

        if (concurrentChannels.socketChannelV6 != null)
        	closeChannel(concurrentChannels.socketChannelV6);

        if (concurrentChannels.socketChannelV4 != null)
        	closeChannel(concurrentChannels.socketChannelV4);

        errorNotificationAcceptor.accept(new NetworkErrorSoftnetException(BuildErrorMessage(concurrentChannels)));
		repeatConnectionAttempt();
	}
	
    private String BuildErrorMessage(ConcurrentChannels cc)
    {
        String IPv6_Error = cc.exceptionV6 != null ? cc.exceptionV6.getMessage() : String.format("No response from the remote endpoint '%s:%d'", cc.serverIPv6.toString(), Constants.ServerPorts.Balancer);
        String IPv4_Error = cc.exceptionV4 != null ? cc.exceptionV4.getMessage() : String.format("No response from the remote endpoint '%s:%d'", cc.serverIPv4.toString(), Constants.ServerPorts.Balancer);

        return String.format("IPv6 error: %s. IPv4 error: %s", IPv6_Error, IPv4_Error);
    }
	
	private void closeChannel(SocketChannel socketChannel)
	{
		try { socketChannel.close(); } catch(IOException ex) { }
	}
}

class ConcurrentChannels
{
    public SocketChannel socketChannelV6 = null;
    public InetAddress serverIPv6 = null;
    public Throwable exceptionV6 = null;

    public SocketChannel socketChannelV4 = null;
    public InetAddress serverIPv4 = null;
    public Throwable exceptionV4 = null;

    public boolean completed = false;
    public ScheduledTask timeoutControlTask;
}
