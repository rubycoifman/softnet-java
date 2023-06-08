package softnet.core;

public interface Acceptor<T>
{
	void accept(T t);
}