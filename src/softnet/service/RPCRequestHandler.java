package softnet.service;

import softnet.asn.*;

public interface RPCRequestHandler
{
	int execute(RequestContext context, SequenceDecoder parameters, SequenceEncoder result, SequenceEncoder error);
}
