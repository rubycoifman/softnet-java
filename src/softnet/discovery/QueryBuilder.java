package softnet.discovery;

public abstract class QueryBuilder {
	 public abstract java.nio.ByteBuffer GetQuery();
	 public abstract void ThrowException(int errorCode) throws softnet.exceptions.SoftnetException;	 
}
