package softnet.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.*;

class SiteStructureAdapter implements SiteStructure
{	
	public final String serviceType;
	public final String contractAuthor;
				
	private boolean is_guest_supported;
	public boolean isGuestSupported()
	{
		return is_guest_supported;
	}

	private boolean is_stateless_guest_supported;
	public boolean isStatelessGuestSupported()
	{
		return is_stateless_guest_supported;
	}
		
	public boolean isRbacSupported()
	{
		return roleArray != null;
	}

	public boolean areEventsSupported()
	{
		return replacingEvents.size() > 0 || queueingEvents.size() > 0 || privateEvents.size() > 0;
	}
	
	private String ownerRole;
    public String ownerRole()
    {
        return ownerRole;
    }
    
	private String[] roleArray;
	public String[] getRoles()
	{
		if(roleArray == null)
			return null;		
		return Arrays.copyOf(roleArray, roleArray.length);
	}
		
	public boolean containsReplacingEvents()
	{
		return replacingEvents.size() > 0;
	}

	public boolean containsReplacingEvent(String eventName)
	{
		for(REvent evt: replacingEvents)
		{
			if(evt.name.equals(eventName))
				return true;
		}	
		return false;
	}

	private ArrayList<REvent> replacingEvents;
	public ArrayList<REvent> getReplacingEvents()
	{
		if(replacingEvents.size() == 0)
			return null;
		return new ArrayList<REvent>(replacingEvents);
	}

	public boolean containsQueueingEvents()
	{
		return queueingEvents.size() > 0;
	}

	public boolean containsQueueingEvent(String eventName)
	{
		for(QEvent evt: queueingEvents)
		{
			if(evt.name.equals(eventName))
				return true;
		}	
		return false;
	}

	private ArrayList<QEvent> queueingEvents;
	public ArrayList<QEvent> getQueueingEvents()
	{
		if(queueingEvents.size() == 0)
			return null;	
		return new ArrayList<QEvent>(queueingEvents);
	}	

	public boolean containsPrivateEvents()
	{
		return privateEvents.size() > 0;
	}

	public boolean containsPrivateEvent(String eventName)
	{
		for(PEvent evt: privateEvents)
		{
			if(evt.name.equals(eventName))
				return true;
		}	
		return false;
	}

	private ArrayList<PEvent> privateEvents;
	public ArrayList<PEvent> getPrivateEvents()
	{
		if(privateEvents.size() == 0)
			return null;	
		return new ArrayList<PEvent>(privateEvents);
	}

	public SiteStructureAdapter(String serviceType, String contractAuthor)
	{
		validateServiceType(serviceType);
		validateContractAuthor(contractAuthor);
						
		this.serviceType = serviceType;
		this.contractAuthor = contractAuthor;
		is_guest_supported = false;
		is_stateless_guest_supported = false;
		roleArray = null;
		ownerRole = null;
		replacingEvents = new ArrayList<REvent>();
		queueingEvents = new ArrayList<QEvent>();
		privateEvents = new ArrayList<PEvent>();
 	}
	
    private Object mutex = new Object();
	
	boolean is_read_only = false;
	public void commit()
	{
		synchronized(mutex)
		{
			is_read_only = true;
			
			if(replacingEvents.size() > 0)
			{
				Collections.sort(replacingEvents);
				for(REvent evt: replacingEvents)
				{
					if(evt.roles != null)
						Arrays.sort(evt.roles);
				}
			}
			
			if(queueingEvents.size() > 0)
			{
				Collections.sort(queueingEvents);
				for(QEvent evt: queueingEvents)
				{
					if(evt.roles != null)
						Arrays.sort(evt.roles);
				}
			}
						
			Collections.sort(privateEvents);
		}
	}

	public void setGuestSupport()
	{
		if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");
		is_guest_supported = true;
	}
	
	public void setStatelessGuestSupport()
	{
		if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");
		is_stateless_guest_supported = true;
	}
	
