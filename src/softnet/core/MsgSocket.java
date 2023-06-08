package softnet.core;

import java.lang.Thread;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Queue;
import java.util.LinkedList;

import softnet.exceptions.*;

public class MsgSocket 
{
	public Acceptor<byte[]> messageReceivedHandler;
	public Acceptor<NetworkErrorSoftnetException> networkErrorHandler;
	public Runnable formatErrorHandler;
	
	public int minLength = 2;
    public int maxLength = 127;
    
    private Object mutex = new Object(); 
    
	public MsgSocket(SocketChannel socketChannel)
	{
		this.socketChannel = socketChannel;
	}
			
	private boolean isClosed = false;
	public void close()
	{
		try
		{			
			synchronized(mutex)
			{
				isClosed = true;
				if(isSending == false)
					socketChannel.close();
			}
		}
		catch(IOException ex){}
	}
	
	public void start()
	{
		m_buffer = ByteBuffer.allocate(1024);
		outputMessageQueue = new LinkedList<ByteBuffer>();
		
		Thread thread = new Thread()
		{
		    public void run(){
		    	inputCircle();
		    }
		};
		thread.start();
	}
	
	public SocketChannel getChannel()
	{
		return socketChannel;
	}
	
	private SocketChannel socketChannel;

	private ByteBuffer m_buffer;
	private byte[] m_message;
	private int messageLength;
	private int messageBytesReceived;
	private boolean isReadingAtMessageOrigin = true;
		
	private void inputCircle()
	{
		try
		{
			while (isClosed == false)
			{
				int bytesRead = socketChannel.read(m_buffer);
				if (bytesRead == -1)
		        {
					if(isClosed == false)
					{
						networkErrorHandler.accept(new NetworkErrorSoftnetException("The softnet server closed the connection."));
						closeSocketChannel();
					}
		            return;
		        }
				
				m_buffer.flip();
				
				while (isClosed == false)
	            {
	                if (isReadingAtMessageOrigin)
	                {
	                    int firstByte = m_buffer.get(m_buffer.position());
	                    
	                    if (firstByte > 0)
	                    {
	                    	m_buffer.get();
	                    	messageLength = firstByte;
	                    }
	                    else if(firstByte < 0)
	                    {
	                    	int lengthBytes = firstByte & 0x0000007F;
	                    	if(lengthBytes < m_buffer.remaining())
	                    	{    
	                    		m_buffer.get();
	                    		messageLength = decodeLength(lengthBytes);
	                    	}
	                    	else
	                    	{
	                    		m_buffer.compact();
	                    		break;
	                    	}
	                    }
	                    else
	                    {
	                    	throw new FormatException();
	                    }
	                    
	                    if (messageLength < minLength || messageLength > maxLength)
	                        throw new FormatException();
	                    
	                    m_message = new byte[messageLength];
	                    
	                    if(messageLength == m_buffer.remaining())
	            		{
	            			m_buffer.get(m_message);
	            			m_buffer.clear();
	            			
	            			messageReceivedHandler.accept(m_message);
	            			m_message = null;
	            			break;
	            		}
	            		else if(messageLength < m_buffer.remaining())
	            		{
	            			m_buffer.get(m_message);
	            			
	            			messageReceivedHandler.accept(m_message);
	            			m_message = null;
	            		}
	            		else
	            		{
	            			messageBytesReceived = m_buffer.remaining();
	            			m_buffer.get(m_message, 0, messageBytesReceived);                    			
	            			m_buffer.clear();
	            			
	            			isReadingAtMessageOrigin = false;
	            			break;
	            		}
	                }
	                else
	                {
	                	int messageBytesRequired = messageLength - messageBytesReceived;
	                	if(messageBytesRequired == m_buffer.remaining())
	                	{
	                		m_buffer.get(m_message, messageBytesReceived, messageBytesRequired);                    			
	                		m_buffer.clear();
	            			
	                		messageReceivedHandler.accept(m_message);
	            			m_message = null;
	
	            			isReadingAtMessageOrigin = true;
	            			break;
	                	}
	                	else if(messageBytesRequired > m_buffer.remaining())
	                	{
	                		int bytesReceived = m_buffer.remaining();                		
	            			m_buffer.get(m_message, messageBytesReceived, bytesReceived);            
	            			m_buffer.clear();
	            			
	                		messageBytesReceived = messageBytesReceived + bytesReceived;
	                		break;
	                	}
	                	else // messageBytesRequired < m_buffer.remaining()
	                	{
	                		m_buffer.get(m_message, messageBytesReceived, messageBytesRequired); 
	                		
	                		messageReceivedHandler.accept(m_message);
	            			m_message = null;
	            			
	            			isReadingAtMessageOrigin = true;
	                	}
	                }
	            }
			}
		}
		catch(FormatException ex)
		{			
			formatErrorHandler.run();
			closeSocketChannel();
		}
		catch(IOException ex)
		{
			networkErrorHandler.accept(new NetworkErrorSoftnetException(ex.getMessage()));
			closeSocketChannel();
		}
	}
		
