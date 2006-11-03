/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

// Create New ServerSocket
task Startup(StartupObject s {initialstate}) {
	System.printString("W> Starting\n");
	ServerSocket ss = new ServerSocket(9000);
	System.printString("W> Creating ServerSocket\n");
	Logger log = new Logger() {Initialize};
	Inventory inventorylist = new Inventory(3){TransInitialize};
	taskexit(s {!initialstate}); /* Turns initial state flag off, so this task won't refire */
}

//Listen for a request and accept request 
task AcceptConnection(ServerSocket ss{SocketPending}) {
	System.printString("W> Waiting for connection...\n");
	WebServerSocket web = new WebServerSocket() {!WritePending, !TransPending};
	ss.accept(web);
	System.printString("W> Connected... \n");
}

// Process the incoming http request 
task ProcessRequest(WebServerSocket web{IOPending}) {
	System.printString("W> Inside ProcessRequest... \n");
	web.clientrequest();
	if(web.checktrans()==false)
		// Not special transaction , do normal filesending	
		taskexit(web {WritePending, LogPending}); //Sets the WritePending and LogPending flag true 
	else 
		// Invoke special inventory transaction
		taskexit(web {TransPending, LogPending});
}

//Do the WriteIO on server socket and send the requested file to Client
task SendFile(WebServerSocket web{WritePending}) {
	System.printString("W> Inside SendFile ... \n");
	web.sendfile();
	web.close();
	taskexit(web {!WritePending});
}

// Log the Client request
task LogRequest(WebServerSocket web{LogPending}, Logger log{Initialize}) {
//Task fired when both
// LogPending and Initialize flags are true 
	System.printString("L > Inside logrequest\n");
	log.logrequest(web.filename);
	taskexit(web {!LogPending});
}

//Transaction on Inventory
task Transaction(WebServerSocket web{TransPending}, Inventory inventorylist{TransInitialize}){ //Task for WebServerTransactions
	System.printString("T > Inside Transaction\n");
	char[] op = new char[10];
	// Parse
	web.parseTransaction();
	// Check for the kind of operation
	op = web.parsed[0].toCharArray();
	if (op[0] == 'a') {
		Integer qty = new Integer(web.parsed[1]);
		Integer price = new Integer(web.parsed[2]);
		inventorylist.additem(web.parsed[0], qty.intValue(), price.intValue());
	} else if (op[0] == 'b') {
		Integer qty = new Integer(web.parsed[1]);
		Integer price = new Integer(web.parsed[2]);
		inventorylist.buyitem(web.parsed[0], qty.intValue(), price.intValue());
	} else if (op[0] == 'i') {
		inventorylist.inventory();
	} else {
		System.printString("T > Error - Unknown transaction\n");
	}

	//Invoke operations

	web.close();
	taskexit(web {!TransPending});
}
