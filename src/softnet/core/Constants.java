package softnet.core;

public class Constants
{
	public static final byte ProtocolVersion = 1;
	
    public static final int TcpP2PConnectionAttemptTimeoutSeconds = 4;
    public static final int TcpConnectingWaitSeconds = 30;

    public static final int UdpP2PConnectionAttemptTimeoutSeconds = 4;
    public static final int UdpConnectingWaitSeconds = 30;
    
    public static final int RpcWaitSeconds = 30;
    public static final int EventDeliverySeconds = 30;
    
	public static class ServerPorts
	{
		public static final int Balancer = 7737;
		public static final int Tracker = 7740;
		public static final int TcpRzvPort = 7778;
		public static final int UdpRzvPort = 7779;
	}
	
	public class EventCategory
    {
    	public static final int Replacing = 1;
    	public static final int Queueing = 2;
    	public static final int Private = 4;
    }

    public class ClientCategory
    {
    	public static final int SingleService = 1;
    	public static final int MultiService = 2;
    	public static final int SingleServiceStateless = 3;
    	public static final int MultiServiceStateless = 4;
    }

    public class UserKind
    {
    	public static final int Owner = 1;
    	public static final int Private = 2;
    	public static final int Contact = 3;
    	public static final int Guest = 4;
    	public static final int StatelessGuest = 5;
    }
    
    public class ServiceStatus
    {            
        public static final int Offline = 0;
        public static final int Online = 1;        
        public static final int SiteNotConstructed = 2;
        public static final int ServiceTypeConflict = 3;
        public static final int SiteStructureMismatch = 4;
        public static final int OwnerDisabled = 5;
        public static final int SiteDisabled = 6;
        public static final int Disabled = 7;
    }

    public class ClientStatus
    {            
        public static final int Offline = 0;
        public static final int Online = 1;
        public static final int AccessDenied = 2;        
        public static final int ServiceDisabled = 3;
        public static final int ServiceOwnerDisabled = 4;
        public static final int CreatorDisabled = 5;
        public static final int ServiceTypeConflict = 6;
        public static final int SiteNotConstructed = 7;
    }
    
    public static class FrontServer
    {
    	// Output
    	public static final byte CLIENT_S_KEY = 1;
    	public static final byte CLIENT_M_KEY = 2;
    	public static final byte CLIENT_SS_KEY = 3;
    	public static final byte CLIENT_MS_KEY = 4;
    	public static final byte SERVICE_UID = 5;
    	
        // Input
    	public static final byte SUCCESS = 1;
    	public static final byte ERROR = 2;
    	    	
    	public static final int IP_V6 = 6;
    	public static final int IP_V4 = 4;
    }
    
    public static class Service
    {
    	public static final byte EndpointType = 1;

        public static class Channel
        {
        	public static final byte ModuleId = 1;
            // Output
        	public static final byte OPEN = 1;
        	public static final byte RESTORE = 2;
        	public static final byte HASH_AND_KEY2 = 3;
            // Input
        	public static final byte SALT_AND_KEY1 = 1;
        	public static final byte OPEN_OK = 2;
        	public static final byte RESTORE_OK = 3;
        	public static final byte ERROR = 4;
        }
        
        public static class ChannelMonitor
        {
        	public static final byte ModuleId = 2;
            // Output
        	public static final byte PING = 1;        	
        	public static final byte KEEP_ALIVE = 2;        	
            // Input
        	public static final byte PONG = 1;
        }

        public static class Installer
        {
        	public static final byte ModuleId = 5;
            // Output
            public static final byte STATE = 1;
            public static final byte SERVICE_PROPERTIES = 2;
            // Input
        	public static final byte GET_STATE = 1;
            public static final byte GET_SERVICE_PROPERTIES = 2;
            public static final byte ONLINE = 3;
            public static final byte PARKED = 4;
        }

        public static class StateController
        {
        	public static final byte ModuleId = 6;
        	// Input
        	public static final byte STATE = 1;
        	public static final byte HOSTNAME_CHANGED = 2;
        	public static final byte SET_PING_PERIOD = 3;
        }
        
        public static class RBMembership
        {
        	public static final byte ModuleId = 7;
        	// Input
        	public static final byte USER_LIST = 1;
        	public static final byte USER_INCLUDED = 2;
        	public static final byte USER_UPDATED = 3;        	
        	public static final byte USER_REMOVED = 4;      
        	public static final byte GUEST_ALLOWED = 5;        	
        	public static final byte GUEST_DENIED = 6;        	
        }

