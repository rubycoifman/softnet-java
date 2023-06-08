package softnet.service;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.lang.Comparable;
import java.util.Comparator;

import softnet.*;
import softnet.asn.*;
import softnet.utils.*;
import softnet.core.*;
import softnet.exceptions.*;

class RBMembership implements Membership
{
	public RBMembership(SiteStructureAdapter siteStructure, ServiceEndpoint serviceEndpoint, Object endpoint_mutex)
	{
		this.siteStructure = siteStructure;
		this.serviceEndpoint = serviceEndpoint;
		this.endpoint_mutex = endpoint_mutex;
		
		mRoles = new ArrayList<MRole>();
		mUsers = new ArrayList<MUser>();
		eventListeners = new HashSet<ServiceEventListener>(2);		
		
		guest = siteStructure.isGuestSupported() ? new Guest() : null;
		statelessGuest = siteStructure.isStatelessGuestSupported() ? new StatelessGuest() : null;
		is_guest_allowed = false;

		connectivity_status = StatusEnum.Disconnected;
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
	
	public MembershipUser findUser(long userId)
	{
		synchronized(endpoint_mutex)
		{
			for(MUser mUser: mUsers)
			{
				if(mUser.userId == userId)
					return mUser;
			}
			return null;
		}
	}
	
	public MembershipUser findUser(String userName)
	{
		synchronized(endpoint_mutex)
		{
			for(MUser mUser: mUsers)
			{
				if(mUser.userName.equals(userName))
					return mUser;
			}
			return null;
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
		return true;
	}
	
	public boolean containsRole(String role)
	{
		return siteStructure.containsRole(role);
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

	private Object endpoint_mutex;

	private enum StatusEnum
	{ 
		Disconnected, Connected, Online
	}
	private StatusEnum connectivity_status;
		
	private HashSet<ServiceEventListener> eventListeners;
	private ServiceEndpoint serviceEndpoint;
	private SiteStructureAdapter siteStructure;
	private ArrayList<MRole> mRoles;
	private ArrayList<MUser> mUsers;
	private Guest guest;
	private StatelessGuest statelessGuest;
	private boolean is_guest_allowed;
	
	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Service.RBMembership.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					OnMessageReceived(message, _channel);
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
			if(mRoles.isEmpty())
				return null;
			
			ASNEncoder asnEncoder = new ASNEncoder();
            SequenceEncoder asnRootSequence = asnEncoder.Sequence();

            Comparator<MRole> roleComparator = new Comparator<MRole>()
			{         
			    @Override
			    public int compare(MRole role1, MRole role2)
			    {      
			    	if(role1.roleId > role2.roleId)
			    		return 1;
			    	else if(role1.roleId < role2.roleId)
			    		return -1;
			    	else 
			    		return 0;
			    }     
			};
			
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
			
            SequenceEncoder asnRoles = asnRootSequence.Sequence();
			Collections.sort(mRoles, roleComparator);
            for (MRole mRole: mRoles)
            {
                SequenceEncoder asnRole = asnRoles.Sequence();
                asnRole.Int64(mRole.roleId);
                asnRole.IA5String(mRole.name);
            }

            SequenceEncoder asnUsers = asnRootSequence.Sequence();
            if (mUsers.size() > 0)
            {
                Collections.sort(mUsers, userComparator);
                for (MUser mUser: mUsers)
                {
                	SequenceEncoder asnUser = asnUsers.Sequence();
                	asnUser.Int64(mUser.userId);
                    asnUser.IA5String(mUser.userName);
                    
                    SequenceEncoder asnUserRoles = asnUser.Sequence();         
                    
                    Collections.sort(mUser.userRoles, roleComparator);                    
                    for(MRole mRole: mUser.userRoles)
                    {
                    	asnUserRoles.Int64(mRole.roleId);
                    }
                }
            }
            
            return Sha1Hash.compute(asnEncoder.getEncoding());
		}
	}	

	private void ProcessMessage_UserList(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);		
		SequenceDecoder asnRoles = asnRootSequence.Sequence();
		SequenceDecoder asnUsers = asnRootSequence.Sequence();
		asnRootSequence.end();		
		
		mRoles.clear();		
		while(asnRoles.hasNext())
		{
			SequenceDecoder asnRole = asnRoles.Sequence();
			MRole mRole = new MRole(asnRole.Int64(), asnRole.IA5String(1, 256));	
			
			if(siteStructure.containsRole(mRole.name) == false)
				throw new InputDataInconsistentSoftnetException();
			
			mRoles.add(mRole);
		}
		asnRoles.end();		
		
		String[] ssRoles = siteStructure.getRoles();
		for(String ssRole: ssRoles)
		{
			boolean matchFound = false;
			for(int i = 0; i < mRoles.size(); i++)
			{
				MRole mRole = mRoles.get(i);
				if(ssRole.equals(mRole.name))
				{
					matchFound = true;
					mRole.orderNum = i;
					break;
				}
			}
			if(matchFound == false)
				throw new InputDataInconsistentSoftnetException();
		}
		
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
			
			ArrayList<MRole> userRoles = new ArrayList<MRole>();			
			SequenceDecoder asnUserRoles = asnUser.Sequence();
			while(asnUserRoles.hasNext())
			{
				long roleId = asnUserRoles.Int64();
				
				MRole userRole = null;
				for(MRole mRole: mRoles)
				{
					if(mRole.roleId == roleId)
					{
						userRole = mRole;
						break;
					}
				}				
				if(userRole == null)
					throw new InputDataInconsistentSoftnetException();
				
				userRoles.add(userRole);
			}
			asnUserRoles.end();
			user.setRoles(userRoles);
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
        SequenceDecoder asnUserRoles = asnRootSequence.Sequence();
        asnRootSequence.end();

        for(MUser user: mUsers)
        {
        	if(user.userId == userId)
        		throw new InputDataInconsistentSoftnetException();
        }
        
        MUser newUser = new MUser(userId, userName);
        
        ArrayList<MRole> userRoles = new ArrayList<MRole>();			
		while(asnUserRoles.hasNext())
		{
			long roleId = asnUserRoles.Int64();
			
			MRole userRole = null;
			for(MRole mRole: mRoles)
			{
				if(mRole.roleId == roleId)
				{
					userRole = mRole;
					break;
				}
			}				
			if(userRole == null)
				throw new InputDataInconsistentSoftnetException();
			
			userRoles.add(userRole);
		}
		asnUserRoles.end();
		newUser.setRoles(userRoles);
		mUsers.add(newUser);
        
        fireUserIncludedEvent(newUser);
	}

