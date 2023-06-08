package softnet.client;

import java.nio.channels.SocketChannel;
import softnet.ConnectionMode;
import softnet.exceptions.SoftnetException;

public interface TCPResponseHandler {
	void onSuccess(ResponseContext context, SocketChannel socketChannel, ConnectionMode mode);
	void onError(ResponseContext context, SoftnetException exception);
}
