package softnet.discovery;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SocketChannel;

import softnet.core.*;
import softnet.exceptions.*;
import softnet.utils.ByteConverter;
import softnet.utils.Fnv1a;

public class BQueryExecutor implements STaskContext
{
	private SocketChannel socketChannel;
	private String serverAddress;
	private QueryBuilder queryBuilder;
	private Scheduler scheduler;
	
	public BQueryExecutor(SocketChannel socketChannel, String serverAddress, QueryBuilder queryBuilder, Scheduler scheduler)
	{
		this.socketChannel = socketChannel;
		this.serverAddress = serverAddress;
		this.queryBuilder = queryBuilder;
		this.scheduler = scheduler;
	}
	
	private boolean is_closed = false;
	public boolean isClosed()
	{
		return is_closed;
	}
	
	public void close()
	{
		is_closed = true;
		closeChannel();		
	}
			
	public InetAddress exec() throws SoftnetException
	{
		try
		{
			java.net.Socket socket = socketChannel.socket();
			java.net.InetSocketAddress remoteAddress = (java.net.InetSocketAddress)socket.getRemoteSocketAddress();
			InetAddress serverIP = remoteAddress.getAddress();
			
			ByteBuffer requestBuffer = queryBuilder.GetQuery();
			
			int request_buffer_offset = requestBuffer.position();
			int request_buffer_size = requestBuffer.remaining();
						
			socketChannel.write(requestBuffer);
			socket.shutdownOutput();

			Acceptor<Object> acceptor = new Acceptor<Object>()
			{
				public void accept(Object noData) { closeChannel(); }
			};
			ScheduledTask task = new ScheduledContextTask(acceptor, this);
			scheduler.add(task, 30);
			
			ByteBuffer responseBuffer = ByteBuffer.allocate(24);
			responseBuffer.order(ByteOrder.BIG_ENDIAN);
			
			try
			{
				for(int i=0; i < 24; i++)
				{
					int bytesCount = socketChannel.read(responseBuffer);
					
					if(responseBuffer.remaining() == 0)
			        	throw new InvalidServerEndpointSoftnetException(serverIP, Constants.ServerPorts.Balancer);
					
					if(bytesCount == -1)
					{
						int dataSize = responseBuffer.position();
						if(dataSize < 6)
				        	throw new InvalidServerEndpointSoftnetException(serverIP, Constants.ServerPorts.Balancer);

						byte[] response = responseBuffer.array();
						int receivedHash = ByteConverter.toInt32(response, 0);

						byte[] request = requestBuffer.array();												
						int requestHash = Fnv1a.get32BitHash(request, request_buffer_offset, request_buffer_size);

						if(receivedHash != requestHash)
		    				throw new InvalidServerEndpointSoftnetException(serverIP, Constants.ServerPorts.Balancer);

		    			byte messageTag = response[4];
				        if(messageTag == Constants.FrontServer.SUCCESS)
				        {
				        	int ipVersion = response[5];	        	
			    			if(ipVersion == Constants.FrontServer.IP_V6 && dataSize == 22 && serverIP instanceof Inet6Address)
			    			{
		    					byte[] addrBytes = new byte[16];
		    					System.arraycopy(response, 6, addrBytes, 0, 16);
		    					return InetAddress.getByAddress(addrBytes);
			    			}
			    			else if(ipVersion == Constants.FrontServer.IP_V4 && dataSize == 10 && serverIP instanceof Inet4Address)
			    			{
		    					byte[] addrBytes = new byte[4];
		    					System.arraycopy(response, 6, addrBytes, 0, 4);
		    					return InetAddress.getByAddress(addrBytes);
			    			}
				        }
				        else if(messageTag == Constants.FrontServer.ERROR && dataSize == 7)
				        {
				        	int errorCode = ByteConverter.toInt32FromInt16(response, 5);
				        	
				        	queryBuilder.ThrowException(errorCode);
				        	
				        	if(errorCode == ErrorCodes.SERVER_BUSY)
							{
								throw new ServerBusySoftnetException();
							}
							else if(errorCode == ErrorCodes.SERVER_DBMS_ERROR)
							{
								throw new ServerDbmsErrorSoftnetException();
							}
				    		else if (errorCode == ErrorCodes.SERVER_CONFIG_ERROR)
				            {
				    			throw new ServerConfigErrorSoftnetException();
				            }
							else if(errorCode == ErrorCodes.INVALID_SERVER_ENDPOINT)
							{
					        	throw new InvalidServerEndpointSoftnetException(String.format("The softnet registry at '%s' does not match the expected one.", serverAddress));
							}
							else if(errorCode == ErrorCodes.INCOMPATIBLE_PROTOCOL_VERSION)
							{
								throw new IncompatibleProtocolVersionSoftnetException();
							}
							else if (errorCode == ErrorCodes.ENDPOINT_DATA_FORMAT_ERROR)
				            {
				    			throw new EndpointDataFormatSoftnetException();
				            }
							else
							{
								throw new UnexpectedErrorSoftnetException(errorCode);
							}
				        }
				        
				    	throw new InvalidServerEndpointSoftnetException(serverIP, Constants.ServerPorts.Balancer);
					}
				}
				
		    	throw new NetworkErrorSoftnetException("The socket is not properly configured.");
			}
			catch(AsynchronousCloseException ex)
			{
				throw new TimeoutExpiredSoftnetException(String.format("The remote endpoint '%s:%d' did not properly respond after a period of time.", serverIP.toString(), Constants.ServerPorts.Balancer));
			}				
		}		
		catch(IOException ex)
		{			
			throw new NetworkErrorSoftnetException(ex.getMessage());			
		}				
	}
			
	private void closeChannel()
	{		
    	try { socketChannel.close(); } catch(IOException e) {}		
	}
}
