package softnet.client;

public interface RemoteService
{
	long getId();
	String getHostname();
	String getVersion();
	boolean isOnline();
	boolean isRemoved();
}
