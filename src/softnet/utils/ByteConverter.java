package softnet.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

public class ByteConverter
{
	public static byte[] getBytes(long value)
	{
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(new byte[8]);
		buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
		buffer.putLong(value);
		return buffer.array();
	}
	
	public static byte[] getBytes(int value)
	{
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(new byte[4]);
		buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
		buffer.putInt(value);
		return buffer.array();
	}
	
	public static byte[] getBytes(short value)
	{
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(new byte[2]);
		buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
		buffer.putShort(value);
		return buffer.array();
	}
	
	public static byte[] getBytes(UUID value)
	{
		java.nio.ByteBuffer nioBuffer = java.nio.ByteBuffer.wrap(new byte[16]);
		nioBuffer.putLong(value.getMostSignificantBits());
		nioBuffer.putLong(value.getLeastSignificantBits());
		return nioBuffer.array();
	}
	
	public static int toInt64(byte[] buffer, int offset)
	{
		int b7 = buffer[offset];
		int b6 = buffer[offset + 1] & 0xFF;
		int b5 = buffer[offset + 2] & 0xFF;
		int b4 = buffer[offset + 3] & 0xFF;
		int b3 = buffer[offset + 4] & 0xFF;
		int b2 = buffer[offset + 5] & 0xFF;
		int b1 = buffer[offset + 6] & 0xFF;
		int b0 = buffer[offset + 7] & 0xFF;
    	return (b7 << 56) | (b6 << 48) | (b5 << 40) | (b4 << 32) | (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
	}	
	
	public static int toInt32(byte[] buffer, int offset)
	{
		int b3 = buffer[offset];
		int b2 = buffer[offset + 1] & 0xFF;
		int b1 = buffer[offset + 2] & 0xFF;
		int b0 = buffer[offset + 3] & 0xFF;
    	return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
	}	

	public static int toInt32FromInt16(byte[] buffer, int offset)
	{		
		int b1 = buffer[offset];
		int b0 = buffer[offset + 1] & 0xFF;
    	return (b1 << 8) | b0;
	}
	
	public static int toInt32FromUInt16(byte[] buffer, int offset)
	{		
		int b1 = buffer[offset] & 0xFF;
		int b0 = buffer[offset + 1] & 0xFF;
    	return (b1 << 8) | b0;
	}
	
	public static void writeAsInt16(int value, byte[] buffer, int offset)
	{
		if(-32768 <= value && value <= 32767)
		{
			buffer[offset] = (byte)((value >> 8) & 0xFF);
			buffer[offset + 1] = (byte)(value & 0xFF);
			return;
		}
		
		throw new IllegalArgumentException("The value is out of range [-32768, 32767].");
	}

	public static void writeAsUInt16(int value, byte[] buffer, int offset)
	{
		if(0 <= value && value <= 65535)
		{
			buffer[offset] = (byte)((value >> 8) & 0xFF);
			buffer[offset + 1] = (byte)(value & 0xFF);
			return;
		}
		
		throw new IllegalArgumentException("The value is out of range [0, 65535].");
	}

	public static UUID toUuid(byte[] buffer)
	{
		if (buffer.length != 16)
			throw new IllegalArgumentException("The length of the octet string must be 16 bytes.");
		
		java.nio.ByteBuffer nioBuffer = java.nio.ByteBuffer.wrap(buffer);    
		nioBuffer.order(java.nio.ByteOrder.BIG_ENDIAN);
		return new UUID(nioBuffer.getLong(), nioBuffer.getLong());
	}
	
	public static UUID toUuid(byte[] buffer, int offset)
	{
		if (offset < 0 || offset + 16 > buffer.length)
			throw new IllegalArgumentException("The length of the octet string must be 16 bytes.");
		
		java.nio.ByteBuffer nioBuffer = java.nio.ByteBuffer.wrap(buffer, offset, 16);            
		nioBuffer.order(java.nio.ByteOrder.BIG_ENDIAN);
		return new UUID(nioBuffer.getLong(), nioBuffer.getLong());
	}
	
	public static java.net.InetAddress toInetAddress(byte[] buffer) throws softnet.exceptions.FormatException
	{
		try
		{
			return InetAddress.getByAddress(buffer);
		}
		catch(UnknownHostException e) 
		{
			throw new softnet.exceptions.FormatException();
		}		
	}
}
