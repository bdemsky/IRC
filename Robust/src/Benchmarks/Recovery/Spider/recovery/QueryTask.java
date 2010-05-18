public class QueryTask extends Task {
  int maxDepth;
  int maxSearchDepth;
  DistributedHashMap results;
  DistributedLinkedList results_list;
  DistributedHashMap visitedList;
  GlobalString gTitle;
  GlobalString workingURL;
  GlobalString hostname;
  GlobalString path;
  int depth;

  public QueryTask(DistributedHashMap visitedList, int maxDepth, int maxSearchDepth, DistributedHashMap results, DistributedLinkedList results_list, GlobalString hostname, GlobalString path, int depth) {
    this.hostname=hostname;
    this.path=path;
    this.depth=depth;
    this.visitedList = visitedList;
    this.maxDepth = maxDepth;
    this.maxSearchDepth = maxSearchDepth;
    this.results = results;
    this.results_list = results_list;
  }
  
  public void execute() {
    int max;
    int maxSearch;
    int ldepth;

    atomic {
      max = this.maxDepth;
      maxSearch = this.maxSearchDepth;
      ldepth=this.depth;
    }

    if (ldepth < max) {
      /* local variables */
      String hostname=null;
      String path=null;
      String title=null;

      atomic {
        hostname = new String(GlobalString.toLocalCharArray(getHostName()));
        path = new String(GlobalString.toLocalCharArray(getPath()));
        System.out.println("hostname= " + hostname + " path= " + path);
        GlobalStringBuffer gsb = global new GlobalStringBuffer(hostname);
        gsb.append("/");
        gsb.append(path);
        workingURL = global new GlobalString(gsb.toGlobalString());
        gTitle = null;
      }
      LocalQuery lq = new LocalQuery(hostname, path, ldepth);

      /*
      if (isDocument(path)) {
        return;
      }
      */

      Socket s = new Socket();

      if(s.connect(hostname, 80) == -1) {
        return;
      }

      if(requestQuery(hostname, path, s) == 0) {
        readResponse(lq, s);
        if ((title = grabTitle(lq)) != null) {
          atomic {
            //commits everything...either works or fails
            gTitle = global new GlobalString(title);
            processPage(lq);
            dequeueTask();
          }
        }
      } else {
        atomic {
          dequeueTask();
        }
      }

      /*
      if(requestQuery(hostname, path, s) == -1) {
        atomic {
          dequeueTask();
        }
      } else {
        readResponse(lq, s);
        if ((title = grabTitle(lq)) != null) {
          atomic {
            //commits everything...either works or fails
            gTitle = global new GlobalString(title);
            processPage(lq);
            dequeueTask();
          }
        }
      }
      */
      /*
      requestQuery(hostname, path, s);
      readResponse(lq, s);
      if ((title = grabTitle(lq)) != null) {
        atomic {
          //commits everything...either works or fails
          gTitle = global new GlobalString(title);
          processPage(lq);
          dequeueTask();
        }
      }
      */
      s.close();
    } else {
      atomic {
        dequeueTask();
      }
    }
  }
  
  public int getDepth() {
    return depth;
  }
  
  public GlobalString getHostName() {
    return hostname;
  }
  
  public GlobalString getPath() {
    return path;
  }

  public GlobalString makewebcanonical(GlobalString page) {
    GlobalStringBuffer b = global new GlobalStringBuffer(getHostName(page));
    b.append("/");
    b.append(getPathName(page));
    return b.toGlobalString();
  }
  
  public GlobalString getHostName(GlobalString page) {
    GlobalString http = global new GlobalString("http://");
    GlobalString https = global new GlobalString("https://");
    int beginindex;
    int endindex;
    
    if ((page.indexOf(http) == -1) && (page.indexOf(https) == -1)) {
      return getHostName();
    } else if (page.indexOf(https) != -1) {
      beginindex = page.indexOf(https) + https.length();
    } else {
      beginindex = page.indexOf(http) + http.length();
    }
    endindex = page.indexOf('/',beginindex+1);
    
    if ((beginindex == -1)) {	
      System.printString("ERROR");
    }
    if (endindex == -1)
      endindex = page.length();
    
    return page.subString(beginindex, endindex);
  }
  
  
  public GlobalString getPathName(GlobalString page) {
    GlobalString http = global new GlobalString("http://");
    GlobalString https = global new GlobalString("https://");
    int beginindex=0;
    int nextindex=0;
    
    if ((page.indexOf(http) == -1) && (page.indexOf(https) == -1)) {
      GlobalString path = getPath();
      int lastindex = path.lastindexOf('/');
      if (lastindex == -1)
        return page;
      
      GlobalStringBuffer sb = global new GlobalStringBuffer(path.subString(0,lastindex+1));
      sb.append(page);
      return sb.toGlobalString();
    } else if (page.indexOf(https) != -1) {
      beginindex = page.indexOf(https) + https.length();
    } else {
      beginindex = page.indexOf(http) + http.length();
    }
    nextindex = page.indexOf('/',beginindex+1);
    if ((beginindex == -1) || (nextindex == -1))
      return global new GlobalString("index.html");
    return page.subString(nextindex+1, page.length());
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
      else if ((str.subString(index+1)).equals("tar")) return true;
      else if ((str.subString(index+1)).equals("tgz")) return true;
      else return false;
    }
    return false;
  }
  
  /*
  public void output() {
    String str;
    Iterator iter = results_list.iterator();
    
    System.out.println("Size = " + results_list.size());
  }
  */

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
        if (mindex >= endquote) {
          return null;
        }
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
  
  public static int requestQuery(String hostname, String path, Socket sock) {
    StringBuffer req = new StringBuffer("GET "); 
    req.append("/");
    req.append(path);
    req.append(" HTTP/1.0\r\nHost: ");
    req.append(hostname);
    req.append("\r\n\r\n");
    if(sock.write(req.toString().getBytes()) == -1) {
      return -1; //error in openning this webpage
    } else { 
      return 0;
    }
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

  public void processedList() {
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
      } else {
        token = gTitle.subString(mindex);
        token = refine(token);
      }

      GlobalQueue q = (GlobalQueue)results.get(token);
      if (q == null) {
        q = global new GlobalQueue();
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
    else return false;
  }
  
  public GlobalString refine(GlobalString str) {
    str = refinePrefix(str);
    str = refinePostfix(str);
    return str;
  }
  
  public GlobalString refinePrefix(GlobalString str) {
    if (str.charAt(0) == '&') {		// &
      return str.subString(1);
    } else if (str.charAt(0) == '/') {		// &
      return str.subString(1);
    }
    return str;
  }
  
  public GlobalString refinePostfix(GlobalString str) {
    if (str.charAt(str.length()-1) == ',') {			// ,
      return str.subString(0, str.length()-1);
    } else if (str.charAt(str.length()-1) == ':') {		// :
      return str.subString(0, str.length()-1);
    } else if (str.charAt(str.length()-1) == ';') {		// ;
      return str.subString(0, str.length()-1);
    } else if (str.charAt(str.length()-1) == '!') {		// !
      return str.subString(0, str.length()-1);
    } else if (str.charAt(str.length()-1) == 's') {			// 's
      if (str.charAt(str.length()-2) == '\'')
	return str.subString(0, str.length()-2);	
    } else if (str.charAt(str.length()-1) == '-') {
      int index = str.length()-2;
      while (Character.isWhitespace(str.charAt(index--)));
      return str.subString(0, index+2);
    }
    return str;
  }
  

  public void processPage(LocalQuery lq) {
    //System.out.println("Inside processPage");
    /*
    if ((gTitle != null) && (gTitle.length() > 0)) {
      processedList();
    }
    */

    int index = 0;
    String href = new String("href=\"");
    String searchstr = lq.response.toString();
    int searchCnt = 0;    
    while(true) {
      int mindex = searchstr.indexOf(href,index);
      if (mindex != -1) {	
        int endquote = searchstr.indexOf('"', mindex+href.length());
        if (endquote != -1) {
          String match = searchstr.subString(mindex+href.length(), endquote);
          String match2 = lq.makewebcanonical(match);
          //System.out.println("match= " + match + " match2= " + match2);

          GlobalString ghostname;
          GlobalString gpath;

          ghostname = global new GlobalString(lq.getHostName(match));
          gpath = global new GlobalString(lq.getPathName(match));

          GlobalStringBuffer gsb = global new GlobalStringBuffer(ghostname);
          gsb.append("/");
          gsb.append(gpath);
          //System.out.println("match2=" + match2 + lq.getHostName(match)+"/"+lq.getPathName(match));

          if (match2 != null) {
            if (!visitedList.containsKey(gsb.toGlobalString()) && (searchCnt < maxSearchDepth)) {
              //System.out.println("I am here");
              GlobalString str = global new GlobalString("1");
              visitedList.put(gsb.toGlobalString(), str);
              //results_list.add(gsb.toGlobalString());
              searchCnt++;
              QueryTask gq = global new QueryTask(visitedList, maxDepth, maxSearchDepth, results, results_list, ghostname, gpath, lq.getDepth()+1);
              enqueueTask(gq);
            }
          }
          index = endquote;
        } else {
          //System.out.println("mindex= " + mindex + " index= " + index + " endquote= " + endquote + " href.length()= " + href.length());
          break;
        }
      } else { 
        //System.out.println("mindex= " + mindex + " index= " + index);
        break;
      }
    }
    //System.out.println("End of processPage");
    //System.out.println("\n");
  }
}
