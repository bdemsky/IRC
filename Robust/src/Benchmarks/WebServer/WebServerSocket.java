public class WebServerSocket extends Socket {
	// Websocket flag
	flag LogPending;
	flag WritePending;
	flag TransPending;
	flag WebInitialize;
	
	//Filename requested by the client 
	String filename;
        String[] parsed; 
        String prefix;
		
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
		StringBuffer req_file = new StringBuffer("./htmlfiles/");
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
	public boolean clientrequest(){
		byte b1[] = new byte[1024];
		int numbytes=read(b1);//Read client request from web server socket
		String curr=(new String(b1)).subString(0, numbytes);
		if (prefix!=null) {
		    StringBuffer sb=new StringBuffer(prefix);
		    sb.append(curr);
		    curr=sb.toString();
		}
		prefix=curr;
		if(prefix.indexOf("\r\n\r\n")>=0) {

		    int index = prefix.indexOf('/');//Parse the GET client request to find filename
		    int end = prefix.indexOf('H');
		    filename = prefix.subString((index+1), (end-1));
		    System.printString("\n");
		    return true;
		}
		return false;
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
	    //		System.printString("DEBUG > ADD\n");
	    int i1 = s.indexOf('_');
	    parsed[0] = new String(s.subString(0,i1));
	    
	    int i2 = s.indexOf('_',i1+1);
	    parsed[1] = new String(s.subString(i1+1,i2));
	    
	    int i3 = s.indexOf('_',i2+1);
	    parsed[2] = new String(s.subString(i2+1,i3));
	    
	    String s3 = s.subString(i3+1);
	    parsed[3] = s3;
	    
	    return 0;
	    
	}
	if (s.startsWith("buy")==true){
	    //		System.printString("DEBUG > BUY\n");
	    int i1 = s.indexOf('_');
	    parsed[0] = s.subString(0,i1);
	    
	    int i2 = s.indexOf('_', i1+1);
	    parsed[1] = s.subString(i1+1,i2);
	    
	    String s2 = s.subString(i2+1);
	    parsed[2] = s2;
	    
	    parsed[3] = "";
	    
	    return 1;
	}
	if (s.startsWith("inventory")==true){
	    //		System.printString("DEBUG > INVENTORY\n");
	    return 2;
	    
	}
	// Error transaction
	return -1;
    }
}
