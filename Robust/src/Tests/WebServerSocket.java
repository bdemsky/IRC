public class WebServerSocket extends Socket {
	// Websocket flag
	flag ReadPending;
	flag WritePending;
	
	//Constructor
	public WebServerSocket(){
		Logger log = new Logger();
		log.logrequest();		
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

	//Send the http header for web browser display	
	public void httpresponse(){
		StringBuffer header = new StringBuffer("HTTP/1.0 200 OK\n");
		StringBuffer htmlBuffer = new StringBuffer("<HTML>\n");

		header.append("Content-type: text/html\n");
		header.append("\n\n");
		String temp_str = new String(header);
		write(temp_str.getBytes());
		return;

	}
	
	//Send the html file , read from file one byte at a time	
	public void sendfile() {
		String filepath = new String("./Tests/htmlfiles/index2.html");
		FileInputStream def_file = new FileInputStream(filepath);
		byte buf[] = new byte[16];
		int ret;
		
		while ((ret = def_file.read(buf)) > 0) {
			byte tosend[] = new byte[ret];
			for (int i = 0; i < ret; i++) {
				tosend[i] = buf[i];
			}
			write(tosend);
			String str = new String(tosend);
		}
		def_file.close();
	}


}

