public class Request extends Socket {
    flag first;
    flag chained;
    flag done;
    flag sent;
    flag received;
    flag processed;

    String response;
    String request;
    int port;
    String remote_host_name;
    InetAddress address;
    int statuscode;
    String url;
    String method;
    boolean ssl;
    String errordescription;

    public final int SC_OK;
    public final int SC_CONNECTING_TO_HOST;
    public final int SC_HOST_NOT_FOUND;
    public final int SC_URL_BLOCKED;
    public final int SC_CLIENT_ERROR;
    public final int SC_INTERNAL_SERVER_ERROR;
    public final int SC_NOT_SUPPORTED;
    public final int SC_REMOTE_DEBUG_MODE;
    public final int SC_CONNECTION_CLOSED;
    public final int SC_HTTP_OPTIONS_THIS;
    public final int SC_FILE_REQUEST;
    public final int SC_MOVED_PERMANENTLY;
    public final int SC_CONFIG_RQ;

    public int length() {
	return request.length();
    }

    void init() {
	 SC_OK=0;
	 SC_CONNECTING_TO_HOST=1;
	 SC_HOST_NOT_FOUND=2;
	 SC_URL_BLOCKED=3;
	 SC_CLIENT_ERROR=4;
	 SC_INTERNAL_SERVER_ERROR=5;
	 SC_NOT_SUPPORTED=6;
	 SC_REMOTE_DEBUG_MODE=7;
	 SC_CONNECTION_CLOSED=8;
	 SC_HTTP_OPTIONS_THIS=9;
	 SC_FILE_REQUEST=10;
	 SC_MOVED_PERMANENTLY=11;
	 SC_CONFIG_RQ = 12;
    }

    public Request(String request) {
	init();
	this.request=request;
    }

    /**
     * Parser for the first (!) line from the HTTP request<BR>
     * Sets up the URL, method and remote hostname.
     * @return an InetAddress for the hostname, null on errors with a statuscode!=SC_OK
     */
    public void parseRequest() {
	String a=request.substring(0,request.indexOf('\n'));
	String f; 
	int pos; 
	int method_index=Jhttpp2Server.getHttpMethod(a);
	
	if (ssl) {
	    url="";
	    f = a.substring(8);
	} else {
	    method = a.substring(0,a.indexOf(" ")); //first word in the line
	    pos = a.indexOf(":"); // locate first :
	    if (pos == -1) { // occours with "GET / HTTP/1.1"
		url = a.substring(a.indexOf(" ")+1,a.lastIndexOf(" "));
		if (method_index == 0) { // method_index==0 --> GET
		    statuscode = SC_FILE_REQUEST;
		} else {
		    statuscode = SC_INTERNAL_SERVER_ERROR;
		    errordescription="This WWW proxy supports only the \"GET\" method while acting as webserver.";
		}
		return;
	    }
	    f = a.substring(pos+3); //removes "http://"
	}
	pos=f.indexOf(" "); // locate space, should be the space before "HTTP/1.1"
	if (pos==-1) { // buggy request
	    statuscode = SC_CLIENT_ERROR;
	    errordescription="Your browser sent an invalid request: \""+ a + "\"";
	    return;
	}
	f = f.substring(0,pos); //removes all after space
	// if the url contains a space... it's not our mistake...(url's must never contain a space character)
	pos=f.indexOf("/"); // locate the first slash
	if (pos!=-1) {
	    url=f.substring(pos); // saves path without hostname
	    f=f.substring(0,pos); // reduce string to the hostname
	}
	else url="/"; // occurs with this request: "GET http://localhost HTTP/1.1"
	pos = f.indexOf(":"); // check for the portnumber
	if (pos!=-1) {
	    String l_port =f.substring(pos+1);
	    if (l_port.indexOf(" ")!=-1)
		    l_port=l_port.substring(0,l_port.indexOf(" "));
	    int i_port=80;
	    i_port = Integer.parseInt(l_port);
	    f = f.substring(0,pos);
	    port=i_port;
	} else
	    port=80;
	remote_host_name = f;
	address = InetAddress.getByName(f);
    }

    public void connect() {
	connect(address, port);
    }

    public String getRequest() {
	return method + " "+url+" "+"HTTP/1.1"+"\r\n";
    }

    public void sendRequest() {
	write(getRequest().getBytes());
	write(request.substring(request.indexOf('\n')+1,request.length()).getBytes());
    }
}
