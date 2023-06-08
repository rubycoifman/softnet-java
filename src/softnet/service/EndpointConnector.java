package softnet.service;

import softnet.asn.*;
import softnet.*;
import softnet.core.*;
import softnet.discovery.*;
import softnet.exceptions.*;
import softnet.utils.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

class EndpointConnector 
{    
	public Acceptor<EndpointConnectivity> connectivityEventCallback;
	public Runnable closedCallback;
	public Runnable disconnectedEventCallback;
	public Acceptor<Channel> connectedEventCallback;
	
    public EndpointConnector(ServiceURI serviceURI, String password, Object endpoint_mutex, ServiceEndpoint softnetService)
    {
        this.serviceURI = serviceURI;
        this.password = password;  
        this.mutex = endpoint_mutex;
        this.threadPool = softnetService.threadPool;
        this.scheduler = softnetService.scheduler;
        channelMonitor = new ChannelMonitor();
    }
        
    public void Connect()
    {
    	synchronized(mutex)
    	{
    		if(isActive || isClosed)
    			return;
    		isActive = true;
    		connectionAttemptNumber = 0;
    	}    	
		requestFS();
    }
    
    public void Disconnect()
    {
    	synchronized(mutex)
    	{
    		if(isActive == false || isClosed)
    			return;
    		isActive = false;
    		
    		if (isConnected)
            {
    			isConnected = false;
                channelMonitor.onChannelDisconnected();
                disconnectedEventCallback.run();
            }
    		connectivityEventCallback.accept(new EndpointConnectivity(ConnectivityStatus.Disconnected));    		
    		
    		if(fsClient != null)
    		{
    			fsClient.close();
    			fsClient = null;
    		}
    		
    		if(endpointChannel != null)
    		{
    			endpointChannel.close();
    			endpointChannel = null;
    		}
    	}
    }    

    public void Close()
    {
    	synchronized(mutex)
    	{
    		if(isClosed)
    			return;    		    		
    		isClosed = true;    		
    		isActive = false;   
    		
    		channelMonitor.close();
    		
    		if(fsClient != null)
    			fsClient.close();
    		
    		if(endpointChannel != null)
    			endpointChannel.close();
    		
    		closedCallback.run();
    	}
    }    
    
    public void onEndpointInstalled()
    {
    	connectionAttemptNumber = 0;
    	channelMonitor.onEndpointInstalled();
    }

    public void setRemotePingPeriod(int period)
    {
		if(!(period == 0 || (10 <= period && period <= 300)))
			return;
		channelMonitor.setRemotePingPeriod(period);
    }

    public void setLocalPingPeriod(int period)
    {
		if(!(period == 0 || (10 <= period && period <= 300)))
			throw new IllegalArgumentException("The value of the channel's ping period is illegal.");
		
		synchronized(mutex)
		{
			channelMonitor.setLocalPingPeriod(period);
		}
    }
    
    private ServiceURI serviceURI;
    private String password;    
    private ThreadPool threadPool;
    private Scheduler scheduler;

    private Object mutex = new Object();    
    private boolean isClosed = false;
    private boolean isActive = false;
    private BClient fsClient = null;
    private EndpointChannel endpointChannel = null;
    private ChannelMonitor channelMonitor;
    private byte[] channelId = null;
    private boolean isConnected = false;
    private int connectionAttemptNumber;

    private void repeatConnectionAttempt(SoftnetError error)
    {
    	if(error == SoftnetError.NetworkError || error == SoftnetError.RestartDemanded)
    	{
    		if(connectionAttemptNumber <= 6)
    		{
    			long waitTime = 40;
    			if(connectionAttemptNumber == 0)
					waitTime = 1;
    			else if(connectionAttemptNumber == 1)
					waitTime = 1;
				else if(connectionAttemptNumber == 2)
					waitTime = 2;
				else if(connectionAttemptNumber == 3)
					waitTime = 5;
				else if(connectionAttemptNumber == 4)
					waitTime = 10;
				else if(connectionAttemptNumber == 5)
					waitTime = 20;
    			
				Acceptor<Object> acceptor = new Acceptor<Object>()
				{
					public void accept(Object noData) { requestFS(); }
				};
				scheduler.add(new ScheduledTask(acceptor, null), waitTime);
    		}
    		else
    		{
    			Acceptor<Object> acceptor = new Acceptor<Object>()
				{
					public void accept(Object noData) { requestFS(); }
				};
				scheduler.add(new ScheduledTask(acceptor, null), 60);
    		}    		
    	}
    	else
    	{
    		Acceptor<Object> acceptor = new Acceptor<Object>()
			{
				public void accept(Object noData) { requestFS(); }
			};
			scheduler.add(new ScheduledTask(acceptor, null), 60);
    	}
    }
    
