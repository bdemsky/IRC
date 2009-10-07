public class Query {
  GlobalString hostname;
  GlobalString path;
	int depth;
  
  public Query(GlobalString hostname, GlobalString path, int depth) {
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

  public GlobalString getHostName(GlobalString page) {
    GlobalString http = global new GlobalString("http://");
    if (page.indexOf(http) == -1) {
      return getHostName();
    } else {
      int beginindex = page.indexOf(http) + http.length();
	    int endindex = page.indexOf('/',beginindex+1);
	    if ((beginindex == -1)) {
        System.printString("ERROR");
	    }
	    if (endindex == -1)
        endindex = page.length();
      return page.subString(beginindex, endindex);
    }
  }

  
	public GlobalString getPathName(GlobalString page) {
    GlobalString http = global new GlobalString("http://");
    if (page.indexOf(http) == -1) {
      GlobalString path = getPath();
	    int lastindex = path.lastindexOf('/');
	    if (lastindex == -1)
        return page;
	    
      GlobalStringBuffer sb = global new GlobalStringBuffer(path.subString(0,lastindex+1));
	    sb.append(page);
      return sb.toGlobalString();
    } else {
      int beginindex = page.indexOf(http)+http.length();
	    int nextindex = page.indexOf('/',beginindex+1);
	    if ((beginindex == -1) || (nextindex == -1))
        return global new GlobalString("index.html");
      return page.subString(nextindex+1, page.length());
    }
  }
}
