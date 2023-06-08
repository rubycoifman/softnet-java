package softnet.service;

import softnet.MembershipUser;

public class MembershipUserEvent extends ServiceEndpointEvent
{
	private static final long serialVersionUID = -3789792508555367377L;
	public final MembershipUser user;

	public MembershipUserEvent(MembershipUser user, ServiceEndpoint source)
	{
		super(source);
		this.user = user;
	}
}
