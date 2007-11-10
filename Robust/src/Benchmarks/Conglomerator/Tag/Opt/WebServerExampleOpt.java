/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

task Startup(StartupObject s {initialstate}) {
	ServerSocket ss = new ServerSocket(9000);
	Logger log = new Logger() {Initialize};
	Inventory inventorylist = new Inventory(){TransInitialize};
	taskexit(s {!initialstate}); /* Turns initial state flag off, so this task won't refire */
}

task LookupS(Stock l{initialstate}) {
    l.doLookup();
    taskexit(l{!initialstate, query});
}

task ReceiveQueryS(Stock l{query&&IOPending}) {
    if (l.Receive()) taskexit(l{!query,done}{});
    else taskexit;
}

task LookupW(Weather l{initialstate}) {
    l.doLookup();
    taskexit(l{!initialstate, query});
}

task ReceiveQueryW(Weather l{query&&IOPending}) {
    if (l.Receive()) taskexit(l{!query,done}{});
    else taskexit;
}

task LookupG(Google l{initialstate}) {
    l.doLookup();
    taskexit(l{!initialstate, query});
}

task ReceiveQueryG(Google l{query&&IOPending}) {
    if (l.Receive()) taskexit(l{!query,done}{});
    else taskexit;
}

task AcceptConnection(ServerSocket ss{SocketPending}) {
    tag t=new tag(link);
    WebServerSocket web = new WebServerSocket() {!WritePending, !TransPending, WebInitialize}{t};
    MySocket ms=new MySocket(){}{t};
    ss.accept(ms);
}

task ProcessRequest(WebServerSocket web{WebInitialize}{link l}, MySocket s{IOPending}{link l}) {
	if (web.clientrequest(s)) {
	    if(web.checktrans()==false)
		taskexit(web {WritePending, LogPending,!WebInitialize});
	    else {
		Weather w=new Weather(){initialstate}{l};
		Google g=new Google(){initialstate}{l};
		Stock st=new Stock(){initialstate}{l};
		taskexit(web {TransPending, LogPending,!WebInitialize});
	    }
	}
}

task SendFile(WebServerSocket web{WritePending}{link l}, MySocket s{}{link l}) {
	web.sendfile(s);
	s.close();
	taskexit(web {!WritePending});
}

task LogRequest(WebServerSocket web{LogPending}, Logger log{Initialize}) {
	log.logrequest(web.filename);
	taskexit(web {!LogPending});
}

task Transaction(WebServerSocket web{TransPending}{link l}, optional Weather weather{done}{link l}, optional Google g{done}{link l}, MySocket s{}{link l}, optional Stock st{done}{link l}){
	web.httpresponse(s);
	s.write(("<html>").getBytes());
	if (isavailable(weather))
	    s.write(weather.data.getBytes());
	if (isavailable(g))
	    s.write(g.data.getBytes());
	if (isavailable(st)) {
	    s.write(st.data.getBytes());
	}
	s.write(("</html>").getBytes());
	s.close();
	taskexit(web {!TransPending});
}
