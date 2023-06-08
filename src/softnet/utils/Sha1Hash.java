package softnet.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import softnet.exceptions.HostFunctionalitySoftnetException;

public class Sha1Hash
{
	public static byte[] compute(byte[] buffer) throws HostFunctionalitySoftnetException
	{
		try
		{
			MessageDigest msgDigest = MessageDigest.getInstance("SHA-1");			
			return msgDigest.digest(buffer);
		}
		catch (NoSuchAlgorithmException ex) 
		{
			throw new HostFunctionalitySoftnetException(ex.getMessage());
		}		
	}
}
