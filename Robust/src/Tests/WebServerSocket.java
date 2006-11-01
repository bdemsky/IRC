public class WebServerSocket extends Socket {
	// Websocket flag
	flag ReadPending;
	flag WritePending;
	flag testflag;
	boolean parsed;
	
	//Constructor
	public WebServerSocket(){
		parsed = false;
	}
	
	//Send the http header for web browser display	
	public void httpresponse(){
		StringBuffer header = new StringBuffer("HTTP/1.0 200 OK\n");

		header.append("Content-type: text/html\n");
		header.append("\n\n");
		String temp_str = new String(header);
		write(temp_str.getBytes());
		return;

	}
	
	//Send the html file , read from file one byte at a time	
	public void sendfile(String filename) {
		//String filepath = new String("./Tests/htmlfiles/index1.html");
		StringBuffer req_file = new StringBuffer("./Tests/htmlfiles/");
		req_file.append(filename);
		String filepath = new String(req_file);
		FileInputStream def_file = new FileInputStream(filepath);
		int status = def_file.getfd();	
		if (status == -1){
			StringBuffer response = new StringBuffer("404: not found: ");
			response.append(filename);
			String buffer = new String(response);
			write(buffer.getBytes());
			//System.printString("File does not  exist");
			def_file.close();
			return;
		}
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

	//Discover what the client wants and handle their request	
	public int clientrequest(){
		byte b1[] = new byte[1024];
		while(read(b1)<0);
		//String clientreq = new String(b1);
		//System.printString(clientreq);
		//int index = clientreq.indexOf('/');
		//int end = clientreq.indexOf('H');
		//String filename = clientreq.subString((index+1), (end-1));
		System.printString("DEBUG -> Inside clientreq ");
		//System.printString(filename);
		return 0;
	}
		
	public int debug_read() {
		byte b1[] = new byte[1024];
		while(read(b1)<0);
		String dummy = new String(b1);
		System.printString(dummy);
		return 0;
	}

	public int checkrequest(){
		byte b1[] = new byte[1024];
		boolean found = false;

		while(read(b1) < 0);
		String clientreq = new String(b1);
		int length = clientreq.length();
		System.printString(clientreq);
		
	}	
	//Send response if html file requested not available
	public void htmlresponse(String filename){
		String s = new String(filename);
		

	}

}

