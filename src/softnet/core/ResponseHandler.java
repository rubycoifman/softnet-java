package softnet.core;

public interface ResponseHandler<T,E>
{
	void onSuccess(T t);
	void onError(E e);	
}
