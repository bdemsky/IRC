public class Query extends Socket {
    flag requested;
    flag processed;
    flag received;
    public int state;

    private String hostname;
    private String path;

    private StringBuffer response;

    public Query(String hostname, String path) {
	this.hostname=hostname;
	this.path=path;
	response=new StringBuffer();
	state=0;
    }

    public void makeConnection() {
	InetAddress address=InetAddress.getByName(hostname);
	int port=80;
        fd=nativeBind(address.getAddress(), port);
        nativeConnect(fd, address.getAddress(), port);
    }

    public String getHostName() {
	return hostname;
    }

    public String getPath() {
	return path;
    }

    public void outputFile() {
	StringBuffer sb=new StringBuffer(hostname);
	sb.append(path);
	FileOutputStream fos=new FileOutputStream(sb.toString().replace('/','#'));
	fos.write(response.toString().getBytes());
	fos.close();
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
