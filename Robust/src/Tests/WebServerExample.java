/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

/* Create New ServerSocket*/
task Startup(StartupObject s {initialstate}) {
	System.printString("W> Starting\n");
	ServerSocket ss = new ServerSocket(9000);
	System.printString("W> Creating ServerSocket\n");
	Logger log = new Logger() {LogPending};
	taskexit(s {!initialstate}); /* Turns initial state flag off, so this task won't refire */
}

/*Listen for a request and accept request*/ 
task AcceptConnection(ServerSocket ss{SocketPending}) {
	System.printString("W> Waiting for connection...\n");
	WebServerSocket web = new WebServerSocket() {!WritePending};
	ss.accept(web);
	System.printString("W> Connected... \n");
}

/* Process the incoming http request */
task ProcessRequest(WebServerSocket web{IOPending}) {
	System.printString("W> Inside ProcessRequest... \n");
	//web.clientrequest();
	web.debug_read();
	taskexit(web {WritePending});
}

task testWritePending(WebServerSocket web{WritePending}) {
	System.printString("W> Testing WritePending");
	//taskexit(web {!WritePending, testflag});
	taskexit(web {!WritePending});
}

task testflag(WebServerSocket web{testflag}) {
	System.printString("DEBUG -> Test flag is true");
	taskexit(web {!testflag});
}

/*
task LogRequest(Logger log{LogPending}) {
	log.logrequest();
	System.printString("L> Inside logrequest");
	taskexit(log {!LogPending});
}
*/
