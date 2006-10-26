/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */


/* Create New ServerSocket*/

task Startup(StartupObject s {initialstate}) {
	System.printString("W> Starting\n");
	ServerSocket ss=new ServerSocket(9000);
	System.printString("W> Creating ServerSocket\n");
	taskexit(s {!initialstate}); /* Turns initial state flag off, so this task won't refire */
}

/*Listen for a request and accept request*/ 
task AcceptConnection(ServerSocket ss{SocketPending}) {
	System.printString("W> Waiting for connection...\n");
	WebServerSocket web = new WebServerSocket() {WritePending};
	ss.accept(web);
	System.printString("W> Connected... \n");
}

/* Send a Write Request to Client*/
task WriteIO(WebServerSocket web{WritePending}) {
	System.printString("W> Before doing WriteIO 0\n");
	web.httpresponse();
	System.printString("W> After doing WriteIO\n");
	web.close();
 	taskexit(web {!WritePending});	
}

