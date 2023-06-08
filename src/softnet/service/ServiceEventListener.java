package softnet.service;

import java.util.EventListener;

public interface ServiceEventListener extends EventListener
{	
	void onConnectivityChanged(ServiceEndpointEvent e);
	void onStatusChanged(ServiceEndpointEvent e);
	void onHostnameChanged(ServiceEndpointEvent e);
	void onUserIncluded(MembershipUserEvent e);
	void onUserUpdated(MembershipUserEvent e);
	void onUserRemoved(MembershipUserEvent e);
	void onUsersUpdated(ServiceEndpointEvent e);
	void onGuestAccessChanged(ServiceEndpointEvent e);
	void onPersistenceFailed(ServicePersistenceFailedEvent e);
}
