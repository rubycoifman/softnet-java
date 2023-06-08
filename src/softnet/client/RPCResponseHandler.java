package softnet.client;

import softnet.asn.*;
import softnet.exceptions.*;

public interface RPCResponseHandler
{
	void onSuccess(ResponseContext context, SequenceDecoder result);
	void onError(ResponseContext context, int errorCode, SequenceDecoder error);
	void onError(ResponseContext context, SoftnetException exception);
}
