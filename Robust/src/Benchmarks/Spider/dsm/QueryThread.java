public class QueryThread extends Thread {
  int MY_MID;
  int NUM_THREADS;
  Queue todoList;
  DistributedHashMap doneList;
  GlobalQuery myWork;
  GlobalQuery[] currentWorkList;

  DistributedHashMap results;
  Queue toprocess;
  GlobalString gTitle;
  GlobalString workingURL;
	int maxDepth;

  public QueryThread(Queue todoList, DistributedHashMap doneList, DistributedHashMap results,int maxDepth,int mid,int NUM_THREADS,GlobalQuery[] currentWorkList) {    
    this.todoList = todoList;
		this.doneList = doneList;
    this.currentWorkList = currentWorkList;
    this.MY_MID = mid;
    this.NUM_THREADS = NUM_THREADS;

		this.maxDepth = maxDepth;
    this.results = results;
    toprocess = global new Queue();
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
        myWork = (GlobalQuery)todoList.pop();
        
        if(myWork == null)  // no work in todolist
        {
          chk = checkCurrentWorkList(this);        
        }
        else {
          currentWorkList[workMID] = myWork;
          chk = 1;
        }
      }

      if(chk == 1) { // it has query
        atomic {
          if(myWork == null) 
            System.out.println("myWork is null");
        }
        QueryThread.execute(this);

        atomic {
          done(myWork);
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
		
		atomic {
			depth = qt.myWork.getDepth();
      max = qt.maxDepth;
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
				gq = qt.myWork;
				hostname = new String(GlobalString.toLocalCharArray(gq.getHostName()));
				path = new String(GlobalString.toLocalCharArray(gq.getPath()));

				GlobalStringBuffer gsb = global new GlobalStringBuffer(hostname);
				gsb.append("/");
				gsb.append(path);
				qt.workingURL = global new GlobalString(gsb.toGlobalString());
				qt.gTitle = null;
			}
			lq = new LocalQuery(hostname, path, depth);

			System.printString("["+lq.getDepth()+"] ");
			System.printString("Processing - Hostname : ");
			System.printString(hostname);
			System.printString(", Path : ");
			System.printString(path);
			System.printString("\n");

      // check if the url is pdf, ps, ppt, pptx ... etc
      if(isDocument(path)) {
        return;
      }

			Socket s = new Socket();

      // connection fail
      if(s.connect(hostname, 80) == -1) {
        return;
      }
    
			requestQuery(hostname, path, s);
			readResponse(lq, s);

			if ((title = grabTitle(lq)) != null) {
				atomic {
					qt.gTitle = global new GlobalString(title);
				}
  			atomic {
	  			qt.toprocess = processPage(lq);
		  	}
      }
			s.close();
		}
  }

  public static boolean isDocument(String str) {
		int index = str.lastindexOf('.');

		if (index != -1) {
			if ((str.subString(index+1)).equals("pdf")) return true;
			else if ((str.subString(index+1)).equals("ps")) return true;
			else if ((str.subString(index+1)).equals("ppt")) return true;
			else if ((str.subString(index+1)).equals("pptx")) return true;
			else if ((str.subString(index+1)).equals("jpg")) return true;
			else if ((str.subString(index+1)).equals("mp3")) return true;
			else if ((str.subString(index+1)).equals("wmv")) return true;
			else if ((str.subString(index+1)).equals("doc")) return true;
			else if ((str.subString(index+1)).equals("docx")) return true;
			else if ((str.subString(index+1)).equals("mov")) return true;
			else if ((str.subString(index+1)).equals("flv")) return true;
			else return false;
		}
		return false;
	}

	public void done(Object obj) {
		if (gTitle != null && (gTitle.length() > 0)) 
			processList();


		while(!toprocess.isEmpty()) {
			GlobalQuery q = (GlobalQuery)toprocess.pop();

			GlobalString hostname = global new GlobalString(q.getHostName());
			GlobalString path = global new GlobalString(q.getPath());

			GlobalStringBuffer gsb = global new GlobalStringBuffer(hostname);
			gsb.append("/");
			gsb.append(path);

			if (!doneList.containsKey(gsb.toGlobalString())) {
        todoList.push(q);

        GlobalString str = global new GlobalString("1");
    		doneList.put(gsb.toGlobalString(), str);
			}
		}
	}

	public static String grabTitle(LocalQuery lq) {
		String sBrace = new String("<");	
		String strTitle = new String("title>");
  	String searchstr = lq.response.toString();
		String title = null;
		char ch;

		int mindex = -1;
		int endquote = -1;
		int i, j;
		String tmp;

		for (i = 0; i < searchstr.length(); i++) {
			if (searchstr.charAt(i) == '<') {                                   	
				i++;                                                                
				if (searchstr.length() > (i+strTitle.length())) {                   
					tmp = searchstr.subString(i, i+strTitle.length());                
					if (tmp.equalsIgnoreCase("title>")) {                             
						mindex = i + tmp.length();                                      
						for (j = mindex; j < searchstr.length(); j++) {                 
							if (searchstr.charAt(j) == '<') {                             
								j++;                                                        
								tmp = searchstr.subString(j, j+strTitle.length()+1);			  
								if (tmp.equalsIgnoreCase("/title>")) {                      
									endquote = j - 1;                                         
									break;                                                    
								}                                                           
							}                                                             
						}                                                               
					}                                                                 
				}                                                                   
			}                                                                     
		}                                                                       
                                                                            
		if (mindex != -1) {                                                     
			title = searchstr.subString(mindex, endquote);                        
			if (Character.isWhitespace(title.charAt(0))){                         
				mindex=0;                                                           
				while (Character.isWhitespace(title.charAt(mindex++)));             
				mindex--;                                                           
				if (mindex >= title.length()) return null;                          
				title = new String(title.subString(mindex));                        
			}                                                                     
                                                                            
			if (Character.isWhitespace(title.charAt(title.length()-1))) {         
				endquote=title.length()-1;                                          
				while (Character.isWhitespace(title.charAt(endquote--)));           
				endquote += 2;                                                      
				if (mindex >= endquote) return null;                                
				title = new String(title.subString(0, endquote));                   
			}                                                                     
                                                                            
			if (isErrorPage(title)) {                                             
				return null;                                                        
			}                                                                     
		}                                                                       
                                                                            
		return title;                                                           
  }

	public static boolean isErrorPage(String str) {
		if (str.equals("301 Moved Permanently"))     
			return true;                               
		else if (str.equals("302 Found"))            
			return true;                               
		else if (str.equals("404 Not Found"))        
			return true;                               
		else if (str.equals("403 Forbidden")) 
			return true;
		else if (str.equals("404 File Not Found")) 
			return true;
		else                                         
			return false;                              
	}                                              
  
  public static void requestQuery(String hostname, String path, Socket sock) {
    StringBuffer req = new StringBuffer("GET "); 
    req.append("/");
		req.append(path);
    req.append(" HTTP/1.0\r\nHost:");
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
    byte[] buffer = new byte[1024];
    int numchars;
  
    do {
      numchars = sock.read(buffer);
  
  	  String curr = (new String(buffer)).subString(0, numchars);
  			
  	  lq.response.append(curr);
  	  buffer = new byte[1024];
    } while(numchars > 0);
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
				if (filter(token)) {
					continue;
				}
				token = refine(token);
			}
			else {
				token = gTitle.subString(mindex);
				token = refine(token);
			}

			Queue q = (Queue)results.get(token);
			if (q == null) {
				q = global new Queue();
			}
			q.push(workingURL);	
			results.put(token, q);
		}
	}

	public boolean filter(GlobalString str) {
	  if (str.equals("of"))	return true;
  	  else if (str.equals("for")) return true;
  		else if (str.equals("a")) return true;
  		else if (str.equals("an")) return true;
  		else if (str.equals("the")) return true;
  		else if (str.equals("at")) return true;
  		else if (str.equals("and")) return true;
  		else if (str.equals("or")) return true;
  		else if (str.equals("but")) return true;
  		else if (str.equals("to")) return true;
  		else if (str.equals("The")) return true;
  		else if (str.length() == 1) {
  			if (str.charAt(0) == '.') return true;
  			else if (str.charAt(0) == '.') return true;
  			else if (str.charAt(0) == '-') return true;
  			else if (str.charAt(0) == '=') return true;
  			else if (str.charAt(0) == '_') return true;
  			else if (str.charAt(0) == ':') return true;
  			else if (str.charAt(0) == ';') return true;
  			else if (str.charAt(0) == '\'') return true;
  			else if (str.charAt(0) == '\"') return true;
  			else if (str.charAt(0) == '|') return true;
  			else if (str.charAt(0) == '@') return true;
  			else if (str.charAt(0) == '&') return true;
  			else if (str.charAt(0) == ' ') return true;
  		}
  		else 
        return false;
  }

	public GlobalString refine(GlobalString str) {
		str = refinePrefix(str);
		str = refinePostfix(str);
		return str;
	}

	public GlobalString refinePrefix(GlobalString str) {
		if (str.charAt(0) == '&') {		// &
			return str.subString(1);
		}
		return str;
	}

	public GlobalString refinePostfix(GlobalString str) {
		if (str.charAt(str.length()-1) == ',') {			// ,
			return str.subString(0, str.length()-1);
		}
		else if (str.charAt(str.length()-1) == ':') {		// :
			return str.subString(0, str.length()-1);
		}
		else if (str.charAt(str.length()-1) == ';') {		// ;
			return str.subString(0, str.length()-1);
		}
		else if (str.charAt(str.length()-1) == '!') {		// !
			return str.subString(0, str.length()-1);
		}
		else if (str.charAt(str.length()-1) == 's') {			// 's
			if (str.charAt(str.length()-2) == '\'')
				return str.subString(0, str.length()-2);	
		}
		else if (str.charAt(str.length()-1) == '-') {
			int index = str.length()-2;
			while (Character.isWhitespace(str.charAt(index--)));
			return str.subString(0, index+2);
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
