package softnet.service;

import java.net.*;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import softnet.*;
import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;
import softnet.utils.*;

class UDPConnectorV6 implements UDPConnector, STaskContext
{
	private UUID connectionUid;
	private InetAddress serverIP;
	private Scheduler scheduler;
	private UDPResponseHandler responseHandler;
	private BiAcceptor<byte[], Object> authenticationHandler;
	private Object attachment;
	
	private Object mutex = new Object();
	private boolean is_disposed = false;

	private MsgSocket msgSocket = null;
	private InetAddress localIP = null;
	private DatagramSocket m_dgmSocket = null;
	private boolean isEndpointEstablished = false;
	private ScheduledContextTask endpointEstablishmentTask = null;
	private byte[] thisEndpointUid = null;
	private byte[] remoteEndpointUid = null;
	private InetSocketAddress remoteIEP = null;
	private boolean isP2PInputHolePunched = false;
	private boolean isP2POutputHolePunched = false;
	private ScheduledContextTask p2pHolePunchTask = null;
	
	private enum ConnectorState
    {
        INITIAL, P2P_MODE, P2P_HANDSHAKE, PROXY_MODE, COMPLETED
    }
	private ConnectorState connectorState = ConnectorState.INITIAL;
			
	public UDPConnectorV6(UUID connectionUid, InetAddress serverIP, Scheduler scheduler)
	{
		this.connectionUid = connectionUid;
		this.serverIP = serverIP;
		this.scheduler = scheduler;
	}
	
	public void connect(UDPResponseHandler responseHandler, BiAcceptor<byte[], Object> authenticationHandler, Object attachment)
	{
		this.responseHandler = responseHandler;
		this.authenticationHandler = authenticationHandler;
		this.attachment = attachment;
		
		Thread thread = new Thread()
		{
		    public void run(){
		    	execute();
		    }
		};
		thread.start();
	}
	
	public void onAuthenticationHash(byte[] authHash, byte[] authKey2)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder sequence = asnEncoder.Sequence();
        sequence.OctetString(authHash);        
        sequence.OctetString(authKey2);   
        
