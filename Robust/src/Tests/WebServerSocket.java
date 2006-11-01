public class WebServerSocket extends Socket {
	// Websocket flag
	flag LogPending;
	flag WritePending;
	//Filename requested by the client 
	String filename;
	
	//Constructor
	public WebServerSocket(){
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
	
	// Send the html file , read from file one byte at a time	
	public void sendfile() {
		StringBuffer req_file = new StringBuffer("./Tests/htmlfiles/");
		req_file.append(filename);
		String filepath = new String(req_file);
		FileInputStream def_file = new FileInputStream(filepath);
		int status = def_file.getfd();//Checks if the file is present in 
					      //current directory	
		httpresponse();
		if (status == -1){
			StringBuffer response = new StringBuffer("404: not found: ");//Send 404 error if
										     // file not found
			response.append(filename);
			String buffer = new String(response);
			write(buffer.getBytes());
			def_file.close();
			return;
		}
		byte buf[] = new byte[16];
		int ret;
		
		while ((ret = def_file.read(buf)) > 0) {// Read from file and write 
							// one byte at a time into the socket 

			byte tosend[] = new byte[ret];
			for (int i = 0; i < ret; i++) {
				tosend[i] = buf[i];
			}
			write(tosend);
			//String str = new String(tosend);
		}
		def_file.close();
	}

	//Discover what the client wants and handle their request	
	public int clientrequest(){
		byte b1[] = new byte[1024];
		read(b1);//Read client request from web server socket
		String clientreq = new String(b1);
		int index = clientreq.indexOf('/');//Parse the GET client request to find filename
		int end = clientreq.indexOf('H');
		filename = clientreq.subString((index+1), (end-1));
		return 0;
	}
}

