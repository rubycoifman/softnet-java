package softnet.service;

public class ServiceEventAdapter implements ServiceEventListener
{
	public void onConnectivityChanged(ServiceEndpointEvent e) {}
	public void onStatusChanged(ServiceEndpointEvent e) {}
	public void onHostnameChanged(ServiceEndpointEvent e) {}
	public void onUserIncluded(MembershipUserEvent e) {}
	public void onUserUpdated(MembershipUserEvent e) {}
	public void onUserRemoved(MembershipUserEvent e) {}
	public void onUsersUpdated(ServiceEndpointEvent e) {}
	public void onGuestAccessChanged(ServiceEndpointEvent e) {}
	public void onPersistenceFailed(ServicePersistenceFailedEvent e) {}
}
