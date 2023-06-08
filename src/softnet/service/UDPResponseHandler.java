package softnet.service;

interface UDPResponseHandler {
	void onSuccess(java.net.DatagramSocket datagramSocket, java.net.InetSocketAddress remoteEndpoint, softnet.ConnectionMode mode, Object attachment);
	void onError(int errorCode, Object attachment);
}
