public class Query {
    private String hostname;
    private String path;

    private StringBuffer response;

    public Query(String hostname, String path) {
	this.hostname=hostname;
	this.path=path;
	response=new StringBuffer();
    }

    public String getHostName() {
	return hostname;
    }

    public String getPath() {
	return path;
    }
    
    public String makewebcanonical(String page) {
	StringBuffer b=new StringBuffer(getHostName(page));
	b.append("/");
	b.append(getPathName(page));
	return b.toString();
    }

    public String getHostName(String page) {
	String http=new String("http://");
	if (page.indexOf(http)==-1) {
	    return getHostName();
	} else {
	    int beginindex=page.indexOf(http)+http.length();
	    int endindex=page.indexOf('/',beginindex+1);
	    if ((beginindex==-1)) {
		System.printString("ERROR");
	    }
	    if (endindex==-1)
		endindex=page.length();
	    return page.subString(beginindex, endindex);
	}
    }

    public String getPathName(String page) {
	String http=new String("http://");
	if (page.indexOf(http)==-1) {
	    String path=getPath();
	    int lastindex=path.lastindexOf('/');
	    if (lastindex==-1)
		return page;
	    
	    StringBuffer sb=new StringBuffer(path.subString(0,lastindex+1));
	    sb.append(page);
	    return sb.toString();
	} else {
	    int beginindex=page.indexOf(http)+http.length();
	    int nextindex=page.indexOf('/',beginindex+1);
	    if ((beginindex==-1)||(nextindex==-1))
		return new String("index.html");
	    return page.subString(nextindex+1, page.length()-1);
	}
    }
}
