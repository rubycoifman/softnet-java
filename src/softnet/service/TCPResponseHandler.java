package softnet.service;

import java.nio.channels.SocketChannel;
import softnet.ConnectionMode;

interface TCPResponseHandler
{
	void onSuccess(SocketChannel socketChannel, ConnectionMode mode, Object attachment);
	void onError(int errorCode, Object attachment);
}
