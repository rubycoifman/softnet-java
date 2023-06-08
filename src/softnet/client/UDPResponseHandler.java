package softnet.client;

import softnet.ConnectionMode;
import softnet.exceptions.SoftnetException;

public interface UDPResponseHandler {
	void onSuccess(ResponseContext context, java.net.DatagramSocket datagramSocket, java.net.InetSocketAddress remoteSocketAddress, ConnectionMode mode);
	void onError(ResponseContext context, SoftnetException exception);
}