    private void requestFS()
    {
    	final BClient newFSClient = new BClient(serviceURI.server, new QueryOnServiceUid(serviceURI), threadPool, scheduler);
    	synchronized(mutex)
        {
            if (isActive == false || fsClient != null)
            	return;
            fsClient = newFSClient;            
    		connectivityEventCallback.accept(new EndpointConnectivity(ConnectivityStatus.AttemptToConnect));
    		
            if(connectionAttemptNumber <= 10)
            	connectionAttemptNumber++;
        }
    	
    	newFSClient.request(
			new ResponseHandler<InetAddress, SoftnetException>()
    		{
    			private AtomicInteger mutex = new AtomicInteger(0);
    			private BClient client = newFSClient;    
    			
    			@Override
				public void onSuccess(InetAddress result)
				{
					if(mutex.compareAndSet(0, 1))
					{
						FSClient_onSuccess(result, client);
					}
				}
    			
				@Override
				public void onError(SoftnetException ex)
				{
					if(mutex.compareAndSet(0, 1))
					{
						FSClient_onCriticalError(ex, client);
					}
				}
    		},
    		new Acceptor<SoftnetException>()
    		{
    			private BClient client = newFSClient;
    			
				@Override
				public void accept(SoftnetException exception)
				{
					FSClient_onErrorNotification(exception, client);
				}
    		});
    }

    private void FSClient_onSuccess(InetAddress trackerIP, BClient caller)
    {
    	EndpointChannel newChannel = null;
    	try
    	{
    		newChannel = new ClearChannel(); 
    		newChannel.connect(trackerIP);

    		synchronized(mutex)
	        {
	            if (caller.isClosed())
	            {
	            	newChannel.close();
	            	return;
	            }
	            
	            fsClient = null;
	            endpointChannel = newChannel;	            
	        }    		
    		
    		newChannel.start();
    	}
    	catch(NetworkErrorSoftnetException ex)
    	{
    		if(newChannel != null)
    			newChannel.close();    		
    		connectivityEventCallback.accept(new EndpointConnectivity(ConnectivityStatus.AttemptToConnect, ex));	
    		repeatConnectionAttempt(ex.Error);
    	}
    }

    private void FSClient_onErrorNotification(SoftnetException ex, BClient caller)
    {
    	synchronized(mutex)
        {
            if (caller.isClosed())
            	return;
            connectivityEventCallback.accept(new EndpointConnectivity(ConnectivityStatus.AttemptToConnect, (SoftnetException)ex));
        }
    }

    private void FSClient_onCriticalError(SoftnetException ex, BClient caller)
    {
    	synchronized(mutex)
    	{
    		if (caller.isClosed())
    			return;
    		fsClient = null;
    		connectivityEventCallback.accept(new EndpointConnectivity(ConnectivityStatus.Down, ex));
    	}
    }
    
	private void Channel_onEstablished(EndpointChannel caller)
    {		
    	synchronized(mutex)
    	{
    		if(caller.isClosed()) 
    			return;    		
    		
    		isConnected = true;
            channelMonitor.onChannelConnected(caller);
            connectedEventCallback.accept(caller);
            connectivityEventCallback.accept(new EndpointConnectivity(ConnectivityStatus.Connected));
    	}
    }
    
    private void Channel_onError(EndpointChannel caller, SoftnetException ex)
    {
    	synchronized(mutex)
    	{
    		if(caller.isClosed()) 
    			return;
    		
    		caller.close();
    		endpointChannel = null;
            
            if (isConnected)
            {
            	isConnected = false;
                channelMonitor.onChannelDisconnected();
                disconnectedEventCallback.run();
                connectivityEventCallback.accept(new EndpointConnectivity(ConnectivityStatus.Disconnected, ex));
            }
            else
            {
            	connectivityEventCallback.accept(new EndpointConnectivity(ConnectivityStatus.AttemptToConnect, ex));
            }
    	}
    	
    	repeatConnectionAttempt(ex.Error);
    }

