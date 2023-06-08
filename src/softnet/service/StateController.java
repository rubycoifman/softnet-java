package softnet.service;

import softnet.asn.*;
import softnet.core.Constants;
import softnet.core.MsgAcceptor;
import softnet.exceptions.FormatException;
import softnet.exceptions.SoftnetException;

public class StateController
{
	public Runnable hostnameChangedCallback;
	
	private String hostname;
	public String getHostname()
	{
		return hostname;
	}
	
	public StateController(EndpointConnector endpointConnector, Object endpoint_mutex)
	{
		this.endpointConnector = endpointConnector;
		this.endpoint_mutex = endpoint_mutex;
		hostname = "";
	}
	
	private Object endpoint_mutex;
	private EndpointConnector endpointConnector;	

	public void onEndpointConnected(Channel channel)
	{
		channel.registerComponent(Constants.Service.StateController.ModuleId, 
			new MsgAcceptor<Channel>()
			{
				public void accept(byte[] message, Channel _channel) throws AsnException, FormatException, SoftnetException
				{
					onMessageReceived(message, _channel);
				}
			});					
	}	
	
	private void ProcessMessage_State(byte[] message) throws AsnException
	{	
		SequenceDecoder asnSequenceDecoder = ASNDecoder.Sequence(message, 2);
		if(asnSequenceDecoder.exists(1))
		{
			int pingPeriod = asnSequenceDecoder.Int32();
			endpointConnector.setRemotePingPeriod(pingPeriod);
		}
		asnSequenceDecoder.end();	
	}
	
	private void ProcessMessage_HostnameChanged(byte[] message) throws AsnException
	{	
		SequenceDecoder asnRootSequenceDecoder = ASNDecoder.Sequence(message, 2);
		String hostname = asnRootSequenceDecoder.IA5String(0, 256);
		asnRootSequenceDecoder.end();
	
		this.hostname = hostname;
		hostnameChangedCallback.run();
	}
	
	private void ProcessMessage_SetPingPeriod(byte[] message) throws AsnException
	{	
		SequenceDecoder asnSequenceDecoder = ASNDecoder.Sequence(message, 2);
		int pingPeriod = asnSequenceDecoder.Int32();					
		asnSequenceDecoder.end();
		
		endpointConnector.setRemotePingPeriod(pingPeriod);
	}

	private void onMessageReceived(byte[] message, Channel channel) throws AsnException, FormatException, SoftnetException
	{
		synchronized(endpoint_mutex)
		{
			if(channel.isClosed())
				return;
			
			byte messageTag = message[1];
			if(messageTag == Constants.Service.StateController.STATE)
			{
				ProcessMessage_State(message);
			}
			else if(messageTag == Constants.Service.StateController.HOSTNAME_CHANGED)
			{
				ProcessMessage_HostnameChanged(message);
			}
			else if(messageTag == Constants.Service.StateController.SET_PING_PERIOD)
			{
				ProcessMessage_SetPingPeriod(message);
			}
			else
				throw new FormatException();
		}
	}
}
