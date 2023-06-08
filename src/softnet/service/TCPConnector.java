package softnet.service;

import softnet.core.BiAcceptor;

interface TCPConnector {
	void connect(TCPResponseHandler responseHandler, BiAcceptor<byte[], Object> authenticationHandler, Object attachment);
	void onAuthenticationHash(byte[] authHash, byte[] authKey2);
	void abort();
}
