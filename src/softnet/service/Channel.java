package softnet.service;

import softnet.core.*;

interface Channel
{
	void registerComponent(int componentId, MsgAcceptor<Channel> MessageReceivedHandler);
	void removeComponent(int componentId);
	void send(SoftnetMessage message);
	boolean isClosed();
}