        public static class UBMembership
        {
        	public static final byte ModuleId = 8;
        	// Input
        	public static final byte USER_LIST = 1;
        	public static final byte USER_INCLUDED = 2;
        	public static final byte USER_UPDATED = 3;        	
        	public static final byte USER_REMOVED = 4;      
        	public static final byte GUEST_ALLOWED = 5;        	
        	public static final byte GUEST_DENIED = 6;        	
        }

        public static class TcpController
        {
        	public static final byte ModuleId = 10;
        	// Input
        	public static final byte REQUEST = 1;
        	public static final byte RZV_DATA = 2;
        	public static final byte AUTH_HASH = 7;
        	public static final byte AUTH_ERROR = 8;
            // Output
        	public static final byte REQUEST_OK = 1;
        	public static final byte REQUEST_ERROR = 2;
        	public static final byte AUTH_KEY = 7;
        }        

        public static class UdpController
        {
        	public static final byte ModuleId = 11;
        	// Input
        	public static final byte REQUEST = 1;
        	public static final byte RZV_DATA = 2;
        	public static final byte AUTH_HASH = 7;
        	public static final byte AUTH_ERROR = 8;
            // Output
        	public static final byte REQUEST_OK = 1;
        	public static final byte REQUEST_ERROR = 2;
        	public static final byte AUTH_KEY = 7;
        }        

        public static class RpcController
        {
        	public static final byte ModuleId = 14;
        	// Output
        	public static final byte REQUEST = 1;
        	// Input
        	public static final byte RESULT = 1;        	
        	public static final byte APP_ERROR = 2;        	
        	public static final byte SOFTNET_ERROR = 3;        	
        }        

        public static class EventController
        {
        	public static final byte ModuleId = 15;
        	// Output
        	public static final byte REPLACING_EVENT = 1;        	
        	public static final byte QUEUEING_EVENT = 2;        	
        	public static final byte PRIVATE_EVENT = 4;        	
        	public static final byte REPLACING_NULL_EVENT = 5;        	
        	public static final byte NEW_STORAGE_UID = 9;        	
        	// Input
        	public static final byte EVENT_ACK = 1;        	
        	public static final byte ILLEGAL_EVENT_NAME = 2;
        	public static final byte LAST_STORAGE_UID = 9;        	
        }
    }
    
    public static class Client
    {
    	public static final byte EndpointType = 2;

        public static class Channel
        {
        	public static final byte ModuleId = 1;
            // Output
        	public static final byte OPEN = 1;
        	public static final byte RESTORE = 2;
        	public static final byte HASH_AND_KEY2 = 3;
            // Input
        	public static final byte SALT_AND_KEY1 = 1;
        	public static final byte OPEN_OK = 2;
        	public static final byte OPEN_OK2 = 3;
        	public static final byte RESTORE_OK = 4;
        	public static final byte ERROR = 5;
        }
        
        public static class ChannelMonitor
        {
        	public static final byte ModuleId = 2;
            // Output
        	public static final byte PING = 1;        	
        	public static final byte KEEP_ALIVE = 2;        	
            // Input
        	public static final byte PONG = 1;
        }
        
        public static class Installer
        {
        	public static final byte ModuleId = 5;
        	// Output
        	public static final byte STATE = 1;        
        	// Input
        	public static final byte GET_STATE = 1;        	
        	public static final byte ONLINE = 2;
        	public static final byte PARKED = 3;
        }

        public static class StateController
        {
        	public static final byte ModuleId = 6;
        	// Input
        	public static final byte STATE = 1;
        	public static final byte SET_PING_PERIOD = 2;
        }
        
        public static class SingleServiceGroup
        {
        	public static final byte ModuleId = 7;
            // Input
        	public static final byte SERVICE_UPDATED = 1;
        	public static final byte SERVICE_ONLINE = 2;
        	public static final byte SERVICE_ONLINE_2 = 3;
        	public static final byte SERVICE_OFFLINE = 4;
        }
        
        public static class MultiServiceGroup
        {
        	public static final byte ModuleId = 8;
            // Input
        	public static final byte SERVICES_UPDATED = 1;
        	public static final byte SERVICES_ONLINE = 2;
        	public static final byte SERVICE_INCLUDED = 3;
        	public static final byte SERVICE_REMOVED = 4;
        	public static final byte SERVICE_UPDATED = 5;
        	public static final byte SERVICE_ONLINE = 6;
        	public static final byte SERVICE_ONLINE_2 = 7;
        	public static final byte SERVICE_OFFLINE = 8;
        }
        