    private void Channel_onCriticalError(EndpointChannel caller, SoftnetException ex)
    {
    	synchronized(mutex)
    	{
    		if(caller.isClosed()) 
    			return;
    		
    		caller.close();
    		endpointChannel = null;

            if (isConnected)
            {
            	isConnected = false;
                channelMonitor.onChannelDisconnected();
                disconnectedEventCallback.run();
            }

            connectivityEventCallback.accept(new EndpointConnectivity(ConnectivityStatus.Down, ex));
    	}
    }
    
    private class ChannelMonitor
    {
    	public ChannelMonitor()
    	{
    		channel = null;
    		pingContext = null;
    		keepAliveContext = null;
    		ping_period_remote = 0;
    		ping_period_local = 0;
    		ping_period = 300;
    		ping_sent_time = 0;
    		isPingSent = false;
    		isEndpointInstalled = false;
    	}
    	
    	public void close()
    	{
    		if(pingContext != null)
    			pingContext.close();    		
    		if(keepAliveContext != null)
    			keepAliveContext.close();
    	}
    	
    	public void onChannelConnected(Channel channel)
    	{
    		channel.registerComponent(Constants.Service.ChannelMonitor.ModuleId, 
    			new MsgAcceptor<Channel>()
    			{
    				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
    				{
    					onMessageReceived(message, _channel);
    				}
    			});
    		this.channel = (EndpointChannel)channel;    		    		
    	
    		ping_period_remote = 0;

    		if(ping_period_local == 0)
    			ping_period = 300;
    		else
    			ping_period = ping_period_local;
    	}
    	
    	public void onChannelDisconnected()
    	{
    		isEndpointInstalled = false;    	
    		channel = null;    		
    		
    		if(pingContext != null)
    			pingContext.close();
    		pingContext = null;
    		
    		if(keepAliveContext != null)
    			keepAliveContext.close();
    		keepAliveContext = null;    		
    	}
    	
    	public void onEndpointInstalled()
    	{
    		isEndpointInstalled = true;
    		
    		pingContext = new PingContext();
    		isPingSent = false;    		
    		Acceptor<Object> acceptor = new Acceptor<Object>()
			{
				public void accept(Object data) { executePing(data); }
			};    	    		
			ScheduledContextTask task = new ScheduledContextTask(acceptor, pingContext, pingContext);    		
			scheduler.add(task, ping_period);    	
    	}

    	public void setRemotePingPeriod(int period)
    	{
			ping_period_remote = period;

			if(ping_period_remote == 0)
			{
				if(ping_period_local == 0)
					ping_period = 300;
				else
					ping_period = ping_period_local;
			}
			else
				ping_period = ping_period_remote;
			
			if(isEndpointInstalled == false)
				return;
    		
			resetPing();
    	}
    	    	
    	public void setLocalPingPeriod(int period)
    	{
			ping_period_local = period;

			if(ping_period_remote > 0)
				return;
			
			if(ping_period_local > 0)
				ping_period = ping_period_local;
			else
				ping_period = 300;	
			
			if(isEndpointInstalled == false)
				return;
			
			resetPing();
    	}
    	
