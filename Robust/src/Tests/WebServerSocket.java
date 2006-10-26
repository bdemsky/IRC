public class WebServerSocket extends Socket {
	// Websocket flag
	flag ReadPending;
	flag WritePending;
	//File Descriptor
	int fd;

	//Constructor
	public WebServerSocket(){

	}
	
	public void datawrite(){ 
		byte[] b = new byte[10];
		b[0] =(byte)'H';
		b[1] =(byte)'E';
		b[2] =(byte)'L';
		b[3] =(byte)'L';
		b[4] =(byte)'O';
	//	b[5] =(byte)'\n';
		b[6] =(byte)'T';
		b[7] =(byte)'E';
		b[8] =(byte)'S';
		b[9] =(byte)'T';
		write(b);
	}
	
	public void httpresponse(){
	
		StringBuffer header = new StringBuffer("HTTP/1.0 200 OK\n");
		StringBuffer htmlBuffer = new StringBuffer("<HTML>\n");

		header.append("Content-type: text/html\n");
		header.append("Content-length: 88");
		header.append("\n\n");
		

		htmlBuffer.append("<HEAD>\n<TITLE>Test HTML Document</TITLE>\n");
		htmlBuffer.append(" </HEAD>\n");
		htmlBuffer.append("          \n");
		htmlBuffer.append("   <BODY>\n");
		htmlBuffer.append("   <h1>This is your java web server's default page.</h1>");
		htmlBuffer.append("   </BODY>\n");
		htmlBuffer.append("</HTML>\n"); 
	
		header.append(htmlBuffer.toString());
		
		String temp_str = new String(header);
		write(temp_str.getBytes());
		return;
	}
}

