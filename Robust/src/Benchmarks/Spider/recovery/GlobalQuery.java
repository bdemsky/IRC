public class GlobalQuery {
  GlobalString hostname;
  GlobalString path;
	int depth;
  
	public GlobalQuery(GlobalString hostname) {
		this.hostname = hostname;
		this.path = global new GlobalString("");
		this.depth = 0;
	}

  public GlobalQuery(GlobalString hostname, GlobalString path, int depth) {
    this.hostname = global new GlobalString(hostname);
    this.path = global new GlobalString(path);
		this.depth = depth;
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
		} 
		else if (page.indexOf(https) != -1) {
			beginindex = page.indexOf(https) + https.length();
		}
		else {
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
		int beginindex;
		int nextindex;

		if ((page.indexOf(http) == -1) && (page.indexOf(https) == -1)) {
      GlobalString path = getPath();
	    int lastindex = path.lastindexOf('/');
	    if (lastindex == -1)
        return page;
	    
      GlobalStringBuffer sb = global new GlobalStringBuffer(path.subString(0,lastindex+1));
	    sb.append(page);
      return sb.toGlobalString();
    } 
		else if (page.indexOf(https) != -1) {
			beginindex = page.indexOf(https) + https.length();
		}
		else {
			beginindex = page.indexOf(http) + http.length();
		}
		nextindex = page.indexOf('/',beginindex+1);

		if ((beginindex == -1) || (nextindex == -1))
			return global new GlobalString("index.html");
		return page.subString(nextindex+1, page.length());
  }
}
