task start(StartupObject s{initialstate}) {
    Jhttpp2Server js=new Jhttpp2Server(true);
    taskexit(s{!initialstate});
}

task acceptconnection(ServerSocket ss{SocketPending}) {
    tag t=new tag(connection);
    MySocket ms=new MySocket(){}{t};
    ss.accept(ms);
    Jhttpp2HTTPSession js=new Jhttpp2HTTPSession() {first}{t};
    Jhttpp2ClientInputStream jcis=new Jhttpp2ClientInputStream() {}{t};
}

task requestfirst(Jhttpp2HTTPSession session {first}{connection tc}, Jhttpp2ClientInputStream jcis {}{connection tc}, MySocket socket {IOPending}{connection tc}) {
    byte[] buf=new byte[10000];
    int length=socket.read(buf);
    String str=new String(buf, 0, length);
    if (session.request!=null) {
	str=session.request.concat(str);
    }
    tag ct=new tag(chained);
    Request r=jcis.readfirst(str, session, ct, tc);
    if (r==null) {
	session.request=str;
	taskexit;
    } else {
	session.request=str.substring(r.length(), str.length());
	if (session.request.length()>0)
	    taskexit(session{more, !first});
	else
	    taskexit(session{!first});
    }
}

task request(Jhttpp2HTTPSession session {!first}{connection tc}, Jhttpp2ClientInputStream jcis {}{connection tc}, MySocket socket {IOPending}{connection tc}, optional Request rold{!chained}{connection tc}) {
    byte[] buf=new byte[10000];
    int length=socket.read(buf);
    if (session.request!=null)
	System.printString(session.request+"\n");
    if (length==0) {
	socket.close();
	taskexit;
    } else if (length < 0 ) {
	System.printString("ERROR\n");
	taskexit;
    }
    String str=new String(buf, 0, length);

    if (session.request!=null) {
	str=session.request.concat(str);
    }
    //pull off the next request
    tag ct=new tag(chained);
    Request r=jcis.read(str, session, ct, tc);
    if (r==null) {
	session.request=str;
	taskexit;
    } else {
	session.request=str.substring(r.length(), str.length());
	if (session.request.length()>0)
	    taskexit(session{more}, rold{chained}{ct});
	else
	    taskexit(rold{chained}{ct});
    }
}

task requestmore(Jhttpp2HTTPSession session {more}{connection tc}, Jhttpp2ClientInputStream jcis {}{connection tc}, optional Request rold{!chained}{connection tc}) {
    String str=session.request;
    //pull off the next request
    tag ct=new tag(chained);
    Request r=jcis.read(str, session, ct, tc);
    if (r==null) {
	session.request=str;
	taskexit;
    } else {
	session.request=str.substring(r.length(), str.length());
	if (session.request.length()>0)
	    taskexit(rold{chained}{ct});
	else
	    taskexit(session{!more}, rold{chained}{ct});
    }
}

task sendfirst(Request r{first&&!processed}{connection tc}) {
    r.parseRequest();
    r.connect();
    r.sendRequest();
    taskexit(r{processed});
}

task sendnext(optional Request rprev{received&&!done}{chained ct}, Request r{!processed&&!first}{chained ct}) {
    r.parseRequest();
    if (isavailable(rprev)&&rprev.remote_host_name!=null&&
	rprev.remote_host_name.equals(r.remote_host_name)&&rprev.port==r.port) {
	r.fd=rprev.fd;
	r.nativeBindFD(r.fd);
    } else
	r.connect();
    r.sendRequest();
    taskexit(r{processed}, rprev{done});
}

task recvreq(Request r{processed&&!received&&IOPending}) {
    byte[] buf=new byte[10000];
    int length=r.read(buf);
    if (length==0) {
	//Done
	taskexit(r{received});
    } else if (length<0) {
	r.close();
	taskexit(r{received});
    }
    String str=new String(buf, 0, length);
    if (r.response!=null) {
	str=r.response.concat(str);
    }
    boolean cnt=true;
    int lastindex=0;
    int bytes=0;
    while(cnt) {
	int nextindex=str.indexOf('\n',lastindex)+1;
	if (nextindex==-1) {
	    r.response=str;
	    taskexit;
	}
	if (nextindex-lastindex<=2) {
	    cnt=false;
	} else if (str.substring(lastindex, nextindex+1).toUpperCase().startsWith("CONTENT-LENGTH")) {
	    String clen=str.substring(lastindex+16, nextindex+1);
	    if (clen.indexOf("\r")!=-1) 
		clen=clen.substring(0,clen.indexOf("\r"));
	    else 
		if(clen.indexOf("\n")!=-1) 
		    clen=clen.substring(0,clen.indexOf("\n"));
	    bytes=Integer.parseInt(clen);
	}
	lastindex=nextindex;
    }
    if (bytes>0) {
	if ((lastindex+bytes)<=str.length()) {
	    r.response=str;
	    taskexit(r{received});
	} else {
	    r.response=str;
	    taskexit;
	}
    } else {
	//read until 0
	r.response=str;
	taskexit;
    }
}

task sendfirstresp(optional Request r{first && !sent && received}{connection tc}, MySocket sock{}{connection tc}) {
    if (isavailable(r)&&r.response!=null) {
	sock.write(r.response.getBytes());
	taskexit(r{sent});
    } else {
	String msg="<HTML><HEAD><TITLE> 503 Service Unavailable </TITLE></HEAD>\r<BODY>Request Failed</BODY></HTML>\r\n";
	String resp="HTTP/1.1 503 Service Unavailable\r\n"+
	    "Cache-Control: nocache, must-revalidate\r\n"+
	    "Connection: close\r\n"+
	    "Content-Length: "+msg.length()+"\r\n"+
	    "Content-Type: text/html; charset=iso-8859-1\r\n\r\n"+msg;
	sock.write(resp.getBytes());
	taskexit(r{sent});
    }
}

task sendresp(optional Request rprev {sent}{chained ct}, optional Request r{!sent && received && !first}{chained ct, connection tc}, MySocket sock{}{connection tc}) {
    if (isavailable(r)&&r.response!=null) {
	sock.write(r.response.getBytes());
	taskexit(r{sent});
    } else {
	String msg="<HTML><HEAD><TITLE> 503 Service Unavailable </TITLE></HEAD>\r<BODY>Request Failed</BODY></HTML>\r\n";
	String resp="HTTP/1.1 503 Service Unavailable\r\n"+
	    "Cache-Control: nocache, must-revalidate\r\n"+
	    "Connection: close\r\n"+
	    "Content-Length: "+msg.length()+"\r\n"+
	    "Content-Type: text/html; charset=iso-8859-1\r\n\r\n"+msg;
	sock.write(resp.getBytes());
	taskexit(r{sent});
    }
}
