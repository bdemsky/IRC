public class QueryThread extends Task {
	int maxDepth;
	int depthCnt;
	int maxSearchDepth;
	int searchDepthCnt;

  public QueryThread(Queue qq, Queue ql, int depth, int searchDepth) {
    this.todoList = qq;
		this.doneList = ql;
		this.maxDepth = depth;
		this.maxSearchDepth = searchDepth;
		depthCnt = 1;
		searchDepthCnt = 0;
  }

  public void execute(Object mywork) {
		Query q = (Query)mywork;
		GlobalString ghostname;
		GlobalString gpath;

		atomic {
			ghostname = q.getHostName();
			gpath = q.getPath();
		}

		String hostname = new String(GlobalString.toLocalCharArray(ghostname));
		String path = new String(GlobalString.toLocalCharArray(gpath));

		System.printString("Processing ");
		System.printString(hostname + "\n");
		System.printString(" ");
		System.printString(path);
		System.printString("\n");

		Socket s = new Socket(hostname, 80);

		requestQuery(hostname, path, s);
//		System.printString("Wait for 5 secs\n");
//		Thread.sleep(2000000);

		readResponse(q, s);
//		System.printString("Wait for 5 secs\n");
//		Thread.sleep(2000000);

		q.outputFile();
//		System.printString("Wait for 5 secs\n");
//		Thread.sleep(2000000);

		processPage(q, (QueryList)doneList);
		s.close();
  }
	
	public void requestQuery(String hostname, String path, Socket sock) {
    StringBuffer req = new StringBuffer("GET "); 
    req.append("/");
		req.append(path);
    req.append(" HTTP/1.1\r\nHost:");
    req.append(hostname);
    req.append("\r\n\r\n");
		System.printString("req : " + req + "\n");
    sock.write(req.toString().getBytes());
  }

	public void readResponse(Query q, Socket sock) {
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
          } else return;
        } else if (state==2) {
          byte[] b=new byte[1];
          int numchars=sock.read(b);
          if (numchars==1) {
            if (b[0]=='\r')
              state++;
            else
              state=0;
          } else return;
        } else if (state==3) {
          byte[] b=new byte[1];
          int numchars=sock.read(b);
          if (numchars==1) {
            if (b[0]=='\n')
              state++;
            else
              state=0;
          } else return;
        }
      } else {
				byte[] buffer=new byte[1024];
        int numchars=sock.read(buffer);
        if (numchars==0)
          return;
        else {
          String curr=(new String(buffer)).subString(0,numchars);
					q.response.append(curr);
        }
      }
    }
  }
	
	public void done(Object obj) {
		doneList.push(obj);
//		System.printString("Size of todoList : " + todoList.size() + "\n");
//		Thread.sleep(5000000);
	}

  public void processPage(Query q, QueryList doneList) {
    int index = 0;
  	String href = new String("href=\"");
  	String searchstr = q.response.toLocalString();
  	boolean cont = true;

		while(cont && (searchDepthCnt < maxSearchDepth)) {
			int mindex = searchstr.indexOf(href,index);
			if (mindex != -1) {	
				int endquote = searchstr.indexOf('"', mindex+href.length());
     		if (endquote != -1) {
		      String match = searchstr.subString(mindex+href.length(), endquote);
					GlobalString gmatch;
					GlobalString gmatch2;

					atomic {
						gmatch = global new GlobalString(match);
						gmatch2 = q.makewebcanonical(gmatch);
					}
		      if (gmatch2 != null && !doneList.checkQuery(gmatch2)) {
//						doneList.push(gmatch2);
						done(gmatch2);
						if (depthCnt < maxDepth) {
							Query newq;
							System.printString("Depth : " + depthCnt + "\n");
							atomic {
								newq = global new Query(q.getHostName(gmatch), q.getPathName(gmatch));
								todoList.push(newq);
								System.printString("Size of todoList : " + todoList.size() + "\n");
								searchDepthCnt++;
							}
						}
					}
		      index = endquote;
        } else cont = false;
      } else cont = false;
    }
		depthCnt++;
		searchDepthCnt = 0;
  }
}
