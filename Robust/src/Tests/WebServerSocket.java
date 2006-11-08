public class WebServerSocket extends Socket {
	// Websocket flag
	flag LogPending;
	flag WritePending;
	flag TransPending;
	flag WebInitialize;
	
	//Filename requested by the client 
	String filename;
	String[] parsed; 
		
	//Constructor
	public WebServerSocket(){
		parsed = new String[4];
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

	//Read the client request and extract the filename from it	
	public int clientrequest(){
		byte b1[] = new byte[1024];
		read(b1);//Read client request from web server socket
		String clientreq = new String(b1);
		int index = clientreq.indexOf('/');//Parse the GET client request to find filename
		int end = clientreq.indexOf('H');
		filename = clientreq.subString((index+1), (end-1));
		System.printString("\n");
		return 0;
	}
	
	// Parse  for the prefix in the client request
	// This is helpful to find if the prefix is a special transaction
	public boolean checktrans(){
		if (filename.startsWith("trans") == true) {
			return true;
		} else {
			return false;
		}
	}

 	//Parse for the substrings in the filename and use it to obtain the
	//kind of operation, name of item, quantity of item, price of item
	//e.g. trans_add_car_2_10000 is the filename
	//store in the parsed[] string , add,car,2,1000
	public int parseTransaction(){
		int start = filename.indexOf('_');
		String s = filename.subString(start+1);

		if (s.startsWith("add")==true){
			System.printString("DEBUG > ADD\n");
			int i1 = s.indexOf('_');
			parsed[0] = new String(s.subString(0,i1));

			String s1 = s.subString(i1+1);
			int i2 = s1.indexOf('_');
			parsed[1] = new String(s1.subString(0,i2));
			
			String s2 = s1.subString(i2+1);
			int i3 = s2.indexOf('_');
			parsed[2] = new String(s2.subString(0,i3));
			
			String s3 = s2.subString(i3+1);
			parsed[3] = s3;
			
			return 0;
			
		}
		if (s.startsWith("buy")==true){
			System.printString("DEBUG > BUY\n");
			int i1 = s.indexOf('_');
			parsed[0] = s.subString(0,i1);

			String s1 = s.subString(i1+1);
			int i2 = s1.indexOf('_');
			parsed[1] = s1.subString(0,i2);
			
			String s2 = s1.subString(i2+1);
			parsed[2] = s2;
			
			parsed[3] = "";
		
			return 1;
		}
		if (s.startsWith("inventory")==true){
			System.printString("DEBUG > INVENTORY\n");
			return 2;

		}
		// Error transaction
		return -1;
	}
}
