public class WebServerExample extends Thread {
/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */
    Logger log;
    WebServerSocket web;
    Socket s;

    public WebServerExample(WebServerSocket web, Socket s, Logger log) {
	this.web=web;
	this.s=s;
	this.log=log;
    }

    public static void main(String x[]) {
	ServerSocket ss = new ServerSocket(9000);
	Logger log = new Logger();
	while(true) {
	    Socket s=ss.accept();
	    WebServerSocket web = new WebServerSocket();
	    WebServerExample wse=new WebServerExample(web, s, log);
	    wse.start();
	}
    }

    public void doLookup(Lookup l) {
	String query="GET /"+l.url+" HTTP/1.1\r\nConnection: close\r\nHost:"+l.hostname+"\r\n\r\n";
	l.s=new Socket(l.hostname, 80);
	l.s.write(query.getBytes());
    }

    public void ReceiveQuery(Lookup l) {
	byte[] buffer=new byte[1024];
	boolean cnt=true;

	while(cnt) {
	    int numchars=l.s.read(buffer);
	    if (numchars<=0) {
		l.fix();
		l.s.close();
		return;
	    }
	    String str=new String(buffer, 0, numchars);
	    if (l.data==null) {
		l.data=str;
	    } else
		l.data=l.data+str;
	}
    }

    public void run() {
	ProcessRequest();
    }

    public void ProcessRequest() {
	if (web.clientrequest(s)) {
	    if(web.checktrans()==false) {
		LogRequest();
		SendFile();
	    } else {
		Weather w=new Weather();
		Google g=new Google();
		Stock st=new Stock();
		doLookup(w);
		ReceiveQuery(w);
		doLookup(g);
		ReceiveQuery(g);
		doLookup(st);
		ReceiveQuery(st);
		LogRequest();
		Transaction(w, g, st);
	    }
	}
    }

    public void SendFile() {
	web.sendfile(s);
	s.close();
    }

    public void LogRequest() {
	log.logrequest(web.filename);
    }
    
    public void Transaction(Weather weather, Google g, Stock st){
	web.httpresponse(s);
	s.write(("<html>").getBytes());
	s.write(weather.data.getBytes());
	s.write(g.data.getBytes());
	s.write(st.data.getBytes());
	s.write(("</html>").getBytes());
	s.close();
    }
}
