public class WebServerSocket extends Socket {
	// Websocket flag
	flag ReadPending;
	flag WritePending;
	//File Descriptor
	int fd;
	// Buffer to store String/  
	public byte[] buffer;
	
	

	//Constructor
	public WebServerSocket(){
		System.printString("DEBUG : Calling WebServerSocket constructor\n");
	}
	
	public void dataread(){		

	}
	
	public void datawrite(){ 
		byte[] b = new byte[10];
		//byte x=(byte)'x'
		b[0] =(byte)'h';
		b[1] =(byte)'h';
		b[2] =(byte)'e';
		b[3] =(byte)'e';
		b[4] =(byte)'l';
		b[5] =(byte)'l';
		b[6] =(byte)'l';
		b[7] =(byte)'o';
//		b[0] = '65';
//		b[1] = '66';
//		b[2] =	'67';
//		b[3] = '68';
//		b[4] = '69';
		write(b);
	}

	public void responseclient(byte b){
	//	buffer = b;
	//	while( buffer!= null && buffer!= " ")
	//	if (
	}

	public void close();   
}