	public void setRoles(String roles)
	{
		if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");
		
		if(roles == null || roles.isEmpty())
			throw new IllegalArgumentException("The list of roles must not be null or empty.");
		
		String[] roleNames = roles.split(";");
		for(int i = 0; i < roleNames.length; i++)
			roleNames[i] = roleNames[i].trim();
				
		synchronized(mutex)
		{
			for(String roleName: roleNames)
				validateRoleName(roleName);				
		
			for (int i = 0; i < roleNames.length; i++)
			{ 
				for (int j = i + 1 ; j < roleNames.length; j++) 
				{ 
					if (roleNames[i].equalsIgnoreCase(roleNames[j]))
						throw new IllegalArgumentException(String.format("The role name '%s' is duplicated.", roleNames[i]));
				}
			}
			
			roleArray = roleNames;
		}		
	}
		
	public boolean containsRole(String role)
	{
		if(roleArray == null)
			return false;
		for(String item: roleArray)
		{
			if(item.equals(role))
				return true;
		}
		return false;
	}
	
	public void setOwnerRole(String role) 
    {
		if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");

		if(role == null)
			throw new IllegalArgumentException("The role name is null.");
		
		synchronized(mutex)
		{
			if (containsRole(role) == false)
				throw new IllegalArgumentException(String.format("The name '%s' is not in the list of roles.", role));			
	        ownerRole = role;
		}
    }
        
    public void addReplacingEvent(String eventName)
    {
    	if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");

    	validateEventName(eventName);
		
    	synchronized(mutex)
    	{   
    		if(containsEvent(eventName))
    			throw new IllegalArgumentException(String.format("The event '%s' is already in the list of events.", eventName));
    		
    		REvent event = new REvent(eventName);
    		replacingEvents.add(event);    		
    	}
    }
    
    public void addReplacingEvent(String eventName, GuestAccess guestAccess)
    {
    	if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");
    	
    	validateEventName(eventName);
    	
		if(guestAccess == null)
			throw new IllegalArgumentException("The value of 'guestAccess' must not be null.");
		
    	synchronized(mutex)
    	{    		    		
    		if(containsEvent(eventName))
    			throw new IllegalArgumentException(String.format("The event '%s' is already in the list of events.", eventName));

    		REvent event = new REvent(eventName, guestAccess);
    		replacingEvents.add(event);
    	}
    }
    	
    public void addReplacingEvent(String eventName, String roles)
    {
    	if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");
    	
    	validateEventName(eventName);
    					
		if(roles == null || roles.length() == 0)
			throw new IllegalArgumentException("The list of roles must not be null or empty");
		
		String[] roleNames = roles.split(";");
		for(int i = 0; i < roleNames.length; i++)
			roleNames[i] = roleNames[i].trim();
		
    	synchronized(mutex)
    	{    		    		    		    		
    		if(containsEvent(eventName))
    			throw new IllegalArgumentException(String.format("The event '%s' is already in the list of events.", eventName));

    		for(String roleName: roleNames)
    		{
    			if(roleName.length() == 0)
    				throw new IllegalArgumentException("The role name must not be empty");
    			if(containsRole(roleName) == false)
    				throw new IllegalArgumentException(String.format("The role '%s' is not in the list of roles.", roleName));
    		}
    		
    		REvent event = new REvent(eventName, roleNames);
    		replacingEvents.add(event);
    	}
    }
    
    public void addQueueingEvent(String eventName, int lifeTime, int maxQueueSize)
    {
    	if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");

    	validateEventName(eventName);
		
		if(lifeTime < 60)
			throw new IllegalArgumentException("The event's lifetime must not be less then 1 minute.");		
		if(lifeTime > 2592000)
			throw new IllegalArgumentException("The event's lifetime must not be more then 30 days.");

		if(maxQueueSize < 1 || maxQueueSize > 1000)
			throw new IllegalArgumentException("The queue size must be in the range [1, 1000].");

    	synchronized(mutex)
    	{    		    		
    		if(containsEvent(eventName))
    			throw new IllegalArgumentException(String.format("The event '%s' is already in the list of events.", eventName));
    		
    		QEvent event = new QEvent(eventName, lifeTime, maxQueueSize);
    		queueingEvents.add(event);
    	}
    }