        public static class Membership
        {
        	public static final byte ModuleId = 9;
        	// Input
        	public static final byte GUEST = 1;
        	public static final byte USER = 2;
        }
        
        public static class TcpController
        {
        	public static final byte ModuleId = 10;
        	// Output
        	public static final byte REQUEST = 1;
        	public static final byte AUTH_KEY = 7;
        	// Input
        	public static final byte RZV_DATA = 1;        	
        	public static final byte REQUEST_ERROR = 2;
        	public static final byte AUTH_HASH = 7;
        	public static final byte AUTH_ERROR = 8;
        }

        public static class UdpController
        {
        	public static final byte ModuleId = 11;
        	// Output
        	public static final byte REQUEST = 1;
        	public static final byte AUTH_KEY = 7;
        	// Input
        	public static final byte RZV_DATA = 1;        	
        	public static final byte REQUEST_ERROR = 2;
        	public static final byte AUTH_HASH = 7;
        	public static final byte AUTH_ERROR = 8;
        }

        public static class RpcController
        {
        	public static final byte ModuleId = 14;        
        	// Output
        	public static final byte REQUEST = 1;
        	// Input
        	public static final byte RESULT = 1;        	
        	public static final byte APP_ERROR = 2;        	
        	public static final byte SOFTNET_ERROR = 3;        	
        }
        
        public static class EventController
        {
        	public static final byte ModuleId = 15;
            // Output
        	public static final byte REPLACING_EVENT_ACK = 1;
        	public static final byte QUEUEING_EVENT_ACK = 2;
        	public static final byte PRIVATE_EVENT_ACK = 4;
        	public static final byte EVENT_REJECTED = 5;
        	
        	public static final byte SYNC_OK = 6;
        	public static final byte SUBSCRIPTIONS = 7;
        	public static final byte ADD_SUBSCRIPTION = 8;
        	public static final byte REMOVE_SUBSCRIPTION = 9;

            // Input
        	public static final byte REPLACING_EVENT = 1;
        	public static final byte QUEUEING_EVENT = 2;
        	public static final byte PRIVATE_EVENT = 4;
        	public static final byte REPLACING_NULL_EVENT = 5;
        	
        	public static final byte SYNC = 6;
        	public static final byte ILLEGAL_SUBSCRIPTION = 7;
        }         
    }
    
    public static class Proxy
    {
        public static class TcpConnector        
        {
        	// Output
        	public static final byte CLIENT_P2P = 1;
        	public static final byte SERVICE_P2P = 2;
        	public static final byte CLIENT_PROXY = 3;
        	public static final byte SERVICE_PROXY = 4;
        	public static final byte AUTH_HASH = 5;
        	public static final byte P2P_FAILED = 6;
            // Input
        	public static final byte AUTH_KEY = 11;
        	public static final byte CREATE_P2P_CONNECTION = 12;
        	public static final byte CREATE_P2P_CONNECTION_IN_DUAL_MODE = 13;
        	public static final byte CREATE_PROXY_CONNECTION = 14;
        	public static final byte ERROR = 15;
        }
        
        public static class TcpProxy        
        {
        	// Input/Output
        	public static final byte CLIENT_PROXY_ENDPOINT = 1;
        	public static final byte SERVICE_PROXY_ENDPOINT = 2;
        }
        
        public static class UdpConnector        
        {
        	// Output
        	public static final byte CLIENT = 1;
        	public static final byte SERVICE = 2;
        	public static final byte AUTH_HASH = 3;
        	public static final byte P2P_FAILED = 4;
        	// Input
        	public static final byte AUTH_KEY = 11;
        	public static final byte CREATE_P2P_CONNECTION = 12;
        	public static final byte CREATE_P2P_CONNECTION_IN_DUAL_MODE = 13;
        	public static final byte ERROR = 14;
        	// Input/Output
        	public static final byte P2P_HOLE_PUNCHED = 21;        	            
        	public static final byte P2P_LOCAL_HOLE_PUNCHED = 22;        	            
        }

        public static class UdpEndpoint        
        {
        	// Input/Output
        	public static final byte ATTACH_TO_CONNECTOR = 86;
        	public static final byte ATTACHED = 87;
        	public static final byte P2P_HOLE_PUNCH = 88;
        	public static final byte P2P_LOCAL_HOLE_PUNCH = 89;
        	public static final byte PROXY_ESTABLISHED = 90;
        }
    }
}

