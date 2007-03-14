public class QueryThread extends Thread {
    QueryQueue toprocess;
    QueryList ql;
    public QueryThread(QueryQueue qq, QueryList ql) {
	toprocess=qq;
	this.ql=ql;
    }

    public void run() {
	while(true) {
	    Query q=null;
	    while(q==null) {
		q=toprocess.getQuery();
		if (q==null)
		    Thread.sleep(2);
	    }
	    String hostname=q.getHostName();
	    Socket s=new Socket(hostname, 80);
	    requestQuery(q, s);
	    readResponse(q, s);
	    processPage(q, ql);
	    s.close();
	}
    }

    void requestQuery(Query q, Socket sock) {
	StringBuffer req=new StringBuffer("GET "); 
	req.append("/");
	req.append(q.getPath());
	req.append(" HTTP/1.1\r\nHost:");
	req.append(q.getHostName());
	req.append("\r\n\r\n");
	sock.write(req.toString().getBytes());
    }

    void readResponse(Query q, Socket sock) {
	//    state 0 - nothing
	//    state 1 - \r
	//    state 2 - \r\n
	//    state 3 - \r\n\r
	//    state 4 - \r\n\r\n
	int state=0;
	while(true) {
	    if (state<4) {
		if (state==0) {
		    byte[] b=new byte[1];
		    int numchars=sock.read(b);
		    if ((numchars==1)) {
			if (b[0]=='\r') {
			    state++;
			    System.printString(new String(b));
			}
		    } else
			return;
		} else if (state==1) {
		    byte[] b=new byte[1];
		    int numchars=sock.read(b);
		    if (numchars==1) {
			if (b[0]=='\n')
			    state++;
			else
			    state=0;
			System.printString(new String(b));
		    } else return;
		} else if (state==2) {
		    byte[] b=new byte[1];
		    int numchars=sock.read(b);
		    if (numchars==1) {
			if (b[0]=='\r')
			    state++;
			else
			    state=0;
			System.printString(new String(b));
		    } else return;
		} else if (state==3) {
		    byte[] b=new byte[1];
		    int numchars=sock.read(b);
		    if (numchars==1) {
			if (b[0]=='\n')
			    state++;
			else
			    state=0;
			System.printString(new String(b));
		    } else return;
		}
	    } else {
		byte[] buffer=new byte[1024];
		int numchars=sock.read(buffer);
		if (numchars==0)
		    return;
		else {
		    String curr=(new String(buffer)).subString(0,numchars);
		    System.printString(curr);
		    q.response.append(curr);
		}
	    }
	}
    }

    void processPage(Query q, QueryList ql) {
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
			Query newq=new Query(q.getHostName(match), q.getPathName(match));
			toprocess.addQuery(newq);
		    }
		    index=endquote;
		} else cont=false;
	    } else cont=false;
	}
    }

}