    	private void resetPing()
    	{    		
    		if(pingContext != null)
    			pingContext.close();
    		
    		pingContext = new PingContext();
			Acceptor<Object> acceptor = new Acceptor<Object>()
			{
				public void accept(Object data) { executePing(data); }
			};    	    		
			ScheduledContextTask task = new ScheduledContextTask(acceptor, pingContext, pingContext); 
    		
			if(isPingSent)
			{
				long last_input_message_time = channel.getLastInputMessageTime();
				long current_time = SystemClock.seconds();
				if(last_input_message_time > current_time)
					last_input_message_time = current_time;
				
				if(last_input_message_time >= ping_sent_time)
				{
					long next_ping_time = last_input_message_time + ping_period;				
					if((next_ping_time - 3) <= current_time)
					{				
						channel.send(MsgBuilder.Create(Constants.Service.ChannelMonitor.ModuleId, Constants.Service.ChannelMonitor.PING));                    			                    			
            			ping_sent_time = current_time;
            			
						if(ping_period > 60)
							scheduler.add(task, 60);
            			else
            				scheduler.add(task, ping_period);
					}
					else
					{
						isPingSent = false;
						scheduler.add(task, next_ping_time - current_time);
					}
				}
				else
				{
					if(ping_period > 60)
					{
						long pong_check_time = ping_sent_time + 60;
						if(pong_check_time <= current_time)
						{
							final EndpointChannel f_channel = channel;
							threadPool.execute(new Runnable()
	    					{
	    						public void run() 
	    						{ 
	    							Channel_onError(f_channel, new NetworkErrorSoftnetException("The softnet endpoint has lost the connection.")); 
	    						}
	    					});
						}
						else
						{
							scheduler.add(task, pong_check_time - current_time);
						}
					}
					else
					{
						long pong_check_time = ping_sent_time + ping_period;						
						if(pong_check_time <= current_time)
						{
							final EndpointChannel f_channel = channel;
							threadPool.execute(new Runnable()
	    					{
	    						public void run() 
	    						{ 
	    							Channel_onError(f_channel, new NetworkErrorSoftnetException("The softnet endpoint has lost the connection.")); 
	    						}
	    					});
						}
						else
						{
							scheduler.add(task, pong_check_time - current_time);
						}
					}
				}
			}
			else
			{
				long last_input_message_time = channel.getLastInputMessageTime();
				long current_time = SystemClock.seconds();
				if(last_input_message_time > current_time)
					last_input_message_time = current_time;
				
				long ping_time = last_input_message_time + ping_period;
				if((ping_time - 3) <= current_time)
				{
					channel.send(MsgBuilder.Create(Constants.Service.ChannelMonitor.ModuleId, Constants.Service.ChannelMonitor.PING));                    			                    			
        			isPingSent = true;
        			ping_sent_time = current_time;
        			
					if(ping_period >= 60)
						scheduler.add(task, 60);
        			else
        				scheduler.add(task, ping_period);
				}
				else
				{
					scheduler.add(task, ping_time - current_time);
				}
			}
    	}
    	    	
    	private void executePing(Object data)
    	{
    		PingContext context = (PingContext)data;
    		synchronized(mutex)
    		{
    			if(context.isClosed())
    				return;
    			context.close();
    			
    			pingContext = new PingContext();    			
				Acceptor<Object> acceptor = new Acceptor<Object>()
				{
					public void accept(Object data) { executePing(data); }
				};    	    		
				ScheduledContextTask task = new ScheduledContextTask(acceptor, pingContext, pingContext);    					
    			
    			if(isPingSent)
    			{
    				long last_input_message_time = channel.getLastInputMessageTime();     				
    				if(last_input_message_time >= ping_sent_time)
    				{
    					long current_time = SystemClock.seconds();
                		long last_input_message_age = current_time - last_input_message_time;
                		
                		if(last_input_message_age < 0)
                			last_input_message_age = 0;
                		else if(last_input_message_age > ping_period)
                			last_input_message_age = ping_period;

                		if((ping_period - last_input_message_age) <= 3)
                		{
            				channel.send(MsgBuilder.Create(Constants.Service.ChannelMonitor.ModuleId, Constants.Service.ChannelMonitor.PING));
                			ping_sent_time = current_time;

                			if(ping_period >= 60)
                				scheduler.add(task, 60);
                			else
                				scheduler.add(task, ping_period);
                		}
                		else
                		{
            				isPingSent = false;
            				scheduler.add(task, ping_period - last_input_message_age);
                		}
    				}
    				else
    				{
    					final EndpointChannel f_channel = channel;
    					threadPool.execute(new Runnable()
    					{
    						public void run() 
    						{ 
    							Channel_onError(f_channel, new NetworkErrorSoftnetException("The softnet endpoint has lost the connection.")); 
    						}
    					});
    				}
    			}
    			else
    			{
    				long last_input_message_time = channel.getLastInputMessageTime();
    				long current_time = SystemClock.seconds();			            		
    				long last_input_message_age = current_time - last_input_message_time;
    				
            		if (last_input_message_age < 0)
            			last_input_message_age = 0;            		
            		else if(last_input_message_age > ping_period)
            			last_input_message_age = ping_period;
            		
            		if((ping_period - last_input_message_age) <= 3)
            		{
        				channel.send(MsgBuilder.Create(Constants.Service.ChannelMonitor.ModuleId, Constants.Service.ChannelMonitor.PING));                    			                    			
            			ping_sent_time = current_time;
            			isPingSent = true;

            			if(ping_period >= 60)
            				scheduler.add(task, 60);
            			else
            				scheduler.add(task, ping_period);
            		}
            		else
            		{
            			long waitTime = ping_period - last_input_message_age; // waitTime >= 4 && waitTime <= 300
            			scheduler.add(task, waitTime);
            			
            			long last_output_message_age = current_time - channel.getLastOutputMessageTime();
            			if(last_output_message_age < 0)
            				last_output_message_age = 0;
            			else if(last_output_message_age > 300)
            				last_output_message_age = 300;
            			            			
            			if(last_output_message_age + waitTime > 303)
            			{
            				if(keepAliveContext != null)
            				{
            					keepAliveContext.close();
            					keepAliveContext = null;
            				}
            			
            				if(last_output_message_age >= 297)
            				{
            					channel.send(MsgBuilder.Create(Constants.Service.ChannelMonitor.ModuleId, Constants.Service.ChannelMonitor.KEEP_ALIVE));
            				}
            				else
            				{
	            				keepAliveContext = new KeepAliveContext();
	            				Acceptor<Object> acceptor2 = new Acceptor<Object>()
	            				{
	            					public void accept(Object data) { executeKeepAlive(data); }
	            				};    	    		
	            				ScheduledContextTask task2 = new ScheduledContextTask(acceptor2, keepAliveContext, keepAliveContext);	            				
	            				scheduler.add(task2, 300 - last_output_message_age);           
            				}
            			}
            		}
    			}
    		}
    	}
    	