	private void ProcessMessage_UserUpdated(byte[] message) throws AsnException, SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
        long userId = asnRootSequence.Int64();
        String userName = asnRootSequence.IA5String(1, 256);
        SequenceDecoder asnUserRoles = asnRootSequence.Sequence();
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
        
        ArrayList<MRole> userRoles = new ArrayList<MRole>();			
		while(asnUserRoles.hasNext())
		{
			long roleId = asnUserRoles.Int64();
			
			MRole userRole = null;
			for(MRole mRole: mRoles)
			{
				if(mRole.roleId == roleId)
				{
					userRole = mRole;
					break;
				}
			}				
			if(userRole == null)
				throw new InputDataInconsistentSoftnetException();
			
			userRoles.add(userRole);
		}
		asnUserRoles.end();

    	updatedUser.setName(userName);
    	updatedUser.setRoles(userRoles);
    	
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

	private void ProcessMessage_GuestAllowed() throws AsnException
	{
		if(siteStructure.isGuestSupported() == false)
			return;		
		is_guest_allowed = true;
		fireGuestAccessChangedEvent();
	}

	private void ProcessMessage_GuestDenied() throws AsnException
	{
		if(siteStructure.isGuestSupported() == false)
			return;	
		is_guest_allowed = false;
		fireGuestAccessChangedEvent();
	}

	private void OnMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(channel.isClosed()) 
				return;
			
