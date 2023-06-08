package softnet.client;

import java.util.EventObject;

public class ClientOnlineEvent extends EventObject {
	private static final long serialVersionUID = 526593227007947782L;
	public final boolean areServicesUpdated;
	
	public ClientOnlineEvent(boolean areServicesUpdated, Object source)
	{
		super(source);
		this.areServicesUpdated = areServicesUpdated;
	}
}
