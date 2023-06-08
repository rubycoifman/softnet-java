package softnet.service;

import softnet.MembershipUser;

public class RequestContext {
	public final ServiceEndpoint serviceEndpoint;
	public final MembershipUser user;
	public final long clientId;

	public RequestContext(ServiceEndpoint serviceEndpoint, MembershipUser user, long clientId)
	{
		this.serviceEndpoint = serviceEndpoint;
		this.user = user;
		this.clientId = clientId;
	}
}
