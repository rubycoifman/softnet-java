package softnet.service;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import softnet.ConnectionMode;

public interface UDPAcceptHandler
{
	public void accept(RequestContext context, DatagramSocket datagramSocket, InetSocketAddress remoteSocketAddress, ConnectionMode mode);
}
