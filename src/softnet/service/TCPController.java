package softnet.service;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.UUID;

import softnet.*;
import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;
import softnet.utils.ByteConverter;

class TCPController
{
	private Object mutex;	
	private ServiceEndpoint serviceEndpoint;
 	private Membership membership;
 	private ArrayList<TCPBinding> tcpBindings;
	
	public TCPController(ServiceEndpoint serviceEndpoint, Membership membership)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.membership = membership;
		mutex = new Object();
		tcpBindings = new ArrayList<TCPBinding>();
	}
	
	public void listen(int virtualPort, TCPOptions tcpOptions, int backlog)
	{
		if(backlog < 0)
			throw new IllegalArgumentException("The value of backlog must not be negative.");			

		synchronized(mutex)
		{		
			if(findBinding(virtualPort) != null)
				throw new IllegalArgumentException(String.format("The TCP virtual port %d is already in use.", virtualPort));					
			
			TCPBinding newBinding = new TCPBinding(serviceEndpoint, virtualPort, tcpOptions, backlog);
			tcpBindings.add(newBinding);
		}
	}

	public void listen(int virtualPort, TCPOptions tcpOptions, int backlog, String roles)
	{
		if(backlog < 0)
			throw new IllegalArgumentException("The value of backlog must not be negative.");			
		
		if(membership.isRbacSupported() == false)
			throw new IllegalArgumentException("A role-based access control is not supported.");
		
		if(roles == null || roles.length() == 0)
			throw new IllegalArgumentException("The list of roles must not be null or empty");
				
		String[] roleNames = roles.split(";");
		for(int i = 0; i < roleNames.length; i++)
			roleNames[i] = roleNames[i].trim();
		
		for(String role: roleNames)
		{
			if(membership.containsRole(role) == false)
				throw new IllegalArgumentException(String.format("Illegal role '%s'.", role));
		}
		
		synchronized(mutex)
		{		
			if(findBinding(virtualPort) != null)
				throw new IllegalArgumentException(String.format("The TCP virtual port %d is already in use.", virtualPort));
			
			TCPBinding newBinding = new TCPBinding(serviceEndpoint, virtualPort, tcpOptions, backlog, roleNames);
			tcpBindings.add(newBinding);
		}
	}

	public void listen(int virtualPort, TCPOptions tcpOptions, int backlog, GuestAccess guestAccess)
	{
		if(backlog < 0)
			throw new IllegalArgumentException("The value of backlog must not be negative.");			

		if(guestAccess == null)
			throw new IllegalArgumentException("'guestAccess' is null.");		
		
		synchronized(mutex)
		{		
			if(findBinding(virtualPort) != null)
				throw new IllegalArgumentException(String.format("The TCP virtual port %d is already in use.", virtualPort));
			
			TCPBinding newBinding = new TCPBinding(serviceEndpoint, virtualPort, tcpOptions, backlog, guestAccess);
			tcpBindings.add(newBinding);
		}
	}
	
	public void releasePort(int virtualPort)
	{
		synchronized(mutex)
		{		
			TCPBinding tcpBinding = findBinding(virtualPort);
			if(tcpBinding != null)
			{
				tcpBindings.remove(tcpBinding);
				tcpBinding.close();
			}
		}
	}

	public void accept(int virtualPort, TCPAcceptHandler acceptHandler)
	{
		if(acceptHandler == null)
			throw new IllegalArgumentException("'acceptHandler' is null.");			

		TCPBinding tcpBinding = null;
		synchronized(mutex)
		{		
			tcpBinding = findBinding(virtualPort);
			if(tcpBinding == null)
				throw new IllegalArgumentException(String.format("The specified port %d is not listening for TCP connections.", virtualPort));			
		}
		tcpBinding.accept(acceptHandler);
	}
	
	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Service.TcpController.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					onMessageReceived(message, _channel);
				}
			});		
	}
	
	public void onEndpointDisconnected()
	{
		synchronized(mutex)
		{
			for(TCPBinding tcpBinding: tcpBindings)
				tcpBinding.onEndpointDisconnected();
		}
	}
	
	public void onEndpointClosed()
	{
		synchronized(mutex)
		{
			for(TCPBinding tcpBinding: tcpBindings)
				tcpBinding.onEndpointClosed();
		}
	}
	
	private void processMessage_Request(byte[] message, Channel channel) throws AsnException
	{
		SequenceDecoder asnSequence = ASNDecoder.Sequence(message, 2);
		byte[] requestUid = asnSequence.OctetString(16);
		int virtualPort = asnSequence.Int32();
		int userKind = asnSequence.Int32(1, 4);
		long userId = asnSequence.Int64();
		long clientId = asnSequence.Int64();
		asnSequence.end();
		
		TCPBinding tcpBinding = null; 
		synchronized(mutex)
		{
			if(channel.isClosed())
				return;			
			tcpBinding = findBinding(virtualPort);
		}
		
		if(tcpBinding == null)
		{			
			channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.PORT_UNREACHABLE, userKind, clientId));
			return;
		}
		
		MembershipUser user = membership.resolve(userKind, userId);
		if(user == null)
		{
			channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
			return;
		}
		
		if(tcpBinding.roles != null)
		{
			if(user.isGuest())
			{
				channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
				return;				
			}
			
			boolean userAllowed = false;
			for(String role: tcpBinding.roles)
			{
				if(user.isInRole(role))
				{
					userAllowed = true;
					break;
				}
			}
			
			if(userAllowed == false)
			{
				channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
				return;		
			}
		}
		else if(tcpBinding.guestAccess != null)
		{
			if(tcpBinding.guestAccess == GuestAccess.GuestDenied)
			{
				if(user.isGuest())
				{
					channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
					return;
				}
			}			
			else if(user.isStatelessGuest()) // tcpBinding.guestAccess == GuestAccess.StatelessGuestDenied
			{
				channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
				return;
			}
		}
		
		if(tcpBinding.isBusy())
		{
			channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.SERVICE_BUSY, userKind, clientId));
			return;
		}
		
		channel.send(EncodeMessage_RequestOk(requestUid, virtualPort, userKind, clientId));
	}
	
	private void processMessage_RzvData(byte[] message, Channel channel) throws AsnException, FormatException
	{
		SequenceDecoder asnSequence = ASNDecoder.Sequence(message, 2);
		byte[] requestUid = asnSequence.OctetString(16);
		UUID connectionUid = asnSequence.OctetStringToUUID();
		int serverId = asnSequence.Int32();
		byte[] serverIpBytes = asnSequence.OctetString();
		int virtualPort = asnSequence.Int32();
		int userKind = asnSequence.Int32(1, 4);
		long userId = asnSequence.Int64();
		long clientId = asnSequence.Int64();
		asnSequence.end();
		
		InetAddress serverIp = ByteConverter.toInetAddress(serverIpBytes);
		
		TCPBinding tcpBinding = null; 
		synchronized(mutex)
		{
			if(channel.isClosed())
				return;			
			tcpBinding = findBinding(virtualPort);
		}
		
		if(tcpBinding == null)
		{			
			channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.PORT_UNREACHABLE, userKind, clientId));
			return;
		}
		
		MembershipUser user = membership.resolve(userKind, userId);
		if(user == null)
		{
			channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
			return;
		}		
		
		if(tcpBinding.roles != null)
		{
			if(user.isGuest())
			{
				channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
				return;				
			}
			
			boolean userAllowed = false;
			for(String role: tcpBinding.roles)
			{
				if(user.isInRole(role))
				{
					userAllowed = true;
					break;
				}
			}
			
			if(userAllowed == false)
			{
				channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
				return;		
			}			
		}
		else if(tcpBinding.guestAccess != null)
		{
			if(tcpBinding.guestAccess == GuestAccess.GuestDenied)
			{
				if(user.isGuest())
				{
					channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
					return;
				}
			}			
			else if(user.isStatelessGuest()) // tcpBinding.guestAccess == GuestAccess.StatelessGuestDenied
			{
				channel.send(EncodeMessage_RequestError(requestUid, ErrorCodes.ACCESS_DENIED, userKind, clientId));
				return;
			}
		}
		
		tcpBinding.createConnection(channel, requestUid, connectionUid, serverId, serverIp, userKind, user, clientId);
	}
	
	private void processMessage_AuthHash(byte[] message, Channel channel) throws AsnException
	{
		SequenceDecoder asnSequence = ASNDecoder.Sequence(message, 2);
		int virtualPort = asnSequence.Int32();
		UUID connectionUid = asnSequence.OctetStringToUUID();
		byte[] authHash = asnSequence.OctetString(20);
		byte[] authKey2 = asnSequence.OctetString(20);
		asnSequence.end();
		
		TCPBinding tcpBinding = null; 
		synchronized(mutex)
		{
			if(channel.isClosed())
				return;			
			tcpBinding = findBinding(virtualPort);
		}
		
		if(tcpBinding != null)
		{
			tcpBinding.onAuthenticationHash(connectionUid, authHash, authKey2);
		}
	}
	
	private void processMessage_AuthError(byte[] message, Channel channel) throws AsnException
	{
		SequenceDecoder asnSequence = ASNDecoder.Sequence(message, 2);
		int virtualPort = asnSequence.Int32();
		UUID connectionUid = asnSequence.OctetStringToUUID();
		asnSequence.end();
		
		TCPBinding tcpBinding = null; 
		synchronized(mutex)
		{
			if(channel.isClosed())
				return;			
			tcpBinding = findBinding(virtualPort);
		}
		
		if(tcpBinding != null)
		{
			tcpBinding.onAuthenticationError(connectionUid);
		}
	}
	
	private SoftnetMessage EncodeMessage_RequestOk(byte[] requestUid, int virtualPort, int userKind, long clientId)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.OctetString(requestUid);
        asnSequence.Int32(virtualPort);
        asnSequence.Int32(userKind);
        asnSequence.Int64(clientId);
        return MsgBuilder.Create(Constants.Service.TcpController.ModuleId, Constants.Service.TcpController.REQUEST_OK, asnEncoder);
	}
	
	private SoftnetMessage EncodeMessage_RequestError(byte[] requestUid, int errorCode, int userKind, long clientId)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.OctetString(requestUid);
        asnSequence.Int32(errorCode);
        asnSequence.Int32(userKind);
        asnSequence.Int64(clientId);
        return MsgBuilder.Create(Constants.Service.TcpController.ModuleId, Constants.Service.TcpController.REQUEST_ERROR, asnEncoder);
	}
	
	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException
	{
		byte messageTag = message[1];
		if(messageTag == Constants.Service.TcpController.REQUEST)
		{
			processMessage_Request(message, channel);
		}
		else if(messageTag == Constants.Service.TcpController.RZV_DATA)
		{
			processMessage_RzvData(message, channel);
		}
		else if(messageTag == Constants.Service.TcpController.AUTH_HASH)
		{
			processMessage_AuthHash(message, channel);
		}
		else if(messageTag == Constants.Service.TcpController.AUTH_ERROR)
		{
			processMessage_AuthError(message, channel);
		}
		else
			throw new FormatException();
	}

	private TCPBinding findBinding(int virtualPort)
	{
		for(TCPBinding tcpBinding:  tcpBindings)
		{
			if(tcpBinding.virtualPort == virtualPort)
				return tcpBinding; 					
		}
		return null;
	}
}