    	private void executeKeepAlive(Object data)
    	{
    		KeepAliveContext context = (KeepAliveContext)data;
    		synchronized(mutex)
    		{
    			if(context.isClosed())
    				return;
    			keepAliveContext = null;
    			
				long elapsed_time = SystemClock.seconds() - channel.getLastOutputMessageTime();
				if(elapsed_time >= 300)
				{
					channel.send(MsgBuilder.Create(Constants.Service.ChannelMonitor.ModuleId, Constants.Service.ChannelMonitor.KEEP_ALIVE));	
				}
    		}
    	}
    	
    	private EndpointChannel channel;
    	private KeepAliveContext keepAliveContext;
    	private PingContext pingContext;    	
    	private long ping_period_remote;
    	private long ping_period_local;
    	private long ping_period;
    	private long ping_sent_time;
    	private boolean isPingSent;
    	private boolean isEndpointInstalled;
    	
    	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
    	{
    		synchronized(mutex)
    		{
    			if(channel.isClosed())
    				return;
    			
    			if(message[1] == Constants.Service.ChannelMonitor.PONG)
    				return;    				
    			
   				throw new FormatException();
    		}
    	}
    	
    	private class PingContext implements STaskContext
    	{
    		private boolean is_closed = false;    		
    		public boolean isClosed()
    		{
    			return is_closed;
    		}    		
    		public void close()
    		{
    			is_closed = true;
    		}
    	}
    	
    	private class KeepAliveContext implements STaskContext
    	{
    		private boolean is_closed = false;    		
    		public boolean isClosed()
    		{
    			return is_closed;
    		}    		
    		public void close()
    		{
    			is_closed = true;
    		}
    	}
    }
    
    private interface EndpointChannel extends Channel
    {    	
    	void connect(InetAddress trackerIP) throws NetworkErrorSoftnetException;
    	void start();
    	void close();
    	long getLastInputMessageTime();
    	long getLastOutputMessageTime();
    }
    
    private class ClearChannel implements EndpointChannel, STaskContext
    {
    	@SuppressWarnings("unchecked")
		public ClearChannel() throws NetworkErrorSoftnetException 
    	{
    		try
    		{    			    		
	    		msgSocket = new MsgSocket(SocketChannel.open());
				components = new MsgAcceptor[16];
				last_input_message_time = 0;
				last_output_message_time = 0;				
    		}
    		catch(IOException ex)
    		{
    			throw new NetworkErrorSoftnetException(ex.getMessage());
    		}
    	}
    	