			byte messageTag = message[1];
			if(connectivity_status == StatusEnum.Online)
			{	
				if(messageTag == Constants.Service.RBMembership.USER_LIST)
				{
					ProcessMessage_UserList(message);	
				}
				else if(messageTag == Constants.Service.RBMembership.USER_INCLUDED)
				{
					ProcessMessage_UserIncluded(message);
				}
				else if(messageTag == Constants.Service.RBMembership.USER_UPDATED)
				{
					ProcessMessage_UserUpdated(message);
				}
				else if(messageTag == Constants.Service.RBMembership.USER_REMOVED)
				{
					ProcessMessage_UserRemoved(message);
				}
				else if(messageTag == Constants.Service.RBMembership.GUEST_ALLOWED)
				{
					ProcessMessage_GuestAllowed();
				}
				else if(messageTag == Constants.Service.RBMembership.GUEST_DENIED)
				{
					ProcessMessage_GuestDenied();
				}
				else
					throw new FormatException();
			}
			else if(connectivity_status == StatusEnum.Connected)
			{
				if(messageTag == Constants.Service.RBMembership.USER_LIST)
				{
					ProcessMessage_UserList(message);
				}
				else if(messageTag == Constants.Service.RBMembership.GUEST_ALLOWED)
				{
					ProcessMessage_GuestAllowed();
				}
				else if(messageTag == Constants.Service.RBMembership.GUEST_DENIED)
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
		final ServiceEndpointEvent event = new ServiceEndpointEvent(serviceEndpoint);		
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
				serviceEndpoint.threadPool.execute(runnable);
		    }
		}
	}
	
	private void fireUserIncludedEvent(MembershipUser user)
	{
		final MembershipUserEvent event = new MembershipUserEvent(user, serviceEndpoint);		
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
				serviceEndpoint.threadPool.execute(runnable);
		    }
		}
	}

	private void fireUserUpdatedEvent(MembershipUser user)
	{
		final MembershipUserEvent event = new MembershipUserEvent(user, serviceEndpoint);		
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
				serviceEndpoint.threadPool.execute(runnable);
		    }
		}
	}
	
	private void fireUserRemovedEvent(MembershipUser user)
	{
		final MembershipUserEvent event = new MembershipUserEvent(user, serviceEndpoint);		
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
				serviceEndpoint.threadPool.execute(runnable);
		    }
		}
	}
		
	private void fireGuestAccessChangedEvent()
	{
		final ServiceEndpointEvent event = new ServiceEndpointEvent(serviceEndpoint);		
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
				serviceEndpoint.threadPool.execute(runnable);
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

	private class MRole
	{
		public MRole(long roleId, String name)
		{
			this.roleId = roleId;
			this.name = name;
		}
		public final long roleId;
		public final String name;
		public int orderNum;
	}
	
 	private class MUser implements MembershipUser, Comparable<MUser>
	{
		public MUser(long userId, String name)
		{
			this.userId = userId;
			this.userName = name;
			userRoles = null;
		}
		
		private Object mutex = new Object();
								
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
	    	    	    
		private ArrayList<MRole> userRoles;
    	public String[] getRoles()
	    {
    		Comparator<MRole> roleComparator = new Comparator<MRole>()
			{         
			    @Override
			    public int compare(MRole role1, MRole role2)
			    {      
			    	if(role1.orderNum > role2.orderNum)
			    		return 1;
			    	else if(role1.orderNum < role2.orderNum)
			    		return -1;
			    	else 
			    		return 0;
			    }     
			};
			
	    	synchronized(mutex)
	    	{
	    		Collections.sort(userRoles, roleComparator);	    		
	    		String[] roles = new String[userRoles.size()];
	    		for(int i = 0; i < userRoles.size(); i++)
	    			roles[i] = userRoles.get(i).name;	    			
	    		return roles;
	    	}
	    }
    	
    	public boolean hasRoles()
	    {
	    	return userRoles.isEmpty() ? false : true;
	    }
	    
	    public boolean isInRole(String role)
	    {
	    	synchronized(mutex)
	    	{
		    	for (MRole mRole: userRoles)
	            {
	                if (mRole.name.equals(role))
	                    return true;
	            }
	            return false;
	    	}
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
            MUser otherUser = (MUser)other;
            return this.userId == otherUser.userId;
        }
        
        @Override
        public int hashCode() 
        {
            return Long.hashCode(this.userId);
        }
	    //--------------------------------
	            
        public void setName(String name)
        {
            userName = name;
        }

        public void setRoles(ArrayList<MRole> roles)
        {
        	synchronized(mutex)
        	{
        		userRoles = roles;
        	}
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

        public boolean hasRoles()
	    {
	    	return false;
	    }
        
        public String[] getRoles()
        {
            return new String[0];
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
            return "Stateless Guest";
        }
        
        public boolean hasRoles()
	    {
	    	return false;
	    }

        public String[] getRoles()
        {
            return new String[0];
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
