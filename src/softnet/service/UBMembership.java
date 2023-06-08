package softnet.service;

import java.util.*;
import softnet.asn.*;
import softnet.*;
import softnet.core.*;
import softnet.exceptions.*;
import softnet.utils.Sha1Hash;

class UBMembership implements Membership
{
	public UBMembership(SiteStructureAdapter siteStructure, ServiceEndpoint softnetService, Object endpoint_mutex)
	{
		this.softnetService = softnetService;
		this.siteStructure = siteStructure;
		this.endpoint_mutex = endpoint_mutex;
		
		eventListeners = new HashSet<ServiceEventListener>(1);		
		mUsers = new ArrayList<MUser>();
		
		guest = siteStructure.isGuestSupported() ? new Guest() : null;
		statelessGuest = siteStructure.isStatelessGuestSupported() ? new StatelessGuest() : null;		
		is_guest_allowed = false;
	
		connectivity_status = StatusEnum.Disconnected;
	}
		
	public MembershipUser findUser(long userId)
	{
		synchronized(endpoint_mutex)
		{
			return findMembershipUser(userId);
		}
	}
	
	public MembershipUser findUser(String userName)
	{
		synchronized(endpoint_mutex)
		{
			return findMembershipUser(userName);
		}
	}
	
	public MembershipUser resolve(int userKind, long userId)
	{
		synchronized(endpoint_mutex)
		{
			if(Constants.UserKind.Owner <= userKind && userKind <= Constants.UserKind.Contact)
			{
				MembershipUser user = findMembershipUser(userId);
				if(user != null)
					return user;					
				if(is_guest_allowed)
					return guest;
				return null;
			}
			else if(userKind == Constants.UserKind.Guest)
			{
				if(is_guest_allowed)
					return guest;
				return null;
			}
			else // userKind == Constants.UserKind.StatelessGuest
			{
				if(is_guest_allowed)
					return statelessGuest;
				return null;
			}			
		}
	}
		
	public boolean isGuestAllowed()
	{
		return is_guest_allowed;
	}

	public boolean isGuestSupported()
	{
		return siteStructure.isGuestSupported();
	}
	
	public boolean isStatelessGuestSupported()
	{
		return siteStructure.isStatelessGuestSupported();
	}
	
	public boolean isRbacSupported()
	{
		return false;
	}
	
	public boolean containsRole(String role)
	{
		return false;
	}
		
	public MembershipUser[] getUsers()
	{
		synchronized(endpoint_mutex)
		{
			MembershipUser[] users = new MembershipUser[mUsers.size()];
			mUsers.toArray(users);
	
			Comparator<MembershipUser> nameComparator = new Comparator<MembershipUser>()
			{         
			    @Override
			    public int compare(MembershipUser user1, MembershipUser user2)
			    {             
			    	return (user1.getName().compareTo(user2.getName()));           
			    }     
			};    			
			
			Arrays.sort(users, nameComparator);			
			return users;
		}
	}
	
	public ArrayList<MembershipUser> getUsers2()
	{
		synchronized(endpoint_mutex)
		{
			ArrayList<MembershipUser> users = new ArrayList<MembershipUser>(mUsers.size());
			
			for(MUser user: mUsers)
				users.add(user);
			
			Comparator<MembershipUser> nameComparator = new Comparator<MembershipUser>()
			{         
			    @Override
			    public int compare(MembershipUser user1, MembershipUser user2)
			    {             
			    	return (user1.getName().compareTo(user2.getName()));           
			    }     
			};    			
			Collections.sort(users, nameComparator);
			
			return users;
		}
	}
	
	private Object endpoint_mutex;

	private enum StatusEnum
	{ 
		Disconnected, Connected, Online
	}
	private StatusEnum connectivity_status;
	