    	// --------- EndpointChannel interface implementation -------------------------------------

    	public void connect(InetAddress trackerIP) throws NetworkErrorSoftnetException
    	{		    		
    		try
    		{
    			SocketChannel socketChannel = msgSocket.getChannel();
    			socketChannel.configureBlocking(true);
				socketChannel.connect(new InetSocketAddress(trackerIP, Constants.ServerPorts.Tracker));	  				
    		}
			catch(IOException ex)
    		{
    			throw new NetworkErrorSoftnetException(ex.getMessage());
    		}
    	}
    	
    	public void start()
    	{
    		components[Constants.Service.Channel.ModuleId] = new MsgAcceptor<Channel>()
    				{
    					@Override
    					public void accept(byte[] message, Channel channel)
    					{
    						onChannelMessageReceived(message);
    					}
    				};		
    				
    		msgSocket.messageReceivedHandler = new Acceptor<byte[]>()
			{
				public void accept(byte[] message) { onHandshakeMessageReceived(message); }
			};
			msgSocket.networkErrorHandler = new Acceptor<NetworkErrorSoftnetException>()
			{
				public void accept(NetworkErrorSoftnetException ex) { onNetworkError(ex); }
			};
			msgSocket.formatErrorHandler = new Runnable()
			{
				public void run() { onFormatError(); }
			};		
			msgSocket.minLength = 2;
			msgSocket.maxLength = 256;	
			msgSocket.start();
			
			startHandshake();
    	}
    	
    	public long getLastInputMessageTime()
    	{
    		return last_input_message_time;
    	}
    	
    	public long getLastOutputMessageTime()
    	{
    		return last_output_message_time;
    	}

    	public void close()
    	{
    		is_closed = true;
			msgSocket.close();
    	}

    	// ---------- Channel interface implementation -------------------------------------
    	public void send(SoftnetMessage message)
    	{
    		msgSocket.send(message);
    		last_output_message_time = SystemClock.seconds();
    	}

    	public void registerComponent(int componentId, MsgAcceptor<Channel> MessageReceivedCallback)
    	{
    		components[componentId] = MessageReceivedCallback;
    	}
    	
    	public void removeComponent(int componentId)
    	{
    		components[componentId] = null;
    	}
    	
    	private boolean is_closed = false;
    	public boolean isClosed()
    	{
    		return is_closed;
    	}
    	
    	// -----------------------------------------------------------------------
    	
		private MsgAcceptor<Channel>[] components;    	
    	private MsgSocket msgSocket;
    	private long last_input_message_time;    	
    	private long last_output_message_time;  
    	
    	private void onMessageReceived(byte[] message)
    	{
    		 try
             {
    			 /*
    			 System.out.println("message: " + message[0] + " " + message[1]);
    			 
    			 for(byte b: message)
    			 {
    				 System.out.print(String.format("%02X ", b));
    			 }	 
    			 System.out.println();
    			 */
    			 
    			 last_input_message_time = SystemClock.seconds();
                 int componentId = message[0];
                 if(0 <= componentId && componentId < components.length && components[componentId] != null)
                 {
                	 components[componentId].accept(message, this);
                 }
                 else
                 {
                	 Channel_onError(this, new InputDataFormatSoftnetException());
                 }
             }
             catch (AsnException | FormatException e)
             {
            	 Channel_onError(this, new InputDataFormatSoftnetException());
             }
    		 catch(HostFunctionalitySoftnetException e)
    		 {
    			 Channel_onCriticalError(this, e);
    		 }
    		 catch(SoftnetException e)
    		 {
    			 Channel_onError(this, e);
    		 }
    	}    	
    	
    	private void onChannelMessageReceived(byte[] message)
    	{
			if (message[1] == Constants.Service.Channel.ERROR)
            {
				ProcessMessage_Error(message);
            }
            else
            {
            	Channel_onError(this, new InputDataFormatSoftnetException());
         	}
    	}    
    	
    	private void onNetworkError(NetworkErrorSoftnetException ex)
    	{
    		Channel_onError(this, ex);
    	}

    	private void onFormatError()
    	{
    		Channel_onError(this, new InputDataFormatSoftnetException());
    	}
    	    	
