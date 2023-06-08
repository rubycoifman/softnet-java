package softnet.core;

import softnet.asn.*;

public class MsgBuilder extends SoftnetMessage
{
	private MsgBuilder(byte[] buffer, int offset)
	{
		super(buffer, offset, buffer.length - offset);
	}
	
	public static SoftnetMessage Create(byte componentId, byte messageType,  ASNEncoder asnEncoder)
    {
		byte[] encoding = asnEncoder.getEncoding(7);
		encoding[5] = componentId;
		encoding[6] = messageType;    	
    	int offset = encodeLength(encoding, 5);
    	return new MsgBuilder(encoding, offset);
    }
	
	public static SoftnetMessage Create(byte componentId, byte messageType,  ASNEncoder asnEncoder, int headerSize)
    {
		if(headerSize < 0 || headerSize > 64)
			throw new IllegalArgumentException("The value of 'headerSize' must be in the range [0, 64].");
		
		int offset = 7 + headerSize;
		byte[] buffer = asnEncoder.getEncoding(offset);
		buffer[offset - 2] = componentId;
		buffer[offset - 1] = messageType;    	
    	offset = encodeLength(buffer, offset - 2);
    	return new MsgBuilder(buffer, offset - headerSize);
    }
	
	public static SoftnetMessage Create(byte messageType, ASNEncoder asnEncoder)
    {
		byte[] buffer = asnEncoder.getEncoding(6);
		buffer[5] = messageType;
    	int offset = encodeLength(buffer, 5);
    	return new MsgBuilder(buffer, offset);
    }

	public static SoftnetMessage Create(byte messageType, ASNEncoder asnEncoder, int headerSize)
    {
		if(headerSize < 0 || headerSize > 64)
			throw new IllegalArgumentException("The value of 'headerSize' must be in the range [0, 64].");

		int offset = 6 + headerSize;
		byte[] buffer = asnEncoder.getEncoding(offset);
		buffer[offset - 1] = messageType;
    	offset = encodeLength(buffer, offset - 1);
    	return new MsgBuilder(buffer, offset - headerSize);
    }

	public static SoftnetMessage Create(byte componentId, byte messageType)
    {
		byte[] buffer = new byte[3];
    	buffer[0] = 2;
    	buffer[1] = componentId;
    	buffer[2] = messageType;    	
    	return new MsgBuilder(buffer, 0);
    }
	
	public static SoftnetMessage Create(byte messageType)
    {
		byte[] buffer = new byte[2];
    	buffer[0] = 1;
    	buffer[1] = messageType;    	
    	return new MsgBuilder(buffer, 0);
    }
	
	private static int encodeLength(byte[] buffer, int offset)
	{
		int dataSize = buffer.length - offset;
        if (dataSize <= 127)
        {
        	offset -= 1;
            buffer[offset] = (byte)dataSize;
            return offset;
        }
        
        if (dataSize <= 255)
        {
            buffer[offset - 2] = (byte)0x81;
            buffer[offset - 1] = (byte)dataSize;
            offset -= 2;
            return offset;
        }
        
        if(dataSize <= 0x0000ffff)
        {
        	buffer[offset - 3] = (byte)0x82;
            buffer[offset - 2] = (byte)((dataSize & 0x0000ff00) >> 8);
            buffer[offset - 1] = (byte)(dataSize & 0x000000ff);
        	offset -= 3;
            return offset;
        }
        
        if(dataSize <= 0x00ffffff)
        {
        	buffer[offset - 4] = (byte)0x83;
            buffer[offset - 3] = (byte)((dataSize & 0x00ff0000) >> 16);
            buffer[offset - 2] = (byte)((dataSize & 0x0000ff00) >> 8);
            buffer[offset - 1] = (byte)(dataSize & 0x000000ff);
        	offset -= 4;
            return offset;
        }
        
        if(dataSize <= 0x7fffffff)
        {
        	buffer[offset - 5] = (byte)0x84;
            buffer[offset - 4] = (byte)((dataSize & 0xff000000) >> 24);
            buffer[offset - 3] = (byte)((dataSize & 0x00ff0000) >> 16);
            buffer[offset - 2] = (byte)((dataSize & 0x0000ff00) >> 8);
            buffer[offset - 1] = (byte)(dataSize & 0x000000ff);
        	offset -= 5;
            return offset;
        }
        
        throw new IllegalArgumentException("The size of the softnet message exceeds 2GB.");        
	}
}