	private ServiceEndpoint softnetService;
	private SiteStructureAdapter siteStructure;
	private ArrayList<MUser> mUsers;
	private Guest guest;
	private StatelessGuest statelessGuest;
	private boolean is_guest_allowed;
	private HashSet<ServiceEventListener> eventListeners;

	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Service.UBMembership.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					onMessageReceived(message, _channel);
				}
			});		
		
		connectivity_status = StatusEnum.Connected;
	}

	public void onServiceOnline()
	{
		connectivity_status = StatusEnum.Online;
	}

	public void onEndpointDisconnected()
	{
		connectivity_status = StatusEnum.Disconnected;
	}
			
	public byte[] getHash() throws HostFunctionalitySoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(mUsers.isEmpty())
				return null;
			
			ASNEncoder asnEncoder = new ASNEncoder();
            SequenceEncoder asnRootSequence = asnEncoder.Sequence();

			Comparator<MUser> userComparator = new Comparator<MUser>()
			{         
			    @Override
			    public int compare(MUser user1, MUser user2)
			    {      
			    	if(user1.userId > user2.userId)
			    		return 1;
			    	else if(user1.userId < user2.userId)
			    		return -1;
			    	else 
			    		return 0;
			    }     
			};    			
			
            SequenceEncoder asnUsers = asnRootSequence.Sequence();
            Collections.sort(mUsers, userComparator);            
            for (MUser mUser: mUsers)
            {
            	SequenceEncoder asnUser = asnUsers.Sequence();
            	asnUser.Int64(mUser.userId);
                asnUser.IA5String(mUser.userName);
            }
            
            return Sha1Hash.compute(asnEncoder.getEncoding());
		}
	}	
	
	public void addEventListener(ServiceEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.add(listener);
		}
	}

	public void removeEventListener(ServiceEventListener listener)
	{
		synchronized(eventListeners)
		{
			eventListeners.remove(listener);
		}
	}

	private void ProcessMessage_UserList(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnUsers = ASNDecoder.Sequence(message, 2);		
				
		ArrayList<MUser> users = new ArrayList<MUser>();
		while(asnUsers.hasNext())
		{
			SequenceDecoder asnUser = asnUsers.Sequence();
			long userId = asnUser.Int64();
			String userName = asnUser.IA5String(1, 256);
			
			MUser user = null;
			for(MUser mUser: mUsers)
			{
				if(userId == mUser.userId)
				{
					user = mUser;					
					break;
				}
			}
			
			if(user != null)
				user.setName(userName);
			else
				user = new MUser(userId, userName);			
			users.add(user);			
		}
		asnUsers.end();		
				
		for(MUser mUser: mUsers)
		{
			boolean userRemoved = true;
			for(MUser receivedUser: users)
			{
				if(receivedUser.userId == mUser.userId)
				{					
					userRemoved = false;
					break;
				}
			}
			
			if(userRemoved)
				mUser.setRemoved();			
		}				
		mUsers = users;		
		
		fireUsersUpdatedEvent();
	}

	private void ProcessMessage_UserIncluded(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        long userId = asnRootSequence.Int64();
        String userName = asnRootSequence.IA5String(1, 256);
        asnRootSequence.end();

        for(MUser user: mUsers)
        {
        	if(user.userId == userId)
        		throw new InputDataInconsistentSoftnetException();
        }
        
        MUser newUser = new MUser(userId, userName);        
        mUsers.add(newUser);
        
        fireUserIncludedEvent(newUser);
	}

	private void ProcessMessage_UserUpdated(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        long userId = asnRootSequence.Int64();
        String userName = asnRootSequence.IA5String(1, 256);
        asnRootSequence.end();

        MUser updatedUser = null;
        for(MUser user: mUsers)
        {
        	if(user.userId == userId)
        	{
        		updatedUser = user;
        		break;
        	}
        }
        
    	if(updatedUser == null)
    		throw new InputDataInconsistentSoftnetException();    	
    	updatedUser.setName(userName);
    	
        fireUserUpdatedEvent(updatedUser);
	}

	private void ProcessMessage_UserRemoved(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        long userId = asnRootSequence.Int64();
        asnRootSequence.end();

        MUser removedUser = null;
        for(MUser user: mUsers)
        {
        	if(user.userId == userId)
        	{
        		removedUser = user;
        		break;
        	}
        }
    	if(removedUser == null)
    		throw new InputDataInconsistentSoftnetException();
    	
    	mUsers.remove(removedUser);
    	removedUser.setRemoved();
        
        fireUserRemovedEvent(removedUser);
	}

	private void ProcessMessage_GuestAllowed()
	{
		if(siteStructure.isGuestSupported() == false)
			return;	
		is_guest_allowed = true;		
		fireGuestAccessChangedEvent();
	}

	private void ProcessMessage_GuestDenied() 
	{
		if(siteStructure.isGuestSupported() == false)
			return;	
		is_guest_allowed = false;
		fireGuestAccessChangedEvent();
	}

	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(channel.isClosed())
				return;
			
			byte messageTag = message[1]; 
			if(connectivity_status == StatusEnum.Online)
			{	
				if(messageTag == Constants.Service.UBMembership.USER_LIST)
				{
					ProcessMessage_UserList(message);	
				}
				else if(messageTag == Constants.Service.UBMembership.USER_INCLUDED)
				{
					ProcessMessage_UserIncluded(message);
				}
				else if(messageTag == Constants.Service.UBMembership.USER_UPDATED)
				{
					ProcessMessage_UserUpdated(message);
				}
				else if(messageTag == Constants.Service.UBMembership.USER_REMOVED)
				{
					ProcessMessage_UserRemoved(message);
				}
				else if(messageTag == Constants.Service.UBMembership.GUEST_ALLOWED)
				{
					ProcessMessage_GuestAllowed();
				}
				else if(messageTag == Constants.Service.UBMembership.GUEST_DENIED)
				{
					ProcessMessage_GuestDenied();
				}
				else 
					throw new FormatException();
			}
			else if(connectivity_status == StatusEnum.Connected)
			{
				if(messageTag == Constants.Service.UBMembership.USER_LIST)
				{
					ProcessMessage_UserList(message);
				}
				else if(messageTag == Constants.Service.UBMembership.GUEST_ALLOWED)
				{
					ProcessMessage_GuestAllowed();
				}
				else if(messageTag == Constants.Service.UBMembership.GUEST_DENIED)
				{
					ProcessMessage_GuestDenied();
				}
				else 
					throw new FormatException();
			}
		}
	}
		
	private void fireUsersUpdatedEvent()
	{
		final ServiceEndpointEvent event = new ServiceEndpointEvent(softnetService);		
		synchronized(eventListeners)
		{
			for (final ServiceEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onUsersUpdated(event);
					}
				};
				softnetService.threadPool.execute(runnable);
		    }
		}
	}
	
	private void fireUserIncludedEvent(MembershipUser user)
	{
		final MembershipUserEvent event = new MembershipUserEvent(user, softnetService);		
		synchronized(eventListeners)
		{
			for (final ServiceEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onUserIncluded(event);
					}
				};
				softnetService.threadPool.execute(runnable);
		    }
		}
	}
	
	private void fireUserRemovedEvent(MembershipUser user)
	{
		final MembershipUserEvent event = new MembershipUserEvent(user, softnetService);		
		synchronized(eventListeners)
		{
			for (final ServiceEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onUserRemoved(event);
					}
				};
				softnetService.threadPool.execute(runnable);
		    }
		}
	}
	
	private void fireUserUpdatedEvent(MembershipUser user)
	{
		final MembershipUserEvent event = new MembershipUserEvent(user, softnetService);		
		synchronized(eventListeners)
		{
			for (final ServiceEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onUserUpdated(event);
					}
				};
				softnetService.threadPool.execute(runnable);
		    }
		}	
	}

	private void fireGuestAccessChangedEvent()
	{
		final ServiceEndpointEvent event = new ServiceEndpointEvent(softnetService);		
		synchronized(eventListeners)
		{
			for (final ServiceEventListener listener: eventListeners)
			{
				final Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						listener.onGuestAccessChanged(event);
					}
				};
				softnetService.threadPool.execute(runnable);
		    }
		}
	}
	
	private MUser findMembershipUser(long userId)
	{
		for(MUser mUser: mUsers)
		{
			if(mUser.userId == userId)
				return mUser;
		}
		return null;
	}

	private MUser findMembershipUser(String userName)
	{
		for(MUser mUser: mUsers)
		{
			if(mUser.userName.equals(userName))
				return mUser;
		}
		return null;
	}
	
 	private class MUser implements MembershipUser, Comparable<MUser>
	{
		public MUser(long userId, String userName)
		{
			this.userId = userId;
			this.userName = userName;
		}
		
	    public boolean isGuest() { return false; }
        public boolean isStatelessGuest() { return false; }

		private long userId;
		public long getId()
		{
			return userId;
		}
		
		private String userName;
	    public String getName()
	    {
	    	return userName;
	    }
	    	    	    
		private String[] userRoles = new String[0];
    	public String[] getRoles()
	    {
    		return userRoles;
	    }
    	
    	public boolean hasRoles()
	    {
	    	return userRoles.length > 0 ? true : false;
	    }
	    
	    public boolean isInRole(String role)
	    {
	        return false;
	    }

	    private boolean _isRemoved = false;
	    public boolean isRemoved()
	    {
	    	return _isRemoved;
	    }
	    
	    @Override
	    public int compareTo(MUser other)
        {
	    	if(this.userId > other.userId)
	    		return 1;
	    	else if (this.userId < other.userId)
	    		return -1;
	    	else	    		
	    		return 0;
        }
        
        @Override
        public boolean equals(Object other) 
        {
        	if (other == null) return false;
            MUser user = (MUser)other;
            return this.userId == user.userId;
        }
        
        @Override
        public int hashCode() 
        {
            return Long.hashCode(this.userId);
        }
	    //--------------------------------
	            
        public void setName(String userName)
        {
            this.userName = userName;
        }
        
        public void setRemoved()
        {
        	_isRemoved = true;
        }
	}	

    private class Guest implements MembershipUser
    {
        public boolean isGuest() { return true; }
        public boolean isStatelessGuest() { return false; }

        public long getId()
        {
            return 0;
        }

        public String getName()
        {
            return "Guest";
        }

        public String[] getRoles()
	    {
	    	return new String[0];
	    }
        
        public boolean hasRoles()
	    {
	    	return false;
	    }
	    
	    public boolean isInRole(String role)
	    {
	    	return false;
	    }

        public boolean isRemoved()
        {
            return false;
        }
    }

    private class StatelessGuest implements MembershipUser
    {
        public boolean isGuest() { return true; }
        public boolean isStatelessGuest() { return true; }

        public long getId()
        {
            return 0;
        }

        public String getName()
        {
            return "Guest";
        }

        public String[] getRoles()
        {
	    	return new String[0];
        }

        public boolean hasRoles()
	    {
	    	return false;
	    }

        public boolean isInRole(String role)
        {
        	return false;
        }

        public boolean isRemoved()
        {
            return false;
        }
    }
}