    	private void ProcessMessage_Error(byte[] message)
    	{
    		if(message.length != 4)
    		{
    			Channel_onError(this, new InputDataFormatSoftnetException());
    			return;
    		}    		
            int errorCode = ByteConverter.toInt32FromInt16(message, 2);            
            
            if (errorCode == ErrorCodes.RESTART_DEMANDED)
            {
            	Channel_onError(this, new RestartDemandedSoftnetException());
            }
            else if(errorCode == ErrorCodes.PASSWORD_NOT_MATCHED)
    		{
            	Channel_onCriticalError(this, new PasswordNotMatchedSoftnetException());
    		}
    		else if (errorCode == ErrorCodes.DUPLICATED_SERVICE_UID_USAGE)
            {
    			Channel_onCriticalError(this, new DublicatedServiceUidUsageSoftnetException(serviceURI.serviceUid));
            }
    		else if (errorCode == ErrorCodes.SERVER_DBMS_ERROR)
            {
    			Channel_onError(this, new ServerDbmsErrorSoftnetException());
            }
    		else if (errorCode == ErrorCodes.SERVER_DATA_INTEGRITY_ERROR)
            {
    			Channel_onError(this, new ServerDataIntegritySoftnetException());
            }
    		else if (errorCode == ErrorCodes.SERVER_CONFIG_ERROR)
            {
    			Channel_onError(this, new ServerConfigErrorSoftnetException());
            }
    		else if (errorCode == ErrorCodes.SERVER_BUSY)
            {
    			Channel_onError(this, new ServerBusySoftnetException());
            }
    		else if (errorCode == ErrorCodes.INCOMPATIBLE_PROTOCOL_VERSION)
            {
    			Channel_onError(this, new IncompatibleProtocolVersionSoftnetException());
            }
    		else if (errorCode == ErrorCodes.ENDPOINT_DATA_FORMAT_ERROR)
            {
    			Channel_onError(this, new EndpointDataFormatSoftnetException());
            }
    		else if (errorCode == ErrorCodes.ENDPOINT_DATA_INCONSISTENT)
            {
    			Channel_onError(this, new EndpointDataInconsistentSoftnetException());
            }
    		else if (errorCode == ErrorCodes.SERVICE_NOT_REGISTERED)
            {
    			Channel_onError(this, new ServiceNotRegisteredSoftnetException(serviceURI.serviceUid));
            }    		
    		else
    		{
    			Channel_onError(this, new UnexpectedErrorSoftnetException(errorCode));
    		}
    	}
    	
    	// handshake region --------------------------------------------------------------------------------------------------------
    	
    	private class HandshakeData
        {
            byte[] Salt;
            byte[] SecurityKey1;
            byte[] SecurityKey2;
        }
    	private HandshakeData handshakeData;

    	private class HandshakePhase
        {
            final static int PHASE_1 = 1;
            final static int PHASE_2 = 2;
        }
    	private int handshakePhase = HandshakePhase.PHASE_1;
    	
    	private void startHandshake()
    	{
    		handshakeData = new HandshakeData();
    				
			if (channelId != null)
            {
				ASNEncoder asnEncoder = new ASNEncoder();
	            SequenceEncoder sequence = asnEncoder.Sequence();
	            sequence.OctetString(serviceURI.serviceUid);
	            sequence.OctetString(channelId);
	            
	            SoftnetMessage message = MsgBuilder.Create(Constants.Service.Channel.ModuleId, Constants.Service.Channel.RESTORE, asnEncoder, 2);	            
				message.buffer[message.offset] = Constants.ProtocolVersion;
                message.buffer[message.offset + 1] = Constants.Service.EndpointType;
                                
                msgSocket.send(message.buffer, message.offset, message.length);
            }
			else
			{
                ASNEncoder asnEncoder = new ASNEncoder();
                SequenceEncoder sequence = asnEncoder.Sequence();            
                sequence.OctetString(serviceURI.serviceUid);            
                
                SoftnetMessage message = MsgBuilder.Create(Constants.Service.Channel.ModuleId, Constants.Service.Channel.OPEN, asnEncoder, 2);	            
				message.buffer[message.offset] = Constants.ProtocolVersion;
                message.buffer[message.offset + 1] = Constants.Service.EndpointType;
                
                msgSocket.send(message.buffer, message.offset, message.length);
			}
    	}
    	
