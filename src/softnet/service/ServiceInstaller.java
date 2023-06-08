package softnet.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import softnet.utils.*;
import softnet.core.*;
import softnet.exceptions.*;
import softnet.asn.*;

class ServiceInstaller 
{	
	public boolean Online()
	{
		return serviceStatus == ServiceStatus.Online;
	}
	
	private ServiceStatus serviceStatus;
	public ServiceStatus getStatus()
	{
		return serviceStatus;
	}	

	public ServiceInstaller(SiteStructureAdapter siteStructure, String serviceVersion, Membership membership, StateController stateController, Object endpoint_mutex)
	{
		this.siteStructure = siteStructure;		
		this.serviceVersion = serviceVersion;
		this.membership = membership;
		this.stateController = stateController;
		this.endpoint_mutex = endpoint_mutex;
		serviceStatus = ServiceStatus.Offline;
	}
	
	public void Init() throws HostFunctionalitySoftnetException
	{
		siteStructureHash = computeSSHash();
	}
	
	public Runnable serviceOnlineCallback;
	public Runnable serviceParkedCallback;
	public Runnable serviceStatusEventCallback;

	private Object endpoint_mutex;
	
	private SiteStructureAdapter siteStructure;
	private byte[] siteStructureHash = null;
	private String serviceVersion;
	private Membership membership;
	private StateController stateController;
			
	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Service.Installer.ModuleId, 
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
		ServiceStatus prevStatus = serviceStatus;
		serviceStatus = ServiceStatus.Offline;
		
