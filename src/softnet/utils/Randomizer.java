package softnet.utils;

import java.util.Random;

public class Randomizer
{
	static Random Rnd;
    static
    {
        Rnd = new Random();
    }

    public static int int32()
    {
        return Rnd.nextInt();
    }

    public static int int32(int maxValue)
    { 
        return Rnd.nextInt(maxValue);
    }
    
    static public byte[] octetString(int length)
    {
        byte[] buffer = new byte[length];
        Rnd.nextBytes(buffer);
        return buffer;
    }
}
