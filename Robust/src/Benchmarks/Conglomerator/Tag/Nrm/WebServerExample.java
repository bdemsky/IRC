/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

// Create New ServerSocket
task Startup(StartupObject s {initialstate}) {
	ServerSocket ss = new ServerSocket(9000);
	Logger log = new Logger() {Initialize};
	Inventory inventorylist = new Inventory(){TransInitialize};
	taskexit(s {!initialstate}); /* Turns initial state flag off, so this task won't refire */
}

task LookupS(Stock l{initialstate}) {
    String query="GET /"+l.url+" HTTP/1.1\r\nConnection: close\r\nHost:"+l.hostname+"\r\n\r\n";
    l.connect(l.hostname, 80);
    l.write(query.getBytes());
    taskexit(l{!initialstate, query});
}

task ReceiveQueryS(Stock l{query&&IOPending}) {
    byte[] buffer=new byte[1024];
    int numchars=l.read(buffer);
    if (numchars<=0) {
	l.fix();
	l.close();
	taskexit(l{!query,done}{});
    }
    String str=new String(buffer, 0, numchars);
    if (l.data==null) {
	l.data=str;
    } else
	l.data=l.data+str;
    taskexit;
}

task LookupG(Google l{initialstate}) {
    String query="GET /"+l.url+" HTTP/1.1\r\nConnection: close\r\nHost:"+l.hostname+"\r\n\r\n";
    l.connect(l.hostname, 80);
    l.write(query.getBytes());
    taskexit(l{!initialstate, query});
}

task ReceiveQueryG(Google l{query&&IOPending}) {
    byte[] buffer=new byte[1024];
    int numchars=l.read(buffer);
    if (numchars<=0) {
	l.fix();
	l.close();
	taskexit(l{!query,done}{});
    }
    String str=new String(buffer, 0, numchars);
    if (l.data==null) {
	l.data=str;
    } else
	l.data=l.data+str;
    taskexit;
}

task LookupW(Weather l{initialstate}) {
    String query="GET /"+l.url+" HTTP/1.1\r\nConnection: close\r\nHost:"+l.hostname+"\r\n\r\n";
    l.connect(l.hostname, 80);
    l.write(query.getBytes());
    taskexit(l{!initialstate, query});
}

task ReceiveQueryW(Weather l{query&&IOPending}) {
    byte[] buffer=new byte[1024];
    int numchars=l.read(buffer);
    if (numchars<=0) {
	l.fix();
	l.close();
	taskexit(l{!query,done}{});
    }
    String str=new String(buffer, 0, numchars);
    if (l.data==null) {
	l.data=str;
    } else
	l.data=l.data+str;
    taskexit;
}



//Listen for a request and accept request 
task AcceptConnection(ServerSocket ss{SocketPending}) {
    //	System.printString("W> Waiting for connection...\n");
    tag t=new tag(link);
    WebServerSocket web = new WebServerSocket() {!WritePending, !TransPending, WebInitialize}{t};
    MySocket ms=new MySocket(){}{t};
    ss.accept(ms);
//	System.printString("W> Connected... \n");
}

// Process the incoming http request 
task ProcessRequest(WebServerSocket web{WebInitialize}{link l}, MySocket s{IOPending}{link l}) {
	if (web.clientrequest(s)) {
	    if(web.checktrans()==false)
		// Not special transaction , do normal filesending	
		taskexit(web {WritePending, LogPending,!WebInitialize}); //Sets the WritePending and LogPending flag true 
	    else {
		Weather w=new Weather(){initialstate}{l};
		Google g=new Google(){initialstate}{l};
		Stock st=new Stock(){initialstate}{l};
		taskexit(web {TransPending, LogPending,!WebInitialize});
	    }
	}
}

//Do the WriteIO on server socket and send the requested file to Client
task SendFile(WebServerSocket web{WritePending}{link l}, MySocket s{}{link l}) {
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
task Transaction(WebServerSocket web{TransPending}{link l}, Weather weather{done}{link l}, Google g{done}{link l}, MySocket s{}{link l}, Stock st{done}{link l}){ //Task for WebServerTransactions
	web.httpresponse(s);
	s.write(("<html>").getBytes());
	s.write(weather.data.getBytes());
	s.write(g.data.getBytes());
	s.write(st.data.getBytes());
	s.write(("</html>").getBytes());
	s.close();
	taskexit(web {!TransPending});
}
