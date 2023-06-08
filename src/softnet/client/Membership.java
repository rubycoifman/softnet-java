package softnet.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import softnet.asn.*;
import softnet.*;
import softnet.core.*;
import softnet.exceptions.*;
import softnet.utils.Sha1Hash;

class Membership
{
	public Membership(ClientEndpoint clientEndpoint)
	{		
		this.clientEndpoint = clientEndpoint;
		this.endpoint_mutex = clientEndpoint.endpoint_mutex;
		mUser = new MUser();
		eventListeners = new HashSet<ClientEventListener>();
		status = StatusEnum.Disconnected;
	}

	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Client.Membership.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					onMessageReceived(message, _channel);
				}
			});		
		
		status = StatusEnum.Connected;
	}

	public void onClientOnline()
	{
		status = StatusEnum.Online;
	}
	
	public void onEndpointDisconnected()
	{
		status = StatusEnum.Disconnected;
	}

	public void addEventListener(ClientEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.add(listener);
		}
	}

	public void removeEventListener(ClientEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.remove(listener);
		}
	}	

	public MembershipUser getUser()
	{
		synchronized(endpoint_mutex)
		{
			return mUser;
		}				
	}
	
	public byte[] getHash() throws HostFunctionalitySoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(mUser.isGuest())
				return null;
						
    		ASNEncoder asnEncoder = new ASNEncoder();
            SequenceEncoder asnRootSequence = asnEncoder.Sequence();
            asnRootSequence.Int64(mUser.getId());
            asnRootSequence.IA5String(mUser.getName());
            if(mUser.hasRoles())
            {            	
            	ASNEncoder asnEncoder2 = new ASNEncoder();
            	SequenceEncoder asnRoles = asnEncoder2.Sequence();
            	String[] roles = mUser.getRoles();
            	Arrays.sort(roles);
            	for(String role: roles)
            		asnRoles.IA5String(role);
            	asnRootSequence.OctetString(1, asnEncoder2.getEncoding());            	
            }
            return Sha1Hash.compute(asnEncoder.getEncoding());
		}        
	}
	
	private Object endpoint_mutex;

	private enum StatusEnum
	{ 
		Disconnected, Connected, Online
	}
	private StatusEnum status;
	private MUser mUser;
	private ClientEndpoint clientEndpoint;
	private HashSet<ClientEventListener> eventListeners;
	
	private void fireUserUpdatedEvent()
	{		
		final ClientEndpointEvent event = new ClientEndpointEvent(clientEndpoint);		
		synchronized(eventListeners)
		{
			for (final ClientEventListener listener: eventListeners)
			{
				Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onUserUpdated(event);
					}
				};
				clientEndpoint.threadPool.execute(runnable);
		    }
		}
	}
	
	private void ProcessMessage_Guest()
	{
		mUser.setAsGuest();
		if(status == StatusEnum.Online)
			fireUserUpdatedEvent();
	}
	
	private void ProcessMessage_User(byte[] message) throws AsnException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
		long userId = asnRootSequence.Int64();
		String userName = asnRootSequence.IA5String(1, 256);
		ArrayList<String> roles = new ArrayList<String>();
		if(asnRootSequence.exists(1))
		{
			SequenceDecoder asnRoles = asnRootSequence.Sequence();
			while(asnRoles.hasNext())
				roles.add(asnRoles.IA5String(1, 256));
		}
		asnRootSequence.end();
		
		mUser.setAsUser(userId, userName, roles);
		if(status == StatusEnum.Online)
			fireUserUpdatedEvent();
	}
	
	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(channel.closed())
				return;
			
			byte messageTag = message[1]; 
			if(messageTag == Constants.Client.Membership.USER)
			{
				ProcessMessage_User(message);
			}
			else if(messageTag == Constants.Client.Membership.GUEST)
			{
				ProcessMessage_Guest();
			}
			else
				throw new FormatException();
		}
	}
	
	private class MUser implements MembershipUser
	{
		public MUser()
		{
			_isGuest = true;
			name = "Guest";
			userId = 0;
			roles = new ArrayList<String>();
		}
								
		private boolean _isGuest;
	    public boolean isGuest()
	    { 
	    	return _isGuest; 
	    }
	    
        public boolean isStatelessGuest() { return false; }

		private long userId;
		public long getId()
		{
			return userId;
		}
		
		private String name;
	    public String getName()
	    {
	    	return name;
	    }
	    
	    public boolean hasRoles()
	    {
	    	return roles.isEmpty() ? false : true;
	    }
	    	    	    
		private ArrayList<String> roles;
    	public String[] getRoles()
	    {
	    	synchronized(roles)
	    	{
	    		String[] v_roles = new String[roles.size()];
	    		return roles.toArray(v_roles);
	    	}
	    }
	    
	    public boolean isInRole(String role)
	    {
	    	synchronized(roles)
	    	{
		    	for (String item: roles)
	            {
	                if (role.equals(item))
	                    return true;
	            }
	            return false;
	    	}
	    }

	    public boolean isRemoved()
	    {
	    	return false;
	    }
	    	                    	    
	    protected void setAsGuest()
	    {
	    	_isGuest = true;
	    	userId = 0;
	    	name = "Guest";
	    	synchronized(roles)
        	{
        		roles.clear();
        	}
	    }

	    protected void setAsUser(long userId, String name, ArrayList<String> roles)
	    {
	    	_isGuest = false;	
	    	this.userId = userId;
	    	this.name = name;
	    	this.roles = roles;
	    }	    
	}		
}
