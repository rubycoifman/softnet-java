package softnet.service;

import softnet.MembershipUser;
import softnet.exceptions.*;

interface Membership
{
	MembershipUser findUser(long userId);
	MembershipUser findUser(String userName);
	MembershipUser resolve(int userKind, long userId);
	boolean isGuestAllowed();
	boolean isGuestSupported();
	boolean isStatelessGuestSupported();
	boolean isRbacSupported();
	boolean containsRole(String role);
	MembershipUser[] getUsers();

	void onEndpointConnected(Channel channel);
	void onEndpointDisconnected();
	void onServiceOnline();
	byte[] getHash() throws HostFunctionalitySoftnetException;
	void addEventListener(ServiceEventListener listener);
	void removeEventListener(ServiceEventListener listener);
}