	private int decodeLength(int lengthBytes) throws FormatException
    {
        if (lengthBytes == 1)
        {
        	int length = m_buffer.get() & 0xFF;
            return length;
        }
        else if (lengthBytes == 2)
        {
        	int d1 = m_buffer.get() & 0xFF;
        	int d0 = m_buffer.get() & 0xFF;
            return (d1 << 8) + d0;
        }
        else if (lengthBytes == 3)
        {
        	int d2 = m_buffer.get() & 0xFF;
        	int d1 = m_buffer.get() & 0xFF;
        	int d0 = m_buffer.get() & 0xFF;
            return (d2 << 16) + (d1 << 8) + d0;
        }
        else // lengthBytes == 4
        {
        	int d3 = m_buffer.get();
        	if (d3 < 0)
                throw new FormatException();

        	int d2 = m_buffer.get() & 0xFF;
        	int d1 = m_buffer.get() & 0xFF;
        	int d0 = m_buffer.get() & 0xFF;
            return (d3 << 24) + (d2 << 16) + (d1 << 8) + d0;
        }
    }
	
	private Queue<ByteBuffer> outputMessageQueue;
	private boolean isSending = false;
	
	public void shutdownOutput()
	{
		try
		{
			synchronized(mutex)
	        {			
	            if (isSending == false && isClosed == false)
	            {
	            	socketChannel.shutdownOutput();
	            }
	        }
		}
		catch(IOException ex)
		{
			networkErrorHandler.accept(new NetworkErrorSoftnetException(ex.getMessage()));
			closeSocketChannel();
		}
	}

	public void send(SoftnetMessage message)
	{	
		ByteBuffer msg = ByteBuffer.wrap(message.buffer, message.offset, message.length);
		synchronized(mutex)
        {
			if(isClosed) 
				return;
            if (isSending)
            {
                outputMessageQueue.add(msg);
                return;                    
            }
            isSending = true;            
        }
		send(msg);
	}
		
	public void send(byte[] buffer, int offset, int length)
	{		
		ByteBuffer msg = ByteBuffer.wrap(buffer, offset, length);
		synchronized(mutex)
        {
			if(isClosed)
				return;
			
            if (isSending)
            {
                outputMessageQueue.add(msg);
                return;                    
            }
            
            isSending = true;
        }
		send(msg);
	}
	
	private void send(ByteBuffer message)
	{
		try		
		{
			while(isSending)
			{				
				socketChannel.write(message);
				
				synchronized(mutex)
                {
                    if (outputMessageQueue.size() == 0)
                    {
                        isSending = false;
                        if(isClosed)
                        {
                        	socketChannel.shutdownOutput();
                        	socketChannel.close();
                        }                        	
                        return;
                    }
                    message = outputMessageQueue.remove();
                }
			}
		}
		catch(IOException ex)
		{
			networkErrorHandler.accept(new NetworkErrorSoftnetException(ex.getMessage()));
			closeSocketChannel();
		}
	}
	
	private void closeSocketChannel()
	{
		try
		{
			socketChannel.close();
		}
		catch(IOException ex) {}
	}
}