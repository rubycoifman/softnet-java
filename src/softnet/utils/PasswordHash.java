package softnet.utils;

import softnet.exceptions.*;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordHash 
{
	public static byte[] compute(byte[] salt, String password) throws HostFunctionalitySoftnetException
	{
		try
		{
			MessageDigest msgDigest = MessageDigest.getInstance("SHA-1");
			byte[] password_bytes = password.getBytes("UTF-16BE");
			byte[] salt_and_password_bytes = new byte[salt.length + password_bytes.length];
			System.arraycopy(salt, 0, salt_and_password_bytes, 0, salt.length);
			System.arraycopy(password_bytes, 0, salt_and_password_bytes, salt.length, password_bytes.length);
			
			return msgDigest.digest(salt_and_password_bytes);
		}
		catch (NoSuchAlgorithmException ex) 
		{
			throw new HostFunctionalitySoftnetException(ex.getMessage());
		}		
		catch (UnsupportedEncodingException ex) 
		{
			throw new HostFunctionalitySoftnetException(ex.getMessage());
		}
	}
	
	public static byte[] compute(byte[] key1, byte[] key2, byte[] salt, String password) throws HostFunctionalitySoftnetException
	{
		try
		{
			MessageDigest msgDigest = MessageDigest.getInstance("SHA-1");
			byte[] password_bytes = password.getBytes("UTF-16BE");
			byte[] salt_and_password_bytes = new byte[salt.length + password_bytes.length];
			System.arraycopy(salt, 0, salt_and_password_bytes, 0, salt.length);
			System.arraycopy(password_bytes, 0, salt_and_password_bytes, salt.length, password_bytes.length);
			
			byte[] saltedPassword = msgDigest.digest(salt_and_password_bytes);
			
			byte[] buffer = new byte[key1.length + key2.length + saltedPassword.length];
			System.arraycopy(key1, 0, buffer, 0, key1.length);
			System.arraycopy(key2, 0, buffer, key1.length, key2.length);
			System.arraycopy(saltedPassword, 0, buffer, key1.length + key2.length, saltedPassword.length);
			
			return msgDigest.digest(buffer);
		}
		catch (NoSuchAlgorithmException ex) 
		{
			throw new HostFunctionalitySoftnetException(ex.getMessage());
		}		
		catch (UnsupportedEncodingException ex) 
		{
			throw new HostFunctionalitySoftnetException(ex.getMessage());
		}
	}
}
