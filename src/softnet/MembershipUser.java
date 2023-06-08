package softnet;

public interface MembershipUser
{
	long getId();
    String getName();
    String[] getRoles();
    boolean hasRoles();
    boolean isInRole(String role);
    boolean isGuest();
    boolean isStatelessGuest();
    boolean isRemoved();
}
