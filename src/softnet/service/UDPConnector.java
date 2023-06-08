package softnet.service;

import softnet.core.BiAcceptor;

public interface UDPConnector {
	void connect(UDPResponseHandler responseHandler, BiAcceptor<byte[], Object> authenticationHandler, Object attachment);
	void onAuthenticationHash(byte[] authHash, byte[] authKey2);
	void abort();
}
