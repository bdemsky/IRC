task Startup(StartupObject s {initialstate}) {
    String firstmachine=s.parameters[0];
    String firstpage=s.parameters[1];
    QueryList ql=new QueryList() {initialized};
    Query firstquery=new Query(firstmachine, firstpage){};
    taskexit(s{!initialstate});
}

task requestQuery(Query q{!requested}) {
    String hostname=q.getHostName();
    q.makeConnection();
    StringBuffer req=new StringBuffer("GET "); 
    req.append("/");
    req.append(q.getPath());
    req.append(" HTTP/1.1\r\nHost:");
    req.append(q.getHostName());
    req.append("\r\n\r\n");
    q.write(req.toString().getBytes());
    taskexit(q{requested});
}

task readResponse(Query q{requested && ! received && IOPending}) {
    //    state 0 - nothing
    //    state 1 - \r
    //    state 2 - \r\n
    //    state 3 - \r\n\r
    //    state 4 - \r\n\r\n
    if (q.state<4) {
	if (q.state==0) {
	    byte[] b=new byte[1];
	    int numchars=q.read(b);
	    if ((numchars==1) && (b[0]=='\r'))
		q.state++;
	    System.printString(new String(b));
	} else if (q.state==1) {
	    byte[] b=new byte[1];
	    int numchars=q.read(b);
	    if (numchars==1) {
		if (b[0]=='\n')
		    q.state++;
		else
		    q.state=0;
		System.printString(new String(b));
	    }
	} else if (q.state==2) {
	    byte[] b=new byte[1];
	    int numchars=q.read(b);
	    if (numchars==1) {
		if (b[0]=='\r')
		    q.state++;
		else
		    q.state=0;
		System.printString(new String(b));
	    }
	} else if (q.state==3) {
	    byte[] b=new byte[1];
	    int numchars=q.read(b);
	    if (numchars==1) {
		if (b[0]=='\n')
		    q.state++;
		else
		    q.state=0;
		System.printString(new String(b));
	    }
	}
    } else {
	byte[] buffer=new byte[1024];
	int numchars=q.read(buffer);
	if (numchars==0)
	    taskexit(q{received});
	else {
	    String curr=(new String(buffer)).subString(0,numchars);
	    System.printString(curr);
	    q.response.append(curr);
	}
    }
}

task processPage(Query q{received&&!processed}, QueryList ql{initialized}) {
    int index=0;
    String href=new String("href=\"");
    String searchstr=q.response.toString();
    boolean cont=true;
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
		Query newq=new Query(q.getHostName(match), q.getPathName(match)){};
	    }
	    index=endquote;
	} else cont=false;
	} else cont=false;
    }
    taskexit(q{processed});
}
