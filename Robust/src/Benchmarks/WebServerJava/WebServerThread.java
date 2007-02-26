public class WebServerThread extends Thread {
    //Filename requested by the client 
    String filename;
    String[] parsed; 
    String prefix;
    Logger log;
    Inventory inventorylist;
    Socket sock;

    //Constructor
    public WebServerThread(Socket s, Logger log, Inventory inventory){
	parsed = new String[4];
	this.log=log;
	this.sock=s;
	this.inventorylist=inventory;
    }
    
    public void run() {
	// Process the incoming http request 
	while (!clientrequest()) {
	}
	if(checktrans()==false) {
	    // Not special transaction , do normal filesending	
	    SendFile();
	    LogRequest();
	} else {
	    // Invoke special inventory transaction
	    Transaction();
	    LogRequest();
	}
    }
    
    //Do the WriteIO on server socket and send the requested file to Client
    public void SendFile() {
	sendfile();
	sock.close();
    }

    // Log the Client request
    public void LogRequest() {
	log.logrequest(filename);
    }
    
    //Transaction on Inventory
    public void Transaction() {
	// Parse
	int op = parseTransaction();
	// Check for the kind of operation
	if (op == 0 ) { /* Add */
	    //		System.printString("DEBUG > Calling add transaction\n");
	    Integer qty = new Integer(parsed[2]);
	    Integer price = new Integer(parsed[3]);
	    int ret = inventorylist.additem(parsed[1], qty.intValue(), price.intValue());
	    if (ret == 0) {
		httpresponse();
		StringBuffer s = new StringBuffer("Added Item ");
		s.append(parsed[1]);
		s.append(" Quantity ");
		s.append(parsed[2]);
		s.append(" Price ");
		s.append(parsed[3]);
		s.append("\n");
		String towrite = new String(s);
		sock.write(towrite.getBytes());
	    } else {
		httpresponse();
		String s = new String("Error encountered");
		sock.write(s.getBytes());
	    }	
	} else if (op == 1) { /* Buy */
	    //		System.printString("DEBUG > Calling buy transaction\n");
	    Integer qty = new Integer(parsed[2]);
	    int ret = inventorylist.buyitem(parsed[1], qty.intValue());
	    if (ret >= 0) {
		httpresponse();
		StringBuffer s = new StringBuffer("Bought item ");
		s.append(parsed[1]);
		s.append(" Quantity ");
		s.append(parsed[2]);
		s.append(" Cost ");
		Integer cost = new Integer(ret*qty.intValue());
		String c = cost.toString();
		s.append(c);
		String towrite = new String(s);
		sock.write(towrite.getBytes());
	    } else {
		httpresponse();
		String s = new String("Error encountered");
		sock.write(s.getBytes());
	    }
	} else if (op == 2) { /* Inventory */
	    //		System.printString("DEBUG > Calling inventory transaction\n");
	    httpresponse();
	    inventorylist.inventory(sock);	
	} else { /* Error */ 
	    //		System.printString("T > Error - Unknown transaction\n");
	}
	//Invoke close operations
	sock.close();
    }

    
    //Send the http header for web browser display	
    public void httpresponse(){
	StringBuffer header = new StringBuffer("HTTP/1.0 200 OK\n");
	header.append("Content-type: text/html\n");
	header.append("\n\n");
	String temp_str = new String(header);
	sock.write(temp_str.getBytes());
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
	    sock.write(buffer.getBytes());
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
	    sock.write(tosend);
	    //String str = new String(tosend);
	}
	def_file.close();
    }
    
    //Read the client request and extract the filename from it	
    public boolean clientrequest(){
	byte b1[] = new byte[1024];
	int numbytes=sock.read(b1);//Read client request from web server socket
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
