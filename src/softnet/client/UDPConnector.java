package softnet.client;

import softnet.core.BiAcceptor;

interface UDPConnector {
	void connect(UDPResponseHandler responseHandler, BiAcceptor<byte[], Object> authenticationHandler, Object attachment);
	void onAuthenticationHash(byte[] authHash, byte[] authKey2);
	void abort();
}
