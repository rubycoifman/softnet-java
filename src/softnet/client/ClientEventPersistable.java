package softnet.client;

public class ClientEventPersistable
{
	public final String name;
	public final long instanceId;	
	
	public ClientEventPersistable(String name, long instanceId)
	{
		this.name = name;
		this.instanceId = instanceId;
	}
}
