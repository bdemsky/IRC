/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

// Create New ServerSocket
task Startup(StartupObject s {initialstate}) {
	System.printString("W> Starting\n");
	ServerSocket ss = new ServerSocket(9000);
	System.printString("W> Creating ServerSocket\n");
	Logger log = new Logger() {Initialize};
	taskexit(s {!initialstate}); /* Turns initial state flag off, so this task won't refire */
}

//Listen for a request and accept request 
task AcceptConnection(ServerSocket ss{SocketPending}) {
	System.printString("W> Waiting for connection...\n");
	WebServerSocket web = new WebServerSocket() {!WritePending};
	ss.accept(web);
	System.printString("W> Connected... \n");
}

// Process the incoming http request 
task ProcessRequest(WebServerSocket web{IOPending}) {
	System.printString("W> Inside ProcessRequest... \n");
	web.clientrequest();
	taskexit(web {WritePending, LogPending}); //Sets the WritePending and LogPending flag true 
}

//Do the WriteIO on server socket and send the requested file to Client
task SendFile(WebServerSocket web{WritePending}) {
	System.printString("W> Inside SendFile ... \n");
	web.sendfile();
	web.close();
	taskexit(web {!WritePending});
}

// Log the Client request
task LogRequest(WebServerSocket web{LogPending}, Logger log{Initialize}) {//Task fired when both
									 // LogPending and Initialize flags are true 
	System.printString("L > Inside logrequest\n");
	log.logrequest(web.filename);
	taskexit(web {!LogPending});
}
