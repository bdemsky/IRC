public class QueryThread extends Task {
	int maxDepth;
	int maxSearchDepth;

  public QueryThread(Queue todoList, Queue doneList, int maxDepth, int maxSearchDepth) {
    this.todoList = todoList;
		this.doneList = doneList;
		this.maxDepth = maxDepth;
		this.maxSearchDepth = maxSearchDepth;
  }

  public void execute() {
		int depth;
    int max;
    int maxSearch;
		
		atomic {
			depth = ((Query)myWork).getDepth();
      max = this.maxDepth;
      maxSearch = this.maxSearchDepth;
		}

		if (depth < max) {
			/* global variables */
			Query q;
			GlobalString ghostname;
			GlobalString gpath;

			/* local variables */
			QueryQueue toprocess;
			LocalQuery lq;
			String hostname;
			String path;

			atomic {
				q = (Query)myWork;
				ghostname = q.getHostName();
				gpath = q.getPath();
				hostname = new String(GlobalString.toLocalCharArray(ghostname));
				path = new String(GlobalString.toLocalCharArray(gpath));
			}
			lq = new LocalQuery(hostname, path, depth);

			System.printString("Processing - Hostname : ");
			System.printString(hostname);
			System.printString(", Path : ");
			System.printString(path);
			System.printString("\n");

			Socket s = new Socket(hostname, 80);
    
			requestQuery(hostname, path, s);
			readResponse(lq, s);
			toprocess = processPage(lq,maxSearch);
			s.close();

			atomic {
				while(!toprocess.isEmpty()) {
					lq = toprocess.pop();
					ghostname = global new GlobalString(lq.getHostName());
					gpath = global new GlobalString(lq.getPath());

					q = global new Query(ghostname, gpath, lq.getDepth());
					todoList.push(q);
				}
			}
		}
  }
	
	public static void requestQuery(String hostname, String path, Socket sock) {
    StringBuffer req = new StringBuffer("GET "); 
    req.append("/");
		req.append(path);
    req.append(" HTTP/1.1\r\nHost:");
    req.append(hostname);
    req.append("\r\n\r\n");
    sock.write(req.toString().getBytes());
  }

	public static void readResponse(LocalQuery lq, Socket sock) {
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
					lq.response.append(curr);
        }
      }
    }
  }
	
	public void done(Object obj) {
		doneList.push(obj);
	}

  public static QueryQueue processPage(LocalQuery lq,int maxSearchDepth) {
    int index = 0;
  	String href = new String("href=\"");
  	String searchstr = lq.response.toString();
		int depth;
  	boolean cont = true;

		QueryQueue toprocess = new QueryQueue();
		depth = lq.getDepth() + 1;

		int searchDepthCnt = 0;
		while(cont && (searchDepthCnt < maxSearchDepth)) {
			int mindex = searchstr.indexOf(href,index);
			if (mindex != -1) {	
				int endquote = searchstr.indexOf('"', mindex+href.length());
     		if (endquote != -1) {
		      String match = searchstr.subString(mindex+href.length(), endquote);
					String match2 = lq.makewebcanonical(match);
	
		      if (match2 != null) {
						LocalQuery newlq = new LocalQuery(lq.getHostName(match), lq.getPathName(match), depth);

						toprocess.push(newlq);
						searchDepthCnt++;
					}
					index = endquote;
        } else cont = false;
      } else cont = false;
    }

		return toprocess;
  }
}
