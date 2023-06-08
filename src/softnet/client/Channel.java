package softnet.client;

import softnet.core.MsgAcceptor;

interface Channel
{
	void registerComponent(int componentId, MsgAcceptor<Channel> MessageReceivedHandler);
	void removeComponent(int componentId);
	void send(softnet.core.SoftnetMessage message);
	boolean closed();
}
