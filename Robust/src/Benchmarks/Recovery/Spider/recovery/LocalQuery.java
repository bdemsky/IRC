public class LocalQuery {
  String hostname;
  String path;
	StringBuffer response;
	int depth;
  
  public LocalQuery(String hostname, String path, int depth) {
    this.hostname = new String(hostname);
    this.path = new String(path);
		response = new StringBuffer();
		this.depth = depth;
  }

	public int getDepth() {
		return depth;
	}
	
  public String getHostName() {
    return hostname;
  }
 
  public String getPath() {
    return path;
  }

  public void outputFile() {
		StringBuffer sb = new StringBuffer(hostname);
		sb.append(path);
    FileOutputStream fos = new FileOutputStream(sb.toString().replace('/','#'));
    fos.write(response.toString().getBytes());
    fos.close();
  }

  public String makewebcanonical(String page) {
    StringBuffer b = new StringBuffer(getHostName(page));
    b.append("/");
		b.append(getPathName(page));
    return b.toString();
  }

	public String getHostName(String page) {
		String http = new String("http://");
		String https = new String("https://");
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

	public String getPathName(String page) {
		String http = new String("http://");
		String https = new String("https://");
		int beginindex;
		int nextindex;

		if ((page.indexOf(http) == -1) && (page.indexOf(https) == -1)) {
			String path = getPath();
			int lastindex = path.lastindexOf('/');
			if (lastindex == -1)
				return page;
	    
			StringBuffer sb = new StringBuffer(path.subString(0,lastindex+1));
			sb.append(page);
			return sb.toString();
		}
		else if (page.indexOf(https) != -1) {
			beginindex = page.indexOf(https) + https.length();
		}
		else {
			beginindex = page.indexOf(http) + http.length();
		}
		nextindex = page.indexOf('/',beginindex+1);

		if ((beginindex==-1) || (nextindex==-1))
			return new String("index.html");
		return page.subString(nextindex+1, page.length());
	}
}
