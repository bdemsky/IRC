public class BankAppSocket extends Socket
{
	flag BASocketInit;
	
	public BankAppSocket()
	{
	
	}
	
	public void send(String message)
	{
		write(message.getBytes());
	}
	
	public String receive()
	{
		byte buffer[] = new byte[64];
		
		int numbytes = read(buffer);
		
		//it's subString() not substring() like in Java
		String message = (new String(buffer)).subString(0, numbytes);
		
		return message;
	}
}