    public void addQueueingEvent(String eventName, int lifeTime, int maxQueueSize, GuestAccess guestAccess)
    {
    	if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");

    	validateEventName(eventName);
		
		if(lifeTime < 60)
			throw new IllegalArgumentException("The event's lifetime must not be less then 1 minute.");		
		if(lifeTime > 2592000)
			throw new IllegalArgumentException("The event's lifetime must not be more then 30 days.");

		if(maxQueueSize < 1 || maxQueueSize > 1000)
			throw new IllegalArgumentException("The queue size must be in the range [1, 1000].");

		if(guestAccess == null)
			throw new IllegalArgumentException("The value of 'guestAccess' must not be null.");

    	synchronized(mutex)
    	{   
    		if(containsEvent(eventName))
    			throw new IllegalArgumentException(String.format("The event '%s' is already in the list of events.", eventName));
    		
    		QEvent event = new QEvent(eventName, lifeTime, maxQueueSize, guestAccess);
    		queueingEvents.add(event);
    	}
    }
    
    public void addQueueingEvent(String eventName, int lifeTime, int maxQueueSize, String roles)
    {
    	if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");

    	validateEventName(eventName);
		
		if(lifeTime < 60)
			throw new IllegalArgumentException("The event's lifetime must not be less then 1 minute.");		
		if(lifeTime > 2592000)
			throw new IllegalArgumentException("The event's lifetime must not be more then 30 days.");

		if(maxQueueSize < 1 || maxQueueSize > 1000)
			throw new IllegalArgumentException("The queue size must be in the range [1, 1000].");		

		if(roles == null || roles.length() == 0)
			throw new IllegalArgumentException("The list of roles must not be null or empty");
				
		String[] roleNames = roles.split(";");
		for(int i = 0; i < roleNames.length; i++)
			roleNames[i] = roleNames[i].trim();
		
    	synchronized(mutex)
    	{   
    		if(containsEvent(eventName))
    			throw new IllegalArgumentException(String.format("The event '%s' is already in the list of events.", eventName));

    		for(String roleName: roleNames)
    		{
    			if(roleName.length() == 0)
    				throw new IllegalArgumentException("The role name must not be empty");
    			if(containsRole(roleName) == false)
    				throw new IllegalArgumentException(String.format("The role '%s' is not in the list of roles.", roleName));
    		}
    		    		
    		QEvent event = new QEvent(eventName, lifeTime, maxQueueSize, roleNames);
    		queueingEvents.add(event);
    	}
    }
        
    public void addPrivateEvent(String eventName, int lifeTime)
    {
    	if(is_read_only)
			throw new IllegalStateException("The site structure is in the readonly mode.");

    	validateEventName(eventName);
		
		if(lifeTime < 60)
			throw new IllegalArgumentException("The event's lifetime must not be less then 1 minute.");		
		if(lifeTime > 2592000)
			throw new IllegalArgumentException("The event's lifetime must not be more then 30 days.");
		
    	synchronized(mutex)
    	{   
    		if(containsEvent(eventName))
    			throw new IllegalArgumentException(String.format("The event '%s' is already in the list of events.", eventName));
    		
    		PEvent event = new PEvent(eventName, lifeTime);
    		privateEvents.add(event);    		
    	}
    }
            
    private boolean containsEvent(String eventName)
    {
    	for(REvent event: replacingEvents)
    	{
    		if(event.name.equals(eventName))
    			return true;
    	}
    	
    	for(QEvent event: queueingEvents)
    	{
    		if(event.name.equals(eventName))
    			return true;
    	}

    	for(PEvent event: privateEvents)
    	{
    		if(event.name.equals(eventName))
    			return true;
    	}

    	return false;
    }
    
    public class REvent implements Comparable<REvent> 
    {
    	public final String name;
    	public final GuestAccess guestAccess;
    	private final String[] roles;

