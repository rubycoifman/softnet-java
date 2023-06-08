package softnet.utils;

public class Fnv1a
{
	public static int get32BitHash(byte[] data)
	{
		return get32BitHash(data, 0, data.length);
	}

	public static int get32BitHash(byte[] data, int offset)
	{
		return get32BitHash(data, offset, data.length - offset);
	}
	
	public static int get32BitHash(byte[] data, int offset, int size)
	{
		final int p = 16777619;
        int hash = -2128831035; // 0x811C9DC5;

        for (int i = offset; i < (offset + size); i++)
            hash = (hash ^ (data[i] & 0xff)) * p;
        
        return hash;
	}
}
