/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

// Create New ServerSocket
task Startup(StartupObject s {initialstate}) {
//	System.printString("W> Starting\n");
	ServerSocket ss = new ServerSocket(9000);
//	System.printString("W> Creating ServerSocket\n");
	Logger log = new Logger() {Initialize};
	Inventory inventorylist = new Inventory(){TransInitialize};
	taskexit(s {!initialstate}); /* Turns initial state flag off, so this task won't refire */
}

//Listen for a request and accept request 
task AcceptConnection(ServerSocket ss{SocketPending}) {
    //	System.printString("W> Waiting for connection...\n");
    tag t=new tag(link);
	WebServerSocket web = new WebServerSocket() {!WritePending, !TransPending, WebInitialize}{t};
	ss.accept(t);
//	System.printString("W> Connected... \n");
}

// Process the incoming http request 
task ProcessRequest(WebServerSocket web{WebInitialize}{link l}, Socket s{IOPending}{link l}) {
	if (web.clientrequest(s)) {
	    if(web.checktrans()==false)
		// Not special transaction , do normal filesending	
		taskexit(web {WritePending, LogPending,!WebInitialize}); //Sets the WritePending and LogPending flag true 
	    else 
		// Invoke special inventory transaction
		taskexit(web {TransPending, LogPending,!WebInitialize});
	}
}

//Do the WriteIO on server socket and send the requested file to Client
task SendFile(WebServerSocket web{WritePending}{link l}, Socket s{}{link l}) {
//	System.printString("W> Inside SendFile ... \n");
	web.sendfile(s);
	s.close();
	taskexit(web {!WritePending});
}

// Log the Client request
task LogRequest(WebServerSocket web{LogPending}, Logger log{Initialize}) {
//Task fired when both
// LogPending and Initialize flags are true 
//	System.printString("L > Inside logrequest\n");
	log.logrequest(web.filename);
	taskexit(web {!LogPending});
}

//Transaction on Inventory
task Transaction(WebServerSocket web{TransPending}{link l}, Inventory inventorylist{TransInitialize},Socket s{}{link l}){ //Task for WebServerTransactions
//	System.printString("T > Inside Transaction\n");
	// Parse
	int op = web.parseTransaction();
	// Check for the kind of operation
	if (op == 0 ) { /* Add */
//		System.printString("DEBUG > Calling add transaction\n");
		Integer qty = new Integer(web.parsed[2]);
		Integer price = new Integer(web.parsed[3]);
		int ret = inventorylist.additem(web.parsed[1], qty.intValue(), price.intValue());
		if (ret == 0) {
			web.httpresponse(s);
			StringBuffer st = new StringBuffer("Added Item ");
			st.append(web.parsed[1]);
			st.append(" Quantity ");
			st.append(web.parsed[2]);
			st.append(" Price ");
			st.append(web.parsed[3]);
			st.append("\n");
			String towrite = new String(st);
			s.write(towrite.getBytes());
		} else {
			web.httpresponse(s);
			String st = new String("Error encountered");
			s.write(st.getBytes());
		}	
	} else if (op == 1) { /* Buy */
//		System.printString("DEBUG > Calling buy transaction\n");
		Integer qty = new Integer(web.parsed[2]);
		int ret = inventorylist.buyitem(web.parsed[1], qty.intValue());
		if (ret >= 0) {
			web.httpresponse(s);
			StringBuffer st = new StringBuffer("Bought item ");
			st.append(web.parsed[1]);
			st.append(" Quantity ");
			st.append(web.parsed[2]);
			st.append(" Cost ");
			Integer cost = new Integer(ret*qty.intValue());
			String c = cost.toString();
			st.append(c);
			String towrite = new String(st);
			s.write(towrite.getBytes());
		} else {
			web.httpresponse(s);
			String st = new String("Error encountered");
			s.write(st.getBytes());
		}
	} else if (op == 2) { /* Inventory */
//		System.printString("DEBUG > Calling inventory transaction\n");
		web.httpresponse(s);
		inventorylist.inventory(s);	
	} else { /* Error */ 
//		System.printString("T > Error - Unknown transaction\n");
	}
	//Invoke close operations
	s.close();
	taskexit(web {!TransPending});
}
