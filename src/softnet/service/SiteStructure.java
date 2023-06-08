package softnet.service;

public interface SiteStructure
{
	void setGuestSupport();
	void setStatelessGuestSupport();
	
	void setRoles(String roles);
	void setOwnerRole(String role);
	
	void addReplacingEvent(String eventName);
	void addReplacingEvent(String eventName, GuestAccess guestAccess);
	void addReplacingEvent(String eventName, String roles);
	
	void addQueueingEvent(String eventName, int lifeTime, int maxQueueSize);
	void addQueueingEvent(String eventName, int lifeTime, int maxQueueSize, GuestAccess guestAccess);
	void addQueueingEvent(String eventName, int lifeTime, int maxQueueSize, String roles);	

	void addPrivateEvent(String eventName, int lifeTime);
}