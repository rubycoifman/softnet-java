package softnet.client;

import softnet.asn.*;
import softnet.core.*;
import softnet.exceptions.*;

public class StateController 
{
	public StateController(EndpointConnector endpointConnector, Object endpoint_mutex)
	{
		this.endpointConnector = endpointConnector;
		this.endpoint_mutex = endpoint_mutex;
	}

	private EndpointConnector endpointConnector;	
	private Object endpoint_mutex;

	public void onChannelConnected(Channel channel)
	{
		channel.registerComponent(Constants.Client.StateController.ModuleId, 
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
			if(channel.closed())
				return;
			
			byte messageTag = message[1];
			if(messageTag == Constants.Client.StateController.STATE)
			{
				ProcessMessage_State(message);
			}
			else if(messageTag == Constants.Client.StateController.SET_PING_PERIOD)
			{
				ProcessMessage_SetPingPeriod(message);
			}
			else
				throw new FormatException();
		}
	}
}