    	public REvent(String name)
    	{
    		this.name = name;
    		guestAccess = null;
    		roles = null;
    	}    	

    	public REvent(String name, GuestAccess guestAccess)
    	{
    		this.name = name;
    		this.guestAccess = guestAccess;
    		roles = null;
    	}    	

    	public REvent(String name, String[] roles)
    	{
    		this.name = name;
    		this.roles = roles;
    		this.guestAccess = null;
    	}
    	
    	public boolean containsRoles()
    	{
    		return roles != null;
    	}
    	
    	public String[] getRoles()
    	{
    		return Arrays.copyOf(roles, roles.length);
    	}

    	@Override
    	public int compareTo(REvent evt)
    	{
    	     return this.name.compareTo(evt.name);
    	}
    }
    
    public class QEvent implements Comparable<QEvent> 
    {
    	public final String name;
    	public final int lifeTime;
    	public final int maxQueueSize;
    	public final GuestAccess guestAccess;
    	private final String[] roles;
    	
    	public QEvent(String name, int lifeTime, int maxQueueSize)
    	{
    		this.name = name;
    		this.lifeTime = lifeTime;
    		this.maxQueueSize = maxQueueSize;
    		guestAccess = null;
    		roles = null;
    	}

    	public QEvent(String name, int lifeTime, int maxQueueSize, GuestAccess guestAccess)
    	{
    		this.name = name;
    		this.lifeTime = lifeTime;
    		this.maxQueueSize = maxQueueSize;
    		this.guestAccess = guestAccess;
    		roles = null;
    	}

    	public QEvent(String name, int lifeTime, int maxQueueSize, String[] roles)
    	{
    		this.name = name;
    		this.lifeTime = lifeTime;
    		this.maxQueueSize = maxQueueSize;
    		this.roles = roles;
    		guestAccess = null;
    	}
    	
    	public boolean containsRoles()
    	{
    		return roles != null;
    	}
    	
    	public String[] getRoles()
    	{
    		return Arrays.copyOf(roles, roles.length);
    	}

    	@Override
    	public int compareTo(QEvent evt)
    	{
    	     return this.name.compareTo(evt.name);
    	}
    }

    public class PEvent implements Comparable<PEvent>
    {
    	public String name;
    	public int lifeTime;
    	
    	public PEvent(String name, int lifeTime)
    	{
    		this.name = name;
    		this.lifeTime = lifeTime;
    	}
    	
    	@Override
    	public int compareTo(PEvent evt)
    	{
    	     return this.name.compareTo(evt.name);
    	}
    }        
    
    private void validateServiceType(String serviceType)
    {
    	if(serviceType == null || serviceType.length() == 0)
			throw new IllegalArgumentException("The name of the service type must not be null or empty.");

		if(serviceType.length() > 256)
			throw new IllegalArgumentException("The name of the service type contains more than 256 characters.");
		
		if (Pattern.matches("^[\\u0020-\\u007F]+$", serviceType) == false)			
            throw new IllegalArgumentException(String.format("The name of the service type '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", serviceType));

		if (Pattern.matches("^[a-zA-Z].*$", serviceType) == false)
            throw new IllegalArgumentException(String.format("The leading character in the name of the service type '%s' is illegal. Allowed characters: a-z A-Z.", serviceType));		

		if (Pattern.matches("^.*\\s$", serviceType))
            throw new IllegalArgumentException(String.format("The trailing space is not allowed in the name of the service type '%s'.", serviceType));			
		
		if (Pattern.matches("^.*\\s\\s.*$", serviceType))
			throw new IllegalArgumentException(String.format("The name of the service type '%s' has illegal format. Two or more consecutive spaces are not allowed.", serviceType));

		if (Pattern.matches("^[\\w\\s.$*+()#@%&=':\\^\\[\\]\\-/!]+$", serviceType) == false)
            throw new IllegalArgumentException(String.format("The name of the service type '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", serviceType));    
    }
    
