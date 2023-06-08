package softnet.core;

public class Triplet<F, S, T> {
	public final F First;
    public final S Second;
    public final T Third;
    public Triplet(F first, S second, T third)
    {
    	First = first;
    	Second = second;
    	Third = third;
    }
}
