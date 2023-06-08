package softnet.service;

import java.util.Hashtable;
import java.util.regex.Pattern;

import softnet.*;
import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;

class RPCController
{
	public RPCController(ServiceEndpoint serviceEndpoint, Membership membership)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.membership = membership;
		procedures = new Hashtable<String, AppProcedure>();
		mutex = new Object();
		concurrentRequests = 0;
	}
	 
	public void register(String procedureName, RPCRequestHandler requestHandler, int concurrencyLimit)
	{
		validateProcedureName(procedureName);

		if(requestHandler == null)
			throw new IllegalArgumentException("'requestHandler' is null.");			

		if(concurrencyLimit <= 0)
	        throw new IllegalArgumentException(String.format("The concurreny limit '%d' is illegal. A valid value is greater than or equal to 1.", concurrencyLimit));	

		synchronized(mutex)
		{
			if(procedures.containsKey(procedureName) == false)
			{
				AppProcedure appProcedure = new AppProcedure(requestHandler, concurrencyLimit);
				procedures.put(procedureName, appProcedure);
			}
			else
			{
				throw new IllegalArgumentException(String.format("The procedure '%s' has already been registered.", procedureName));
			}
		}
	}
		
	public void register(String procedureName, RPCRequestHandler requestHandler, int concurrencyLimit, String roles)
	{
		validateProcedureName(procedureName);

		if(requestHandler == null)
			throw new IllegalArgumentException("'requestHandler' is null.");			

		if(concurrencyLimit <= 0)
	        throw new IllegalArgumentException(String.format("The concurreny limit '%d' is illegal. A valid value is greater than or equal to 1.", concurrencyLimit));	

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
			if(procedures.containsKey(procedureName) == false)
			{
				AppProcedure appProcedure = new AppProcedure(requestHandler, concurrencyLimit, roleNames);
				procedures.put(procedureName, appProcedure);
			}
			else
			{
				throw new IllegalArgumentException(String.format("The procedure '%s' has already been registered.", procedureName));
			}
		}
	}
	
	public void register(String procedureName, RPCRequestHandler requestHandler, int concurrencyLimit, GuestAccess guestAccess)
	{
		validateProcedureName(procedureName);

		if(requestHandler == null)
			throw new IllegalArgumentException("'requestHandler' is null.");			

		if(concurrencyLimit <= 0)
	        throw new IllegalArgumentException(String.format("The concurreny limit '%d' is illegal. A valid value is greater than or equal to 1.", concurrencyLimit));	

		if(guestAccess == null)
			throw new IllegalArgumentException("'guestAccess' is null.");		

		synchronized(mutex)
		{
			if(procedures.containsKey(procedureName) == false)
			{				
				AppProcedure appProcedure = new AppProcedure(requestHandler, concurrencyLimit, guestAccess);
				procedures.put(procedureName, appProcedure);
			}
			else
			{
				throw new IllegalArgumentException(String.format("The procedure '%s' has already been registered.", procedureName));
			}
		}
	}
	
	public void remove(String procedureName)
	{
		synchronized(mutex)
		{
			procedures.remove(procedureName);
		}		
	}
	
	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Service.RpcController.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					OnMessageReceived(message, _channel);
				}
			});
	}
			
	private final Object mutex;
	private ServiceEndpoint serviceEndpoint;
 	private Membership membership;
	private Hashtable<String, AppProcedure> procedures;
	
	private int concurrentRequests;
	
	private void processMessage_Request(byte[] message, final Channel channel) throws AsnException, SoftnetException
	{
		SequenceDecoder asnSequence = ASNDecoder.Sequence(message, 2);
		final byte[] transactionUid = asnSequence.OctetString(16);
		String procedureName = asnSequence.IA5String(1, 256);
		final int userKind = asnSequence.Int32(1, 4);
		final long userId = asnSequence.Int64();
		final long clientId = asnSequence.Int64();
		byte[] argumentsEncoding = asnSequence.OctetString(2, 65536);
		asnSequence.end();
		
		AppProcedure appProcedure = null;
		synchronized(mutex)
		{
			appProcedure = procedures.get(procedureName);
		}
		
		if(appProcedure == null)
		{
			channel.send(EncodeMessage_SoftnetError(transactionUid, userKind, clientId, ErrorCodes.MISSING_PROCEDURE));
			return;
		}

		final MembershipUser user = membership.resolve(userKind, userId);
		if(user == null)
		{
			channel.send(EncodeMessage_SoftnetError(transactionUid, userKind, clientId, ErrorCodes.ACCESS_DENIED));
			return;
		}
		
		if(appProcedure.roles != null)
		{
			if(user.isGuest())
			{
				channel.send(EncodeMessage_SoftnetError(transactionUid, userKind, clientId, ErrorCodes.ACCESS_DENIED));
				return;				
			}
			
			boolean userAllowed = false;
			for(String role: appProcedure.roles)
			{
				if(user.isInRole(role))
				{
					userAllowed = true;
					break;
				}
			}
			
			if(userAllowed == false)
			{
				channel.send(EncodeMessage_SoftnetError(transactionUid, userKind, clientId, ErrorCodes.ACCESS_DENIED));
				return;		
			}			
		}
		else if(appProcedure.guestAccess != null) 
		{
			if(appProcedure.guestAccess == GuestAccess.GuestDenied)
			{
				if(user.isGuest())
				{
					channel.send(EncodeMessage_SoftnetError(transactionUid, userKind, clientId, ErrorCodes.ACCESS_DENIED));
					return;
				}
			}			
			else if(user.isStatelessGuest()) // appProcedure.guestAccess == GuestAccess.StatelessGuestDenied
			{
				channel.send(EncodeMessage_SoftnetError(transactionUid, userKind, clientId, ErrorCodes.ACCESS_DENIED));
				return;
			}			
		}
		
		synchronized(mutex)
		{
			if(concurrentRequests >= appProcedure.concurrencyLimit)
			{
				channel.send(EncodeMessage_SoftnetError(transactionUid, userKind, clientId, ErrorCodes.SERVICE_BUSY));
				return;
			}				
			concurrentRequests++;
		}
		
		final SequenceDecoder f_arguments = ASNDecoder.Sequence(argumentsEncoding);
		final AppProcedure f_appProcedure = appProcedure;
		
		Runnable runnable = new Runnable()
		{
			@Override
			public void run()
			{					
				try
				{
					ASNEncoder asnEncoder = new ASNEncoder();
					SequenceEncoder result = asnEncoder.Sequence();
					
					ASNEncoder asnEncoder2 = new ASNEncoder();
					SequenceEncoder error = asnEncoder2.Sequence();
					
					if(user.isStatelessGuest() == false)
					{
						int errorCode = f_appProcedure.requestHandler.execute(new RequestContext(serviceEndpoint, user, clientId), f_arguments, result, error);
						if(errorCode == 0)
						{
							channel.send(EncodeMessage_Result(transactionUid, userKind, clientId, asnEncoder.getEncoding()));
						}
						else
						{
							channel.send(EncodeMessage_AppError(transactionUid, userKind, clientId, errorCode, asnEncoder2.getEncoding()));
						}
					}
					else
					{
						int errorCode = f_appProcedure.requestHandler.execute(new RequestContext(serviceEndpoint, user, 0), f_arguments, result, error);
						if(errorCode == 0)
						{
							channel.send(EncodeMessage_Result(transactionUid, userKind, clientId, asnEncoder.getEncoding()));
						}
						else
						{
							channel.send(EncodeMessage_AppError(transactionUid, userKind, clientId, errorCode, asnEncoder2.getEncoding()));
						}
					}
				}
				finally
				{
					synchronized(mutex)
					{
						concurrentRequests--;
					}
				}
			}
		};
		
		try
		{
			serviceEndpoint.threadPool.execute(runnable);
		}
		catch(java.util.concurrent.RejectedExecutionException e)
		{
			synchronized(mutex)
			{
				concurrentRequests--;
			}
		}		
	}
	
	private SoftnetMessage EncodeMessage_Result(byte[] transactionUid, int userKind, long clientId, byte[]  resultEncoding)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.OctetString(transactionUid);
        asnSequence.Int32(userKind);
        asnSequence.Int64(clientId);
        asnSequence.OctetString(resultEncoding);
        return MsgBuilder.Create(Constants.Service.RpcController.ModuleId, Constants.Service.RpcController.RESULT, asnEncoder);		
	}
	
	private SoftnetMessage EncodeMessage_SoftnetError(byte[] transactionUid, int userKind, long clientId, int errorCode)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.OctetString(transactionUid);
        asnSequence.Int32(userKind);
        asnSequence.Int64(clientId);
        asnSequence.Int32(errorCode);
        return MsgBuilder.Create(Constants.Service.RpcController.ModuleId, Constants.Service.RpcController.SOFTNET_ERROR, asnEncoder);
	}
	
	private SoftnetMessage EncodeMessage_AppError(byte[] transactionUid, int userKind, long clientId, int errorCode, byte[] errorEncoding)
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSequence = asnEncoder.Sequence();
        asnSequence.OctetString(transactionUid);
        asnSequence.Int32(userKind);
        asnSequence.Int64(clientId);
        asnSequence.Int32(errorCode);
        asnSequence.OctetString(errorEncoding);
        return MsgBuilder.Create(Constants.Service.RpcController.ModuleId, Constants.Service.RpcController.APP_ERROR, asnEncoder);
	}
	
	private void OnMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
	{
		byte messageTag = message[1];
		if(messageTag == Constants.Service.RpcController.REQUEST)
		{
			processMessage_Request(message, channel);
		}
		else
			throw new FormatException();
	}
	
	private class AppProcedure
	{
		public final RPCRequestHandler requestHandler;
		public final GuestAccess guestAccess;
		public final String[] roles;
		public final int concurrencyLimit;
		
		public AppProcedure(RPCRequestHandler requestHandler, int concurrencyLimit)
		{
			this.requestHandler = requestHandler;
			this.concurrencyLimit = concurrencyLimit;
			guestAccess = null;
			roles = null;
		}

		public AppProcedure(RPCRequestHandler requestHandler, int concurrencyLimit, GuestAccess guestAccess)
		{
			this.requestHandler = requestHandler;
			this.concurrencyLimit = concurrencyLimit;
			this.guestAccess = guestAccess;
			roles = null;
		}

		public AppProcedure(RPCRequestHandler requestHandler, int concurrencyLimit, String[] roles)
		{
			this.requestHandler = requestHandler;
			this.concurrencyLimit = concurrencyLimit;
			this.roles = roles;
			guestAccess = null;
		}
	}

	private void validateProcedureName(String procedureName)
	{
		if(procedureName == null || procedureName.length() == 0)
			throw new IllegalArgumentException("A procedure name is not allowed to be null or empty.");

		if(procedureName.length() > 256)
	        throw new IllegalArgumentException(String.format("The length of the procedure name '%s' is greater then 256 characters.", procedureName));

		if (Pattern.matches("^[\\u0020-\\u007F]+$", procedureName) == false)			
	        throw new IllegalArgumentException(String.format("The procedure name '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 _.", procedureName));
	
		if (Pattern.matches("^[a-zA-Z].*$", procedureName) == false)
	        throw new IllegalArgumentException(String.format("The leading character in the name of procedure '%s' is illegal. Allowed characters: a-z A-Z.", procedureName));		
	
		if (Pattern.matches("^[a-zA-Z0-9_]+$", procedureName) == false)
	        throw new IllegalArgumentException(String.format("An illegal character in the name of procedure '%s'. Allowed characters: a-z A-Z 0-9 _.", procedureName));			
	}
}
