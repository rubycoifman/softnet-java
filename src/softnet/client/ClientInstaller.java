package softnet.client;

import java.util.UUID;

import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.FormatException;
import softnet.exceptions.SoftnetException;

class ClientInstaller 
{
	private UUID siteUid;
	public UUID getSiteUid()
	{
		return siteUid;
	}
		
	private ClientStatus clientStatus;
	public ClientStatus getStatus()
	{
		return clientStatus;
	}	
	
	public boolean isOnline()
	{
		return clientStatus == ClientStatus.Online;
	}

	public ClientInstaller(String serviceType, String contractAuthor, String clientDescription, ServiceGroup serviceGroup, Membership membership, Object endpoint_mutex)
	{
		this.serviceType = serviceType;
		this.contractAuthor = contractAuthor;
		this.clientDescription = clientDescription;
		this.serviceGroup = serviceGroup;
		this.membership = membership;
		this.endpoint_mutex = endpoint_mutex;
		clientStatus = ClientStatus.Offline;
	}
	
	public Acceptor<Channel> onClientOnlineCallback;
	public Runnable onClientParkedCallback;
	public Runnable onStatusChangedCallback;

	private Object endpoint_mutex;	
	private String serviceType;
	private String contractAuthor;
	private String clientDescription;
	private ServiceGroup serviceGroup;
	private Membership membership;
	
	public void onEndpointConnected(Channel channel)
	{		
		channel.registerComponent(Constants.Client.Installer.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					onMessageReceived(message, _channel);
				}
			});			
	}
	
	public void onEndpointDisconnected()
	{
		ClientStatus prevStatus = clientStatus;
		clientStatus = ClientStatus.Offline;
				
		if(prevStatus != ClientStatus.Offline)
			onStatusChangedCallback.run();
	}

	private void ProcessMessage_Online(byte[] message, Channel channel)throws AsnException
	{		
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
		siteUid = softnet.utils.ByteConverter.toUuid(asnRootSequence.OctetString(16));
		asnRootSequence.end();
		
		clientStatus = ClientStatus.Online;		
		onClientOnlineCallback.accept(channel);
		onStatusChangedCallback.run();
	}
	
	private void ProcessMessage_Parked(byte[] message, Channel channel) throws AsnException, softnet.exceptions.SoftnetException
	{
		SequenceDecoder asnRootSequence = ASNDecoder.Sequence(message, 2);
		siteUid = softnet.utils.ByteConverter.toUuid(asnRootSequence.OctetString(16));
		int statusCode = asnRootSequence.Int32();
		asnRootSequence.end();

		clientStatus = ResolveStatusCode(statusCode);
		onClientParkedCallback.run();
		onStatusChangedCallback.run();
	}

	private void ProcessMessage_GetState(byte[] message, Channel channel) throws SoftnetException
	{
		ASNEncoder asnEncoder = new ASNEncoder();        
        SequenceEncoder asnRootSequence = asnEncoder.Sequence();
        
        asnRootSequence.IA5String(serviceType);
        asnRootSequence.IA5String(contractAuthor);
        
        if(clientDescription != null)
        	asnRootSequence.IA5String(1, clientDescription);
        
        byte[] serviceGroupHash = serviceGroup.getHash();
        if(serviceGroupHash != null)
        	asnRootSequence.OctetString(2, serviceGroupHash);
        
        if(membership != null)
        {
        	byte[] userHash = membership.getHash();
        	if(userHash != null)
        		asnRootSequence.OctetString(3, userHash);
        }
        
        channel.send(MsgBuilder.Create(Constants.Client.Installer.ModuleId, Constants.Client.Installer.STATE, asnEncoder));
	}
			
	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(channel.closed())
				return;
			
			byte messageTag = message[1];
			if(messageTag == Constants.Client.Installer.GET_STATE)
			{
				ProcessMessage_GetState(message, channel);
			}
			else if(messageTag == Constants.Client.Installer.ONLINE)
			{
				ProcessMessage_Online(message, channel);
			}
			else if(messageTag == Constants.Client.Installer.PARKED)
			{
				ProcessMessage_Parked(message, channel);
			}
			else
				throw new FormatException();
		}
	}
	
	private ClientStatus ResolveStatusCode(int statusCode) throws softnet.exceptions.InputDataFormatSoftnetException
	{
		if(statusCode == Constants.ClientStatus.SiteNotConstructed)
			return ClientStatus.SiteNotConstructed;
		if(statusCode == Constants.ClientStatus.ServiceTypeConflict)
			return ClientStatus.ServiceTypeConflict;
		if(statusCode == Constants.ClientStatus.CreatorDisabled)
			return ClientStatus.CreatorDisabled;
		if(statusCode == Constants.ClientStatus.ServiceOwnerDisabled)
			return ClientStatus.ServiceOwnerDisabled;
		if(statusCode == Constants.ClientStatus.ServiceDisabled)
			return ClientStatus.ServiceDisabled;
		if(statusCode == Constants.ClientStatus.AccessDenied)
			return ClientStatus.AccessDenied;
		throw new softnet.exceptions.InputDataFormatSoftnetException();
	}
}
