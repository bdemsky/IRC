public class QueryTask extends Task {
	int maxDepth;
	Queue toprocess;
	DistributedHashMap results;
	GlobalString workingURL;

  public QueryTask(Queue todoList, DistributedHashMap doneList, int maxDepth, DistributedHashMap results) {
    this.todoList = todoList;
		this.doneList = doneList;
		this.maxDepth = maxDepth;
		this.results = results;
  }

  public void execute() {
		int depth;
		int max;
		
		atomic {
			depth = ((GlobalQuery)myWork).getDepth();
      max = this.maxDepth;
		}

		if (depth < max) {
			/* global variables */
			GlobalQuery gq;

			/* local variables */
			LocalQuery lq;
			String hostname;
			String path;

			atomic {
				gq = (GlobalQuery)myWork;
				hostname = new String(GlobalString.toLocalCharArray(gq.getHostName()));
				path = new String(GlobalString.toLocalCharArray(gq.getPath()));

				GlobalStringBuffer gsb = global new GlobalStringBuffer(hostname);
				gsb.append("/");
				gsb.append(path);
				workingURL = global new GlobalString(gsb.toGlobalString());
			}
			lq = new LocalQuery(hostname, path, depth);

			System.printString(lq.getDepth()+" ");
			System.printString("Processing - Hostname : ");
			System.printString(hostname);
			System.printString(", Path : ");
			System.printString(path);
			System.printString("\n");

			Socket s = new Socket(hostname, 80);
    
			requestQuery(hostname, path, s);
			readResponse(lq, s);

			atomic {
				processList(lq, workingURL, results);
			}

			atomic {
				toprocess = processPage(lq);
			}

			s.close();
		}
  }

	public void done(Object obj) {
		GlobalString str = global new GlobalString("true");
		doneList.put(workingURL, str);

		while(!toprocess.isEmpty()) {
			GlobalQuery q = (GlobalQuery)toprocess.pop();

			GlobalString hostname = global new GlobalString(q.getHostName());
			GlobalString path = global new GlobalString(q.getPath());

			GlobalStringBuffer gsb = global new GlobalStringBuffer(hostname);
			gsb.append("/");
			gsb.append(path);

			if (!doneList.containsKey(gsb.toGlobalString())) {
				todoList.push(q);
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

	public static void processList(LocalQuery lq, GlobalString url, DistributedHashMap results) {
		String sTitle = new String("<title>");	
		String eTitle = new String("</title>");
		String searchstr = lq.response.toString();
		LinkedList ll;

		int sIndex = searchstr.indexOf(sTitle);
		if (sIndex != -1) {
			int eIndex = searchstr.indexOf(eTitle, sIndex+sTitle.length());
			String title = new String(searchstr.subString(sIndex+sTitle.length(), eIndex));
			ll = tokenize(title);

			Queue q;
			while (!ll.isEmpty()) {
				GlobalString word = global new GlobalString(ll.pop().toString());
//				q = (Queue)(results.get(word));

//				if (q == null) {
				if (!results.containsKey(word)) {
					q = global new Queue();
				}
				else {
					q = (Queue)(results.get(word));
				}
				q.push(url);
				results.put(word, q);

				System.out.println("Key : ["+word.toLocalString()+"],["+q.size()+"]");
/*
				for (int i = 0; i < q.size(); i++) {
					Object obj = q.elements[i];
					GlobalString str = global new GlobalString((GlobalString)obj);
					System.out.println("\t["+i+"] : "+str.toLocalString());
				}*/
			}
		}
	}

	public static LinkedList tokenize(String str) {
		LinkedList ll;
		int sIndex = 0;
		int eIndex = 0;
		String token;

		ll = new LinkedList();
		
		// and, or, of, at, but, '.', ',', ':' ';', '"', ' ', '-', '='
		while (true) {
			eIndex = str.indexOf(' ', sIndex);
			if (eIndex == -1) {
				token = str.subString(sIndex);
				ll.add(token);
				break;
			}
			else {
				token = str.subString(sIndex, eIndex);
				ll.add(token);
				sIndex = eIndex+1;
			}
		}
		
		return ll;
	}
	
  public static Queue processPage(LocalQuery lq) {
    int index = 0;
  	String href = new String("href=\"");
  	String searchstr = lq.response.toString();
		int depth;
  	boolean cont = true;
		Queue toprocess;

		depth = lq.getDepth() + 1;

		toprocess = global new Queue();

		while(cont) {
			int mindex = searchstr.indexOf(href,index);
			if (mindex != -1) {	
				int endquote = searchstr.indexOf('"', mindex+href.length());
     		if (endquote != -1) {
		      String match = searchstr.subString(mindex+href.length(), endquote);
					String match2 = lq.makewebcanonical(match);
	
					GlobalString ghostname;
					GlobalString gpath;

					ghostname = global new GlobalString(lq.getHostName(match));
					gpath = global new GlobalString(lq.getPathName(match));

		      if (match2 != null) {
							GlobalQuery gq = global new GlobalQuery(ghostname, gpath, depth);
							toprocess.push(gq);
					}
					index = endquote;
        } else cont = false;
      } else cont = false;
    }
		return toprocess;
  }
}