		if(prevStatus != ServiceStatus.Offline)
			serviceStatusEventCallback.run();
	}
	
	private void ProcessMessage_Online(byte[] message, Channel channel)
	{
		serviceStatus = ServiceStatus.Online;
		serviceOnlineCallback.run();
		serviceStatusEventCallback.run();
	}
	
	private void ProcessMessage_Parked(byte[] message, Channel channel) throws AsnException, softnet.exceptions.SoftnetException
	{
		SequenceDecoder asnRootSequenceDecoder = ASNDecoder.Sequence(message, 2);
		int statusCode = asnRootSequenceDecoder.Int32();
		asnRootSequenceDecoder.end();

		serviceStatus = ResolveStatusCode(statusCode);
		serviceParkedCallback.run();
		serviceStatusEventCallback.run();
	}
	
	private void ProcessMessage_GetState(byte[] message, Channel channel) throws AsnException, SoftnetException
	{				
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnRootEncoder = asnEncoder.Sequence();

        SequenceEncoder asnSiteProfile = asnRootEncoder.Sequence();
        asnSiteProfile.IA5String(siteStructure.serviceType);
        asnSiteProfile.IA5String(siteStructure.contractAuthor);         	        
        asnSiteProfile.OctetString(siteStructureHash);
        
        if(serviceVersion != null)
        	asnRootEncoder.IA5String(1, serviceVersion);
     
        SequenceEncoder asnMembershipState = asnRootEncoder.Sequence();
        byte[] userListHash = membership.getHash();
        if(userListHash != null)
        	asnMembershipState.OctetString(1, userListHash);

        if(siteStructure.isGuestSupported())
        	asnMembershipState.Boolean(2, membership.isGuestAllowed());

        String hostname = stateController.getHostname();
        if(hostname.length() > 0)
        {
        	byte[] nameBytes = hostname.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        	byte[] nameHash = ByteConverter.getBytes(Fnv1a.get32BitHash(nameBytes));
        	asnRootEncoder.OctetString(2, nameHash);
        }

        channel.send(MsgBuilder.Create(Constants.Service.Installer.ModuleId, Constants.Service.Installer.STATE, asnEncoder));		
	}
	
	private void ProcessMessage_GetServiceProperties(byte[] message, Channel channel) throws AsnException
	{
		ASNEncoder asnEncoder = new ASNEncoder();        
        SequenceEncoder asnRootSequence = asnEncoder.Sequence();	          
        SequenceEncoder asnSiteStructure = asnRootSequence.Sequence();
        
        asnSiteStructure.IA5String(siteStructure.serviceType);
        asnSiteStructure.IA5String(siteStructure.contractAuthor);
        
        if(siteStructure.isGuestSupported())
        {
        	if(siteStructure.isStatelessGuestSupported())
        		asnSiteStructure.Int32(2);
        	else
        		asnSiteStructure.Int32(1);
        }
        else        	
        {
        	asnSiteStructure.Int32(0);        	
        }
        
        if(siteStructure.isRbacSupported())
        {
        	SequenceEncoder asnRoles = asnSiteStructure.Sequence(1);
	        SequenceEncoder asnRoleNames = asnRoles.Sequence();
	        String[] roleNames = siteStructure.getRoles();
	        for(String role: roleNames)
	        	asnRoleNames.IA5String(role);
	        
	        if (siteStructure.ownerRole() != null)
	        	asnRoles.IA5String(1, siteStructure.ownerRole());
        }
        
        if(siteStructure.areEventsSupported())
        {
        	SequenceEncoder asnEventsDefinition = asnSiteStructure.Sequence(2);
        	if(siteStructure.containsReplacingEvents())
        	{
        		SequenceEncoder asnReplacingEvents = asnEventsDefinition.Sequence(1);
        		ArrayList<SiteStructureAdapter.REvent> replacingEvents = siteStructure.getReplacingEvents();
        		for(SiteStructureAdapter.REvent event: replacingEvents)
        		{
        			SequenceEncoder asnEvent = asnReplacingEvents.Sequence();
        			asnEvent.IA5String(event.name);
        			if(event.guestAccess != null)
        			{
        				if(event.guestAccess == GuestAccess.GuestDenied)
        					asnEvent.Int32(1, 1);
        				else
        					asnEvent.Int32(1, 2);        					
        			}
        			else if(event.containsRoles())
        			{
        				String[] roles = event.getRoles();
        				SequenceEncoder asnRoleNames = asnEvent.Sequence(2);
        				for(String roleName: roles)
        					asnRoleNames.IA5String(roleName);
        			}        				
        		}
        	}
        	
        	if(siteStructure.containsQueueingEvents())
        	{
        		SequenceEncoder asnQueueingEvents = asnEventsDefinition.Sequence(2);
        		ArrayList<SiteStructureAdapter.QEvent> queueingEvents = siteStructure.getQueueingEvents();
        		for(SiteStructureAdapter.QEvent event: queueingEvents)
        		{
        			SequenceEncoder asnEvent = asnQueueingEvents.Sequence();
        			asnEvent.IA5String(event.name);
        			asnEvent.Int32(event.lifeTime);
        			asnEvent.Int32(event.maxQueueSize);
        			if(event.guestAccess != null)
        			{
        				if(event.guestAccess == GuestAccess.GuestDenied)
        					asnEvent.Int32(1, 1);
        				else
        					asnEvent.Int32(1, 2);        					
        			}
        			else if(event.containsRoles())
        			{
        				String[] roles = event.getRoles();
        				SequenceEncoder asnRoleNames = asnEvent.Sequence(2);
        				for(String roleName: roles)
        					asnRoleNames.IA5String(roleName);
        			}        				
        		}
        	}
        	
        	if(siteStructure.containsPrivateEvents())
        	{
        		SequenceEncoder asnPrivateEvents = asnEventsDefinition.Sequence(4);
        		ArrayList<SiteStructureAdapter.PEvent> privateEvents = siteStructure.getPrivateEvents();
        		for(SiteStructureAdapter.PEvent event: privateEvents)
        		{
        			SequenceEncoder asnEvent = asnPrivateEvents.Sequence();
        			asnEvent.IA5String(event.name);
        			asnEvent.Int32(event.lifeTime);
        		}
        	}
        }        
        
        if(serviceVersion != null)
        	asnRootSequence.IA5String(1, serviceVersion);
		
		channel.send(MsgBuilder.Create(Constants.Service.Installer.ModuleId, Constants.Service.Installer.SERVICE_PROPERTIES, asnEncoder));
	}
	
	public byte[] computeSSHash() throws HostFunctionalitySoftnetException
	{
		ASNEncoder asnEncoder = new ASNEncoder();
        SequenceEncoder asnSiteStructure = asnEncoder.Sequence();
        
        asnSiteStructure.IA5String(siteStructure.serviceType);
        asnSiteStructure.IA5String(siteStructure.contractAuthor);

        if(siteStructure.isGuestSupported())
        {
        	if(siteStructure.isStatelessGuestSupported())
        		asnSiteStructure.Int32(2);
        	else
        		asnSiteStructure.Int32(1);
        }
        else        	
        	asnSiteStructure.Int32(0);        	

        if(siteStructure.isRbacSupported())
        {
        	SequenceEncoder asnRoles = asnSiteStructure.Sequence(1);	        
	        String[] roles = siteStructure.getRoles();
	        Arrays.sort(roles);
	        for(String roleName: roles)
	        	asnRoles.IA5String(roleName);
        }
        
        if(siteStructure.areEventsSupported())
        {
        	SequenceEncoder asnEventsDefinition = asnSiteStructure.Sequence(2);
        	if(siteStructure.containsReplacingEvents())
        	{
        		SequenceEncoder asnReplacingEvents = asnEventsDefinition.Sequence(1);
        		ArrayList<SiteStructureAdapter.REvent> replacingEvents = siteStructure.getReplacingEvents();
        		Collections.sort(replacingEvents);
        		for(SiteStructureAdapter.REvent event: replacingEvents)
        		{
        			SequenceEncoder asnEvent = asnReplacingEvents.Sequence();
        			asnEvent.IA5String(event.name);
        			if(event.guestAccess != null)
        			{
        				if(event.guestAccess == GuestAccess.GuestDenied)
        					asnEvent.Int32(1, 1);
        				else
        					asnEvent.Int32(1, 2);
        			}
        			else if(event.containsRoles())
        			{
        				String[] roles = event.getRoles();
        				Arrays.sort(roles);
        				SequenceEncoder asnRoleNames = asnEvent.Sequence(2);
        				for(String roleName: roles)
        					asnRoleNames.IA5String(roleName);
        			}        				
        		}
        	}
        	
        	if(siteStructure.containsQueueingEvents())
        	{
        		SequenceEncoder asnQueueingEvents = asnEventsDefinition.Sequence(2);
        		ArrayList<SiteStructureAdapter.QEvent> queueingEvents = siteStructure.getQueueingEvents();
        		Collections.sort(queueingEvents);
        		for(SiteStructureAdapter.QEvent event: queueingEvents)
        		{
        			SequenceEncoder asnEvent = asnQueueingEvents.Sequence();
        			asnEvent.IA5String(event.name);
        			asnEvent.Int32(event.lifeTime);
        			asnEvent.Int32(event.maxQueueSize);
        			if(event.guestAccess != null)
        			{
        				if(event.guestAccess == GuestAccess.GuestDenied)
        					asnEvent.Int32(1, 1);
        				else
        					asnEvent.Int32(1, 2);        					
        			}
        			else if(event.containsRoles())
        			{
        				String[] roles = event.getRoles();
        				Arrays.sort(roles);
        				SequenceEncoder asnRoleNames = asnEvent.Sequence(2);
        				for(String roleName: roles)
        					asnRoleNames.IA5String(roleName);
        			}        				
        		}
        	}
        	
        	if(siteStructure.containsPrivateEvents())
        	{
        		SequenceEncoder asnPrivateEvents = asnEventsDefinition.Sequence(4);
        		ArrayList<SiteStructureAdapter.PEvent> privateEvents = siteStructure.getPrivateEvents();
        		Collections.sort(privateEvents);
        		for(SiteStructureAdapter.PEvent event: privateEvents)
        		{
        			SequenceEncoder asnEvent = asnPrivateEvents.Sequence();
        			asnEvent.IA5String(event.name);
        			asnEvent.Int32(event.lifeTime);
        		}
        	}
        }
        
        byte[] encoding = asnEncoder.getEncoding();
        return Sha1Hash.compute(encoding);
	}

	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(channel.isClosed())
				return;
			
			byte messageTag = message[1];
			if(messageTag == Constants.Service.Installer.GET_STATE)
			{
				ProcessMessage_GetState(message, channel);
			}
			else if(messageTag == Constants.Service.Installer.ONLINE)
			{
				ProcessMessage_Online(message, channel);
			}
			else if(messageTag == Constants.Service.Installer.PARKED)
			{
				ProcessMessage_Parked(message, channel);
			}
			else if(messageTag == Constants.Service.Installer.GET_SERVICE_PROPERTIES)
			{
				ProcessMessage_GetServiceProperties(message, channel);
			}
			else
				throw new FormatException();
		}
	}	
	
	private ServiceStatus ResolveStatusCode(int statusCode) throws InputDataFormatSoftnetException
	{
		if(statusCode == Constants.ServiceStatus.SiteNotConstructed)
			return ServiceStatus.SiteNotConstructed;
		if(statusCode == Constants.ServiceStatus.ServiceTypeConflict)
			return ServiceStatus.ServiceTypeConflict;
		if(statusCode == Constants.ServiceStatus.SiteStructureMismatch)
			return ServiceStatus.SiteStructureMismatch;
		if(statusCode == Constants.ServiceStatus.OwnerDisabled)
			return ServiceStatus.OwnerDisabled;
		if(statusCode == Constants.ServiceStatus.SiteDisabled)
			return ServiceStatus.SiteDisabled;
		if(statusCode == Constants.ServiceStatus.Disabled)
			return ServiceStatus.Disabled;
		throw new InputDataFormatSoftnetException();
	}
}