    	private void ProcessMessage_SaltAndKey1(byte[] message) throws AsnException, HostFunctionalitySoftnetException
    	{
    		SequenceDecoder sequence = ASNDecoder.Sequence(message, 2);            
    		handshakeData.Salt = sequence.OctetString(16, 20);
    		handshakeData.SecurityKey1 = sequence.OctetString(20);
            sequence.end();
            
            handshakeData.SecurityKey2 = Randomizer.octetString(20);
            byte[] passwordHash = PasswordHash.compute(handshakeData.SecurityKey1, handshakeData.SecurityKey2, handshakeData.Salt, password);    					
            SoftnetMessage message2 = EncodeMessage_HashAndKey2(passwordHash, handshakeData.SecurityKey2);

            handshakePhase = HandshakePhase.PHASE_2;
            msgSocket.send(message2);
    	}
    	
    	private void ProcessMessage_OpenOk(byte[] message) throws AsnException
    	{
    		SequenceDecoder sequence = ASNDecoder.Sequence(message, 2);
    		channelId = sequence.OctetString(16);
            sequence.end();
        	
			msgSocket.maxLength = 4194304;
            msgSocket.messageReceivedHandler = new Acceptor<byte[]>()
			{
				public void accept(byte[] message) { onMessageReceived(message); }
			};
			
			Channel_onEstablished(this);
			handshakeData = null;
    	}
    	
    	private void ProcessMessage_RestoreOk()
    	{
			msgSocket.maxLength = 4194304;
    		msgSocket.messageReceivedHandler = new Acceptor<byte[]>()
			{
				public void accept(byte[] message) { onMessageReceived(message); }
			};
		
			Channel_onEstablished(this);
			handshakeData = null;
    	}
    	
    	private SoftnetMessage EncodeMessage_HashAndKey2(byte[] passwordHash, byte[] key2)
        {
    		ASNEncoder asnEncoder = new ASNEncoder();
            SequenceEncoder sequence = asnEncoder.Sequence();
            sequence.OctetString(passwordHash);
            sequence.OctetString(key2);
            return MsgBuilder.Create(Constants.Service.Channel.ModuleId, Constants.Service.Channel.HASH_AND_KEY2, asnEncoder);
        }    	

    	private void onHandshakeMessageReceived(byte[] message)
    	{
    		try
    		{
	    		if (handshakePhase == HandshakePhase.PHASE_1)
	    		{
					if(message[0] == Constants.Service.Channel.ModuleId)
	                {
	    				if (message[1] == Constants.Service.Channel.SALT_AND_KEY1)
	                    {    					
	    					ProcessMessage_SaltAndKey1(message);	    					
	                    }    				
	    				else if (message[1] == Constants.Service.Channel.ERROR)
	                    {
	    					ProcessMessage_Error(message);
	                    }
	    				else
	    				{
	    					onFormatError();
	    				}
	                }
					else if (message[0] == 0 && message[1] == 1)			
					{
						ProcessMessage_Error(message);
					}
					else
					{
						onFormatError();
					}                
	    		}    		
	    		else // handshakePhase == HandshakePhase.PHASE_2
	    		{
					if(message[0] == Constants.Service.Channel.ModuleId)
					{
						if (channelId != null)
						{
	                        if (message[1] == Constants.Service.Channel.RESTORE_OK)
	                        {
	                        	ProcessMessage_RestoreOk();
	                        }
	                        else if (message[1] == Constants.Service.Channel.ERROR)
	                        {
	                        	ProcessMessage_Error(message);
	                        }
	                        else
	                        {
	                        	onFormatError();
	                        }
						}
						else
						{
							if (message[1] == Constants.Service.Channel.OPEN_OK)
	                        {
								ProcessMessage_OpenOk(message);
	                        }
	                        else if (message[1] == Constants.Service.Channel.ERROR)
	                        {
	                        	ProcessMessage_Error(message);
	                        }
	                        else
	                        {
	                        	onFormatError();
	                        }
						}
					}
					else
					{
						onFormatError();
					}
	    		}
	    	}    	
	    	catch(AsnException e)
			{
	    		onFormatError();
			}
			catch(HostFunctionalitySoftnetException e)
			{
				Channel_onCriticalError(this, e);
			}
    	}
    }
}