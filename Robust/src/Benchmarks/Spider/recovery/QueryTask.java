public class QueryTask extends Task {
	int maxDepth;
	Queue toprocess;
	DistributedHashMap results;
	GlobalString gTitle;
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
			String title;

			atomic {
				gq = (GlobalQuery)myWork;
				hostname = new String(GlobalString.toLocalCharArray(gq.getHostName()));
				path = new String(GlobalString.toLocalCharArray(gq.getPath()));

				GlobalStringBuffer gsb = global new GlobalStringBuffer(hostname);
				gsb.append("/");
				gsb.append(path);
				workingURL = global new GlobalString(gsb.toGlobalString());
				gTitle = null;
			}
			lq = new LocalQuery(hostname, path, depth);

			System.printString("["+lq.getDepth()+"] ");
			System.printString("Processing - Hostname : ");
			System.printString(hostname);
			System.printString(", Path : ");
			System.printString(path);
			System.printString("\n");

			Socket s = new Socket(hostname, 80);
    
			requestQuery(hostname, path, s);
			readResponse(lq, s);

			if ((title = grabTitle(lq)) != null) {
				atomic {
					gTitle = global new GlobalString(title);
				}
			}

			atomic {
				toprocess = processPage(lq);
			}

			s.close();
		}
  }

	public void done(Object obj) {
		if (gTitle != null) 
			processList();

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

	public static String grabTitle(LocalQuery lq) {
		String sTitle = new String("<title>");	
		String eTitle = new String("</title>");
  	String searchstr = lq.response.toString();
		String title = null;

		int mindex = searchstr.indexOf(sTitle);
		if (mindex != -1) {
			int endquote = searchstr.indexOf(eTitle, mindex+sTitle.length());
			title = new String(searchstr.subString(mindex+sTitle.length(), endquote));
		}

		return title;
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

	public void processList() {
		LinkedList ll;
		GlobalString token = null;
		int mindex = 0;
		int endquote = 0;

		while (endquote != -1) {
			endquote = gTitle.indexOf(' ', mindex);

			if (endquote != -1) {
				token = gTitle.subString(mindex, endquote);
				mindex = endquote + 1;
				if (censor(token)) {
					continue;
				}
				token = refinement(token);
			}
			else {
				token = gTitle.subString(mindex);
				token = refinement(token);
			}

/*
			Queue q;
			if ((q = (Queue)(results.remove(token))) == null) {
				q = global new Queue();
			}
			else {
				q = (Queue)(results.get(token));
			}
			// bug here <- object id changed?? 
			q.push(workingURL);	
			results.put(token, q);
			
			System.out.println("Key : ["+token.toLocalString()+"],["+q.size()+"]");
			*/
		}
	}

	public boolean censor(GlobalString str) {
		if (str.equals("of"))	return true;
		else if (str.equals("for")) return true;
		else if (str.equals("a")) return true;
		else if (str.equals("an")) return true;
		else if (str.equals("the")) return true;
		else if (str.equals("at")) return true;
		else if (str.equals("and")) return true;
		else if (str.equals("or")) return true;
		else if (str.equals("but")) return true;
		else if (str.equals(".")) return true;
		else if (str.equals("=")) return true;
		else if (str.equals("-")) return true;
		else if (str.equals(":")) return true;
		else if (str.equals(";")) return true;
		else if (str.equals("\'")) return true;
		else if (str.equals("\"")) return true;
		else if (str.equals("@")) return true;
		else return false;
	}

	public GlobalString refinement(GlobalString str) {
		if (str.charAt(str.length()-1) == ',') {
			return str.subString(0, str.length()-1);
		}
		else if (str.charAt(str.length()-1) == ':') {
			return str.subString(0, str.length()-1);
		}
		else if (str.charAt(str.length()-1) == 's') {
			if (str.charAt(str.length()-2) == '\'')
				return str.subString(0, str.length()-2);	
		}
		return str;
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