        msgSocket.send(MsgBuilder.Create(Constants.Proxy.UdpConnector.AUTH_HASH, asnEncoder));
	}
		
	public boolean isClosed()
	{
		return is_disposed;
	}
	
	public void abort()
	{
		dispose();
	}
	
	private void dispose()
	{
		synchronized(mutex)
		{
			if(is_disposed)
				return;
			
			is_disposed = true;
			connectorState = ConnectorState.COMPLETED;			

			if(msgSocket != null)
				msgSocket.close();
			
			if(m_dgmSocket != null)
				m_dgmSocket.close();			
		}
	}
	
	private void completeOnError()
	{		
		synchronized(mutex)
		{
			if (connectorState == ConnectorState.COMPLETED)
                return;
			connectorState = ConnectorState.COMPLETED;
		}
		
		dispose();
		responseHandler.onError(ErrorCodes.CONNECTION_ATTEMPT_FAILED, attachment);
	}
	
	private void execute()
	{
		SocketChannel controlChannel = null;
		try
		{
			synchronized(mutex)
			{
				if(is_disposed == false)
				{
					controlChannel = SocketChannel.open();
					msgSocket = new MsgSocket(controlChannel);
				}
				else return;
			}
			
			controlChannel.configureBlocking(true);
			controlChannel.connect(new InetSocketAddress(serverIP, Constants.ServerPorts.UdpRzvPort));	
			
			localIP = ((InetSocketAddress)controlChannel.getLocalAddress()).getAddress();
			
			msgSocket.messageReceivedHandler	= new Acceptor<byte[]>()
			{
				public void accept(byte[] message) { onMessageReceived(message); }
			};
			msgSocket.networkErrorHandler = new Acceptor<NetworkErrorSoftnetException>()
			{
				public void accept(NetworkErrorSoftnetException ex) { onNetworkError(ex); }
			};
			msgSocket.formatErrorHandler = new Runnable()
			{
				public void run() { onFormatError(); }
			};					
			msgSocket.minLength = 1;
			msgSocket.maxLength = 256;
			msgSocket.start();
			
			msgSocket.send(EncodeMessage_Service());
		}
		catch(IOException ex)
		{			
			closeChannelIfOpened(controlChannel);
            responseHandler.onError(ErrorCodes.CONNECTION_ATTEMPT_FAILED, attachment);
		}
	}
	
	private void ProcessMessage_AuthKey(byte[] message) throws AsnException
	{
		SequenceDecoder sequence = ASNDecoder.Sequence(message, 1);            
		byte[] authKey = sequence.OctetString(20);
		byte[] endpointUid = sequence.OctetString(16);
        sequence.end();

        synchronized(mutex)
		{
			if (connectorState != ConnectorState.INITIAL)
                return;
			connectorState = ConnectorState.P2P_MODE;
			thisEndpointUid = endpointUid;			
		}
        
        Thread thread = new Thread()
		{
		    public void run(){
		    	udpExecute();
		    }
		};
		thread.start();
        
        authenticationHandler.accept(authKey, attachment);
	}
	
	private void udpExecute()
	{
		try
		{
			DatagramSocket dgmSocket = new DatagramSocket(new InetSocketAddress(localIP, 0));			
			synchronized(mutex)
			{
				if(is_disposed)
				{
					dgmSocket.close();
					return;
				}
				m_dgmSocket = dgmSocket;
			}
			
			sendAttachToConnectorCommand(1);
			
			while(connectorState !=  ConnectorState.COMPLETED)
			{
				byte[] data = new byte[32];
				DatagramPacket packet = new DatagramPacket(data, 32);
				dgmSocket.receive(packet);
				
				if(packet.getLength() == 17)
				{
					byte messageTag = data[0];
					if(messageTag == Constants.Proxy.UdpEndpoint.ATTACHED)
					{
						if(ByteArrays.equals(data, 1, thisEndpointUid, 0, 16))
						{
							isEndpointEstablished = true;
							if(endpointEstablishmentTask != null)
								endpointEstablishmentTask.complete();
						}
					}
					else if(messageTag == Constants.Proxy.UdpEndpoint.P2P_HOLE_PUNCH)
					{
						if(remoteEndpointUid != null && ByteArrays.equals(data, 1, remoteEndpointUid, 0, 16))
						{
							synchronized(mutex)
							{
								if(isP2PInputHolePunched)
									continue;
								
								isP2PInputHolePunched = true;
								remoteIEP = (InetSocketAddress)packet.getSocketAddress();
								
								msgSocket.send(MsgBuilder.Create(Constants.Proxy.UdpConnector.P2P_HOLE_PUNCHED));
								
								if(connectorState != ConnectorState.P2P_HANDSHAKE || isP2POutputHolePunched == false)
									continue;
								
								connectorState = ConnectorState.COMPLETED;			
								msgSocket.shutdownOutput();
								m_dgmSocket = null;
							}
							
							dispose();
							responseHandler.onSuccess(dgmSocket, remoteIEP, ConnectionMode.P2P, attachment);
							return;
						}
					}
					else if(messageTag == Constants.Proxy.UdpEndpoint.PROXY_ESTABLISHED)
					{
						if(ByteArrays.equals(data, 1, thisEndpointUid, 0, 16))
						{
							synchronized(mutex)
							{
								if(connectorState == ConnectorState.COMPLETED)
									return;
								connectorState = ConnectorState.COMPLETED;
								msgSocket.shutdownOutput();
								m_dgmSocket = null;
							}
							
							dispose();
							responseHandler.onSuccess(dgmSocket, new InetSocketAddress(serverIP, Constants.ServerPorts.UdpRzvPort), ConnectionMode.Proxy, attachment);
							return;
						}
					}
				}
			}
		}
		catch(IOException ex)
		{
			completeOnError();
		}
	}	

	private void sendAttachToConnectorCommand(Object state)
	{
		if(isEndpointEstablished)
			return;
		
		DatagramSocket dgmSocket = null;
		synchronized(mutex)
		{
			if(connectorState == ConnectorState.COMPLETED)
				return;
			dgmSocket = m_dgmSocket;
		}

		try
		{
			byte[] data = new byte[17];
			data[0] = Constants.Proxy.UdpEndpoint.ATTACH_TO_CONNECTOR;
			System.arraycopy(thisEndpointUid, 0, data, 1, 16);

			DatagramPacket packet = new DatagramPacket(data, 17, serverIP, Constants.ServerPorts.UdpRzvPort);
			dgmSocket.send(packet);

			int packetRepeatPeriod = (int)state;
			if(packetRepeatPeriod <= 8)
			{
				Acceptor<Object> acceptor = new Acceptor<Object>()
				{
					public void accept(Object state) { sendAttachToConnectorCommand(state); }
				};
				endpointEstablishmentTask = new ScheduledContextTask(acceptor, this, packetRepeatPeriod * 2);
				scheduler.add(endpointEstablishmentTask, packetRepeatPeriod);
			}
			else
			{
				Acceptor<Object> acceptor = new Acceptor<Object>()
				{
					public void accept(Object noData) { onEndpointEstablishmentFailed(); }
				};
				endpointEstablishmentTask = new ScheduledContextTask(acceptor, this, null);					
				scheduler.add(endpointEstablishmentTask, 4);
			}
		}
		catch(IOException ex)
		{
			completeOnError();			
		}
	}
	
	private void onEndpointEstablishmentFailed()
	{
		synchronized(mutex)
		{
			if (isEndpointEstablished || connectorState != ConnectorState.P2P_MODE)
                return;
			connectorState = ConnectorState.COMPLETED;
		}
		
		dispose();
		responseHandler.onError(ErrorCodes.CONNECTION_ATTEMPT_FAILED, attachment);
	}
	
	private void sendP2PHolePunch(Object state)
	{
		DatagramSocket dgmSocket = null;
		InetSocketAddress ipEndpoint = null;
		synchronized(mutex)
		{
			if(connectorState != ConnectorState.P2P_HANDSHAKE)
				return;
			dgmSocket = m_dgmSocket;
			ipEndpoint = remoteIEP;
		}
		
		try
		{
			byte[] data = new byte[17];
			data[0] = Constants.Proxy.UdpEndpoint.P2P_HOLE_PUNCH;
			System.arraycopy(thisEndpointUid, 0, data, 1, 16);

			DatagramPacket packet = new DatagramPacket(data, 17, ipEndpoint);
			dgmSocket.send(packet);
						
			int packetCounter = (int)state;
			if(packetCounter == 1)
			{
				Acceptor<Object> acceptor = new Acceptor<Object>()
				{
					public void accept(Object state) { sendP2PHolePunch(state); }
				};
				p2pHolePunchTask = new ScheduledContextTask(acceptor, this, 2);
				scheduler.add(p2pHolePunchTask, 1); // send the second packet in a second
			}
			else if(packetCounter == 2)
			{
				Acceptor<Object> acceptor = new Acceptor<Object>()
				{
					public void accept(Object state) { sendP2PHolePunch(state); }
				};
				p2pHolePunchTask = new ScheduledContextTask(acceptor, this, 3);
				scheduler.add(p2pHolePunchTask, 2); // send the third packet in two seconds
			}			
		}
		catch(IOException ex) {}
	}

	private void onP2PConnectionAttemptFailed()
	{
		synchronized(mutex)
        {
            if (connectorState != ConnectorState.P2P_HANDSHAKE)
                return;
            connectorState = ConnectorState.PROXY_MODE;
            
            if(p2pHolePunchTask != null)
            	p2pHolePunchTask.cancel();
        }
		
		msgSocket.send(MsgBuilder.Create(Constants.Proxy.UdpConnector.P2P_FAILED));
	}
	
	private void ProcessMessage_CreateP2PConnection(byte[] message) throws AsnException, UnknownHostException
	{
		SequenceDecoder sequence = ASNDecoder.Sequence(message, 1);
		byte[] iepBytes = sequence.OctetString(18);
		byte[] endpointUid = sequence.OctetString(16);
		sequence.end();
		
		byte[] ipBytes = new byte[16];
		System.arraycopy(iepBytes, 0, ipBytes, 0, 16);		
		InetAddress ip = InetAddress.getByAddress(ipBytes);
		int port = ByteConverter.toInt32FromUInt16(iepBytes, 16);
		InetSocketAddress ipEndpoint = new InetSocketAddress(ip, port);
		
		synchronized(mutex)
		{
			if (connectorState != ConnectorState.P2P_MODE)
                return;
			connectorState = ConnectorState.P2P_HANDSHAKE;
			
			remoteEndpointUid = endpointUid;
			remoteIEP = ipEndpoint;
		}
		
		Acceptor<Object> acceptor = new Acceptor<Object>()
		{
			public void accept(Object noData) { onP2PConnectionAttemptFailed(); }
		};
		ScheduledContextTask task = new ScheduledContextTask(acceptor, this, null);
		scheduler.add(task, Constants.UdpP2PConnectionAttemptTimeoutSeconds);
        
		sendP2PHolePunch(1);
	}
	
	private void ProcessMessage_P2PHolePunched()
	{
		DatagramSocket dgmSocket = null;
		synchronized(mutex)
		{
			if(connectorState != ConnectorState.P2P_HANDSHAKE)
				return;
			
			isP2POutputHolePunched = true;						
			if(isP2PInputHolePunched == false)
				return;
			
			connectorState = ConnectorState.COMPLETED;
			msgSocket.shutdownOutput();
			dgmSocket = m_dgmSocket;
			m_dgmSocket = null;
		}
		
		dispose();
		responseHandler.onSuccess(dgmSocket, remoteIEP, ConnectionMode.P2P, attachment);
	}

	private SoftnetMessage EncodeMessage_Service()
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder sequence = asnEncoder.Sequence();
        sequence.OctetString(connectionUid);
        return MsgBuilder.Create(Constants.Proxy.UdpConnector.SERVICE, asnEncoder);
	}
	
	private void onMessageReceived(byte[] message)
	{
		try		
		{
			byte messageTag = message[0];
			if(messageTag == Constants.Proxy.UdpConnector.AUTH_KEY)
			{
				ProcessMessage_AuthKey(message);
			}
			else if(messageTag == Constants.Proxy.UdpConnector.CREATE_P2P_CONNECTION)
			{
				ProcessMessage_CreateP2PConnection(message);
			}
			else if(messageTag == Constants.Proxy.UdpConnector.P2P_HOLE_PUNCHED)
			{
				ProcessMessage_P2PHolePunched();
			}
			else
			{
				completeOnError();
			}
		}
		catch(AsnException | UnknownHostException e)
		{
			completeOnError();
		}
	}
	
	private void onNetworkError(NetworkErrorSoftnetException ex)
	{
		completeOnError();
	}

	private void onFormatError()
	{
		completeOnError();
	}
	
	private void closeChannelIfOpened(SocketChannel channel)
	{
		if(channel != null)
		{
			try
			{
				channel.close();
			}
			catch(IOException e) {}
		}
	}
}
