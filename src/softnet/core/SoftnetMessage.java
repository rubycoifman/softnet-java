package softnet.core;

public abstract class SoftnetMessage
{	
	public final byte[] buffer;
	public final int offset;
	public final int length;
	
	protected SoftnetMessage(byte[] _buffer, int _offset, int _length)
	{
		buffer = _buffer;
		offset = _offset;
		length = _length;
	}
}