    private void validateContractAuthor(String contractAuthor)
    {
		if(contractAuthor == null || contractAuthor.length() == 0)
			throw new IllegalArgumentException("The name of the contract author must not be null or empty.");

		if(contractAuthor.length() > 256)
			throw new IllegalArgumentException("The name of the contract author contains more than 256 characters.");

		if (Pattern.matches("^[\\u0020-\\u007F]+$", contractAuthor) == false)			
            throw new IllegalArgumentException(String.format("The name of the contract author '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", contractAuthor));

		if (Pattern.matches("^[a-zA-Z].*$", contractAuthor) == false)
            throw new IllegalArgumentException(String.format("The leading character in the name of the contract author '%s' is illegal. Allowed characters: a-z A-Z.", contractAuthor));		

		if (Pattern.matches("^.*\\s$", contractAuthor))
            throw new IllegalArgumentException(String.format("The trailing space is not allowed in the name of the contract author '%s'.", contractAuthor));			
		
		if (Pattern.matches("^.*\\s\\s.*$", contractAuthor))
			throw new IllegalArgumentException(String.format("The name of the contract author '%s' has illegal format. Two or more consecutive spaces are not allowed.", contractAuthor));

		if (Pattern.matches("^[\\w\\s.$*+()#@%&=':\\^\\[\\]\\-/!]+$", contractAuthor) == false)
            throw new IllegalArgumentException(String.format("The name of the contract author '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", contractAuthor));    
    }
    
    private void validateRoleName(String roleName)
    {
		if(roleName.length() == 0)
			throw new IllegalArgumentException("A role name must not be empty.");
		
		if(roleName.length() > 256)
			throw new IllegalArgumentException(String.format("The role name '%s' contains more than 256 characters.", roleName));

		if (Pattern.matches("^[\\u0020-\\u007F]+$", roleName) == false)			
            throw new IllegalArgumentException(String.format("The role name '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ]", roleName));
		
		if (Pattern.matches("^[a-zA-Z].*$", roleName) == false)
            throw new IllegalArgumentException(String.format("The leading character in the role name '%s' is illegal. Allowed characters: a-z A-Z", roleName));		
						
		if (Pattern.matches("^.*\\s\\s.*$", roleName))
			throw new IllegalArgumentException(String.format("The format of the role name '%s' is illegal. Two or more consecutive spaces are not allowed.", roleName));

		if (Pattern.matches("^[\\w\\s.$*+()#@%&=':\\^\\[\\]\\-/!]+$", roleName) == false)
            throw new IllegalArgumentException(String.format("The role name '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ]", roleName));    
    }

    private void validateEventName(String eventName)
    {
    	if(eventName == null || eventName.length() == 0)
			throw new IllegalArgumentException("An event name must not be null or empty.");		
		
		if(eventName.length() > 256)
			throw new IllegalArgumentException(String.format("The event name '%s' contains more than 256 characters.", eventName));
		
		if (Pattern.matches("^[\\u0020-\\u007F]+$", eventName) == false)
            throw new IllegalArgumentException(String.format("The event name '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", eventName));
		
		if (Pattern.matches("^[a-zA-Z].*$", eventName) == false)
            throw new IllegalArgumentException(String.format("The leading character in the event name '%s' is illegal. Allowed characters: a-z A-Z.", eventName));
		
		if (Pattern.matches("^.*\\s$", eventName))
            throw new IllegalArgumentException(String.format("The trailing space in the event name '%s' is illegal.", eventName));
		
		if (Pattern.matches("^.*\\s\\s.*$", eventName))
            throw new IllegalArgumentException(String.format("The format of the event name '%s' is illegal. Two or more consecutive spaces are not allowed.", eventName));	

		if (Pattern.matches("^[\\w\\s.$*+()#@%&=':\\^\\[\\]\\-/!]+$", eventName) == false)
            throw new IllegalArgumentException(String.format("The event name '%s' contains illegal characters. Allowed characters: a-z A-Z 0-9 space _ - . @ # $ %% & * ' : ^ / ! ( ) [ ].", eventName));		    
    }
}
