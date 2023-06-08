package softnet.core;

public interface BiAcceptor<T, U>
{
	void accept(T t, U u);
}
