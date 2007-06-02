task Startup(StartupObject s {initialstate}) {
    String firstmachine=s.parameters[0];
    String firstpage=s.parameters[1];
    QueryList ql=new QueryList() {initialized};
    tag t=new tag(connect);
    Socket sock=new Socket(){}{t};
    Query firstquery=new Query(firstmachine, firstpage){}{t};
    taskexit(s{!initialstate});
}

task requestQuery(Query q{!requested}{connect t}, Socket s{}{connect t}) {
    String hostname=q.getHostName();
    q.makeConnection(s);
    StringBuffer req=new StringBuffer("GET "); 
    req.append("/");
    req.append(q.getPath());
    req.append(" HTTP/1.1\r\nHost:");
    req.append(q.getHostName());
    req.append("\r\n\r\n");
    s.write(req.toString().getBytes());
    taskexit(q{requested});
}

task readResponse(Query q{requested && ! received}{connect t},Socket s{IOPending}{connect t}) {
    //    state 0 - nothing
    //    state 1 - \r
    //    state 2 - \r\n
    //    state 3 - \r\n\r
    //    state 4 - \r\n\r\n
    if (q.state<4) {
	if (q.state==0) {
	    byte[] b=new byte[1];
	    int numchars=s.read(b);
	    if ((numchars==1) && (b[0]=='\r'))
		q.state++;
	} else if (q.state==1) {
	    byte[] b=new byte[1];
	    int numchars=s.read(b);
	    if (numchars==1) {
		if (b[0]=='\n')
		    q.state++;
		else
		    q.state=0;
	    }
	} else if (q.state==2) {
	    byte[] b=new byte[1];
	    int numchars=s.read(b);
	    if (numchars==1) {
		if (b[0]=='\r')
		    q.state++;
		else
		    q.state=0;
	    }
	} else if (q.state==3) {
	    byte[] b=new byte[1];
	    int numchars=s.read(b);
	    if (numchars==1) {
		if (b[0]=='\n')
		    q.state++;
		else
		    q.state=0;
	    }
	}
    } else {
	byte[] buffer=new byte[1024];
	int numchars=s.read(buffer);
	if (numchars==0) {
	    s.close();
	    taskexit(q{received});
	} else {
	    String curr=(new String(buffer)).subString(0,numchars);
	    q.response.append(curr);
	}
    }
}

task processPage(Query q{received&&!processed}, QueryList ql{initialized}) {
    int index=0;
    String href=new String("href=\"");
    String searchstr=q.response.toString();
    boolean cont=true;
    q.outputFile();

    while(cont) {
	int mindex=searchstr.indexOf(href,index);
	if (mindex!=-1) {

	int endquote=searchstr.indexOf('"', mindex+href.length());
	if (endquote!=-1) {
	    String match=searchstr.subString(mindex+href.length(), endquote);
	    String match2=q.makewebcanonical(match);
	    if (match2!=null&&!ql.checkQuery(match2)) {
		ql.addQuery(match2);
		System.printString(q.getHostName(match));
		System.printString("        ");
		System.printString(q.getPathName(match));
		System.printString("\n");
		tag t=new tag(connect);
		Socket s=new Socket(){}{t};
		Query newq=new Query(q.getHostName(match), q.getPathName(match)){}{t};
	    }
	    index=endquote;
	} else cont=false;
	} else cont=false;
    }
    taskexit(q{processed});
}
