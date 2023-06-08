package softnet.service;

import java.nio.channels.SocketChannel;
import softnet.*;

public interface TCPAcceptHandler
{
	public void accept(RequestContext context, SocketChannel socketChannel, ConnectionMode mode);
}
