public class QueryTask {
	int maxDepth;
	int maxSearchDepth;
	Queue todoList;
	HashMap results;
	HashMap visitedList;
	LinkedList results_list;

	String title;
	String workingURL;

  public QueryTask(Queue todoList, HashMap visitedList, int maxDepth, int maxSearchDepth, HashMap results, LinkedList results_list) {
		this.todoList = todoList;
		this.visitedList = visitedList;
		this.maxDepth = maxDepth;
		this.maxSearchDepth = maxSearchDepth;
		this.results = results;
		this.results_list = results_list;
		title = new String();
		workingURL = new String();
  }

  public void execute() {
		int depth;
		LocalQuery lq;
		String hostname;
		String path;
		String title;
		Queue toprocess;

		lq = (LocalQuery)(todoList.pop());
		depth = lq.getDepth();
		
		while (depth < maxDepth) {
			toprocess = new Queue();
			hostname = lq.getHostName();
			path = lq.getPath();

			StringBuffer sb = new StringBuffer(hostname);
			sb.append("/");
			sb.append(path);
			workingURL = sb.toString();
			title = null;

			//System.printString("["+lq.getDepth()+"] ");
			//System.printString("Processing - Hostname : ");
			//System.printString(hostname);
			//System.printString(", Path : ");
			//System.printString(path);
			//System.printString("\n");

			if (isDocument(path)) {
				lq = (LocalQuery)(todoList.pop());
				depth = lq.getDepth();
				continue;
			}

			Socket s = new Socket();

			if(s.connect(hostname, 80) == -1) {
				lq = (LocalQuery)(todoList.pop());
				depth = lq.getDepth();
				continue;
			}

//			System.out.println("AAA");
			requestQuery(hostname, path, s);
//			System.out.println("BBB");
			readResponse(lq, s);

//			System.out.println("CCC");
			if ((title = grabTitle(lq)) != null) {
				toprocess = processPage(lq);
			}
//			System.out.println("DDD");

			s.close();
			done(toprocess);
			lq = (LocalQuery)(todoList.pop());
			depth = lq.getDepth();
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

	public void done(Queue toprocess) {
		if ((title != null) && (title.length() > 0)) {
			processedList();
		}

		int searchCnt = 0;
		while(!toprocess.isEmpty()) {
			LocalQuery q = (LocalQuery)toprocess.pop();

			String hostname = new String(q.getHostName());
			String path = new String(q.getPath());

			StringBuffer sb = new StringBuffer(hostname);
			sb.append("/");
			sb.append(path);

			if (!visitedList.containsKey(sb.toString()) && (searchCnt < maxSearchDepth)) {
				todoList.push(q);
					
				String str = new String("1");			// dump data
				visitedList.put(sb.toString(), str);		// if URL is never visited, put in the list
				results_list.add(sb.toString());				// the whole list that once visited
				searchCnt++;
			}
		}
	}

	public void output() {
		String str;
		Iterator iter = results_list.iterator();

		while (iter.hasNext() == true) {
			str = ((String)(iter.next()));
			//System.printString(str + "\n");
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

			if (isErrorPage(title)) {			// error page
				return null;
			}
		}

		return title;
	}
	
	public static boolean isErrorPage(String str) {				// error msg list
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
	  req.append(" HTTP/1.0\r\nHost: ");
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

	public void processedList() {
		LinkedList ll;
		String token = null;
		int mindex = 0;
		int endquote = 0;

		while (endquote != -1) {
			endquote = title.indexOf(' ', mindex);

			if (endquote != -1) {
				token = title.subString(mindex, endquote);
				mindex = endquote + 1;
				if (filter(token)) {
					continue;
				}
				token = refine(token);
			}
			else {
				token = title.subString(mindex);
				token = refine(token);
			}

			Queue q = (Queue)results.get(token);
			if (q == null) {
				q = new Queue();
			}
			q.push(workingURL);	
			results.put(token, q);
		}
	}
	
	public boolean filter(String str) {
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
	
	public String refine(String str) {		// if title has some unnecessary prefix or postfix 
		str = refinePrefix(str);
		str = refinePostfix(str);
		return str;
	}
	
	public String refinePrefix(String str) {
		if (str.charAt(0) == '&') {		// &
			return str.subString(1);
		}
		else if (str.charAt(0) == '/') {		// &
			return str.subString(1);
		}
		return str;
	}

	public String refinePostfix(String str) {
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

		toprocess = new Queue();
		while(cont) {
			int mindex = searchstr.indexOf(href,index);
			if (mindex != -1) {	
				int endquote = searchstr.indexOf('"', mindex+href.length());
     		if (endquote != -1) {
		      String match = searchstr.subString(mindex+href.length(), endquote);
					String match2 = lq.makewebcanonical(match);
	
					String hostname;
					String path;

					hostname = new String(lq.getHostName(match));
					path = new String(lq.getPathName(match));

		      if (match2 != null) {
							LocalQuery gq = new LocalQuery(hostname, path, depth);
							toprocess.push(gq);
					}
					index = endquote;
        } else cont = false;
      } else cont = false;
    }
		return toprocess;
  }
}
