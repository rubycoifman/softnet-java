package softnet.utils;

public class ByteArrays
{
	public static boolean equals(byte[] left, int left_offset, byte[] right, int right_offset, int size)
	{
        for (int i = 0; i < size; i++)
        {
            if (left[left_offset + i] != right[right_offset + i])
                return false;
        }
        return true;
	}
}
