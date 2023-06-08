package softnet.exceptions;

public enum SoftnetError
{
	NoError(0),
	SecurityError(ErrorCodes.SECURITY_ERROR),
	NoResponse(ErrorCodes.NO_RESPONSE),
	UnsupportedHostFunctionality(ErrorCodes.UNSUPPORTED_HOST_FUNCTIONALITY),
	PersistenceError(ErrorCodes.PERSISTENCE_ERROR),
	NetworkError(ErrorCodes.NETWORK_ERROR),
	IncompatibleProtocolVersion(ErrorCodes.INCOMPATIBLE_PROTOCOL_VERSION),
	InvalidServerEndpoint(ErrorCodes.INVALID_SERVER_ENDPOINT),
	EndpointDataFormatError(ErrorCodes.ENDPOINT_DATA_FORMAT_ERROR),
	EndpointDataInconsistent(ErrorCodes.ENDPOINT_DATA_INCONSISTENT),
	InputDataFormatError(ErrorCodes.INPUT_DATA_FORMAT_ERROR),
	InputDataInconsistent(ErrorCodes.INPUT_DATA_INCONSISTENT),
	IllegalName(ErrorCodes.ILLEGAL_NAME),
	UnexpectedError(ErrorCodes.UNEXPECTED_ERROR),
	ClientOffline(ErrorCodes.CLIENT_OFFLINE),
	AccessDenied(ErrorCodes.ACCESS_DENIED),
	PortUnreachable(ErrorCodes.PORT_UNREACHABLE),
	ServiceOffline(ErrorCodes.SERVICE_OFFLINE),
	ServiceBusy(ErrorCodes.SERVICE_BUSY),
	MissingProcedure(ErrorCodes.MISSING_PROCEDURE),
	ArgumentError(ErrorCodes.ARGUMENT_ERROR),
	ConnectionAttemptFailed(ErrorCodes.CONNECTION_ATTEMPT_FAILED),
	ServiceNotRegistered(ErrorCodes.SERVICE_NOT_REGISTERED),
	InvalidClientCategory(ErrorCodes.INVALID_CLIENT_CATEGORY),
	ClientNotRegistered(ErrorCodes.CLIENT_NOT_REGISTERED),
	PasswordNotMatched(ErrorCodes.PASSWORD_NOT_MATCHED),
	DublicatedServiceUidUsage(ErrorCodes.DUPLICATED_SERVICE_UID_USAGE),
	DublicatedClientKeyUsage(ErrorCodes.DUPLICATED_CLIENT_KEY_USAGE),
	TimeoutExpired(ErrorCodes.TIMEOUT_EXPIRED),
	ServerBusy(ErrorCodes.SERVER_BUSY),
	ServerConfigError(ErrorCodes.SERVER_CONFIG_ERROR),
	ServerDbmsError(ErrorCodes.SERVER_DBMS_ERROR),
	ServerDataIntegrityError(ErrorCodes.SERVER_DATA_INTEGRITY_ERROR),
	RestartDemanded(ErrorCodes.RESTART_DEMANDED);
	 	
	public final int Code;
	SoftnetError(int code) { Code = code; }
}
