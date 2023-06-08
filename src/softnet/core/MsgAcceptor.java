package softnet.core;

import softnet.exceptions.FormatException;

public interface MsgAcceptor<C>
{	
	void accept(byte[] message, C channel) throws softnet.asn.AsnException, FormatException, softnet.exceptions.SoftnetException;
}
