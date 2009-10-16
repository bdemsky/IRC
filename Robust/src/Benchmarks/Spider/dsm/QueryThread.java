public class QueryThread extends Thread {
	int maxDepth;
	int maxSearchDepth;
  int MY_MID;
  int NUM_THREADS;
  Queue todoList;
  Queue doneList;
  Query myWork;
  Query[] currentWorkList;

  public QueryThread(Queue todoList, Queue doneList, int maxDepth, int maxSearchDepth,int mid,int NUM_THREADS,Query[] currentWorkList) {    
    this.todoList = todoList;
		this.doneList = doneList;
		this.maxDepth = maxDepth;
		this.maxSearchDepth = maxSearchDepth;
    this.currentWorkList = currentWorkList;
    this.MY_MID = mid;
    this.NUM_THREADS = NUM_THREADS;
  }

  public void run()
  {
    int workMID;

    atomic {
      workMID = MY_MID;
    }

    System.out.println("Thread " + workMID + " has started");

    int chk;

    while(true) {
      atomic {
        myWork = (Query)todoList.pop();
        
        if(null == myWork)  // no work in todolist
        {
          chk = checkCurrentWorkList(this);        
        }
        else {
          currentWorkList[workMID] = myWork;
          chk = 1;
        }
      }

      if(chk == 1) { // it has query
        execute(this);

        atomic {
          doneWork(myWork);
          currentWorkList[workMID] = null;
        }
      }
      else if(chk == -1) { // finished all work
        break;
      }
      else {  // wait for other thread
        sleep(5000000);
      }

    }

   atomic {
      System.out.println("\n\nDoneSize = " + doneList.size());
    }

    System.out.println("\n\n\n I'm done");
  }

	public static int checkCurrentWorkList(QueryThread qt) {		
    int i;
    int myID;
		int num_threads; 
    boolean chk = false;
    Object s;

		atomic {
	    myID = qt.MY_MID;
			num_threads = qt.NUM_THREADS;

      for(i = 0 ; (i < num_threads); i++) {
        if(myID == i) {
          continue;
        }  

        s = qt.currentWorkList[i];

        if(null != s) {
          chk = true;
          break;
        }
      }
			
    }

    if(chk == false)  // wait for other machine's work
      return -1;
    else
      return 0; // others are still working wait until they finish work
  }

  public static void execute(QueryThread qt) {
		int depth;
    int max;
    int maxSearch;

    atomic {
      if(qt.myWork == null) {
        System.out.println("What!!!!!!!!!!!!!!!");
        System.exit(0);
      }
			depth = ((Query)qt.myWork).getDepth();
      max = qt.maxDepth;
      maxSearch = qt.maxSearchDepth;
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
				q = (Query)(qt.myWork);
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
					qt.todoList.push(q);
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
	
	public void doneWork(Object obj) {
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
