/* Written and copyright 2001-2003 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;


/**
	File: Jhttpp2BufferedFilterStream.java
	@author Benjamin Kohl
*/
public class Jhttpp2ClientInputStream extends BufferedInputStream {
	private boolean filter;
	private String buf;
	private int lread;
	/**
	 * The length of the header (with body, if one)
	 */
	private int header_length;
	/**
	 * The length of the (optional) body of the actual request
	 */
	private int content_len;
	/**
	 * This is set to true with requests with bodies, like "POST"
	 */
	private boolean body;
	private static Jhttpp2Server server;
	private Jhttpp2HTTPSession connection;
	private InetAddress remote_host;
	private String remote_host_name;
	private String errordescription;
	private int statuscode;

	public String url;
	public String method;
	public int remote_port;
	public int post_data_len;
    public boolean ssl;

	public int getHeaderLength() {
          return header_length;
	}

	public InetAddress getRemoteHost() { return remote_host; }
	public String getRemoteHostName() { return remote_host_name; }

    private void init() {
	lread = 0;
	header_length = 0;
	content_len = 0;
	ssl = false;
	body = false;
	remote_port = 0;
	post_data_len = 0;
    }

    public Jhttpp2ClientInputStream(Jhttpp2Server server,Jhttpp2HTTPSession connection,InputStream a) {
	super(a);
	init();
	this.server = server;
	this.connection=connection;
    }
	/**
	 * Handler for the actual HTTP request
     * @exception IOException
	 */
    public int read(byte[] a) {
	statuscode = connection.SC_OK;
	if (ssl) return super.read(a);
	boolean cookies_enabled=server.enableCookiesByDefault();
	String rq="";
	header_length=0;
	post_data_len = 0;
	content_len = 0;
	boolean start_line=true;
	buf = getLine(); // reads the first line
	if (buf==null)
	    return -2;
	boolean cnt=true;
	while (lread>2&&cnt) {
	    if (start_line) {
		start_line = false;
		int methodID = server.getHttpMethod(buf);
		if (methodID==-1) {
		    statuscode = connection.SC_NOT_SUPPORTED;
		} else {
		    if (methodID==2) {
			ssl = true;
		    }
		    InetAddress host = parseRequest(buf,methodID);
		    
		    if (statuscode == connection.SC_OK) {
			if (!server.use_proxy && !ssl) {
			    /* creates a new request without the hostname */
			    buf = method + " " + url + " " + server.getHttpVersion() + "\r\n";
			    lread = buf.length();
			}
			if ((server.use_proxy && connection.notConnected()) || !host.equals(remote_host)) {
			    if (server.debug) server.writeLog("read_f: STATE_CONNECT_TO_NEW_HOST");
			    statuscode = connection.SC_CONNECTING_TO_HOST;
			    remote_host = host;
			}
			/* -------------------------
			 * url blocking (only "GET" method)
			 * -------------------------*/
			if (server.block_urls && methodID==0 && statuscode!=connection.SC_FILE_REQUEST) {
			    if (server.debug) System.printString("Searching match...");
			    Jhttpp2URLMatch match=server.findMatch(this.remote_host_name+url);
			    if (match!=null){
				if (server.debug) System.printString("Match found!");
				cookies_enabled=match.getCookiesEnabled();
				if (match.getActionIndex()==-1) cnt=false; else {
				    OnURLAction action=(OnURLAction)server.getURLActions().elementAt(match.getActionIndex());
				    if (action.onAccesssDeny()) {
					statuscode=connection.SC_URL_BLOCKED;
					if (action.onAccessDenyWithCustomText()) errordescription=action.getCustomErrorText();
				    } else if (action.onAccessRedirect()) {
					statuscode=connection.SC_MOVED_PERMANENTLY;
					errordescription=action.newLocation();
				    }
				}
			    }//end if match!=null)
			} //end if (server.block...
		    }
		}
	    } else { // end if(startline)
		/*-----------------------------------------------
		 * Content-Length parsing
		 *-----------------------------------------------*/
		if(server.startsWith(buf.toUpperCase(),"CONTENT-LENGTH")) {
		    String clen=buf.substring(16);
		    if (clen.indexOf("\r")!=-1) clen=clen.substring(0,clen.indexOf("\r"));
		    else if(clen.indexOf("\n")!=-1) clen=clen.substring(0,clen.indexOf("\n"));
		    content_len=Integer.parseInt(clen);
		    if (server.debug) server.writeLog("read_f: content_len: " + content_len);
		    if (!ssl) body=true; // Note: in HTTP/1.1 any method can have a body, not only "POST"
		}
		else if (server.startsWith(buf,"Proxy-Connection:")) {
		    if (!server.use_proxy) buf=null;
		    else {
			buf="Proxy-Connection: Keep-Alive\r\n";
			lread=buf.length();
		    }
		}
		/*-----------------------------------------------
		 * cookie crunch section
		 *-----------------------------------------------*/
		else if(server.startsWith(buf,"Cookie:")) {
		    if (!cookies_enabled) buf=null;
		}
		/*------------------------------------------------
		 * Http-Header filtering section
		 *------------------------------------------------*/
		else if (server.filter_http) {
		    if(server.startsWith(buf,"Referer:")) {// removes "Referer"
			buf=null;
		    } else if(server.startsWith(buf,"User-Agent")) // changes User-Agent
			{
			    buf="User-Agent: " + server.getUserAgent() + "\r\n";
			    lread=buf.length();
			}
		}
	    }
	    if (buf!=null) {
		rq+=buf;
		if (server.debug) server.writeLog(buf);
		header_length+=lread;
	    }
	    buf=getLine();
	    if (buf==null)
		return -2;
	}
	rq+=buf; //adds last line (should be an empty line) to the header String
	header_length+=lread;
	
	if (header_length==0) {
	    if (server.debug) server.writeLog("header_length=0, setting status to SC_CONNECTION_CLOSED (buggy request)");
	    statuscode=connection.SC_CONNECTION_CLOSED;
	}
	
	for (int i=0;i<header_length;i++) a[i]=(byte)rq.charAt(i);
	
	if(body) {// read the body, if "Content-Length" given
	    post_data_len = 0;
	    while(post_data_len < content_len)  {
		a[header_length + post_data_len]=(byte)read(); // writes data into the array
		post_data_len ++;
	    }
	    header_length += content_len; // add the body-length to the header-length
	    body = false;
	}
	if (statuscode==connection.SC_OK) {
	    return header_length;
	} else {
	    return -1;
	}
    }
    /**
     * reads a line
     * @exception IOException
     */
    public String getLine()
    {
	int l=0; String line=""; lread=0;
	boolean cnt=true;
	while(l!='\n'&&cnt) {
	    l=read();
	    if (l!=-1) {
		line+=(char)l;
		lread++;
	    } else {
		cnt=false;
		if (!line.equals(""))
		    return null;
	    }
	}
	return line;
    }
	/**
	* Parser for the first (!) line from the HTTP request<BR>
	* Sets up the URL, method and remote hostname.
	* @return an InetAddress for the hostname, null on errors with a statuscode!=SC_OK
	*/
	public InetAddress parseRequest(String a,int method_index)  {
	    if (server.debug) 
		server.writeLog(a);
	    String f; 
	    int pos; 
	    url="";
	    if (ssl) {
		f = a.substring(8);
	    } else {
		method = a.substring(0,a.indexOf(" ")); //first word in the line
		pos = a.indexOf(":"); // locate first :
		if (pos == -1) { // occours with "GET / HTTP/1.1"
		    url = a.substring(a.indexOf(" ")+1,a.lastIndexOf(" "));
		    if (method_index == 0) { // method_index==0 --> GET
			if (url.indexOf(server.WEB_CONFIG_FILE) != -1) {
			    statuscode = connection.SC_CONFIG_RQ;
			} else { 
			    statuscode = connection.SC_FILE_REQUEST;
			}
		    } else {
			if (method_index == 1 && url.indexOf(server.WEB_CONFIG_FILE) != -1) { // allow "POST" for admin log in
			    statuscode = connection.SC_CONFIG_RQ;
			} else {
			    statuscode=connection.SC_INTERNAL_SERVER_ERROR;
			    errordescription="This WWW proxy supports only the \"GET\" method while acting as webserver.";
			}
		    }
		    return null;
		}
		f = a.substring(pos+3); //removes "http://"
	    }
	    pos=f.indexOf(" "); // locate space, should be the space before "HTTP/1.1"
	    if (pos==-1) { // buggy request
		statuscode=connection.SC_CLIENT_ERROR;
		errordescription="Your browser sent an invalid request: \""+ a + "\"";
		return null;
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
		//BCD
		i_port = Integer.parseInt(l_port);
		f = f.substring(0,pos);
		remote_port=i_port;
	    } else
		remote_port = 80;
	    remote_host_name = f;
	    InetAddress address = null;
	    if (server.log_access) 
		server.logAccess(method + " " + getFullURL());

	    address = InetAddress.getByName(f);

	    if (remote_port == server.port && address.equals(InetAddress.getLocalHost())) {
		if (url.indexOf(server.WEB_CONFIG_FILE) != -1 && (method_index == 0 || method_index == 1))
		    statuscode = connection.SC_CONFIG_RQ;
		else if (method_index > 0 ) {
		    statuscode=connection.SC_INTERNAL_SERVER_ERROR;
		    errordescription="This WWW proxy supports only the \"GET\" method while acting as webserver.";
		} else
		    statuscode = connection.SC_FILE_REQUEST;
	    }
	    return address;
	}
	/**
        * @return boolean whether the actual connection was established with the CONNECT method.
	* @since 0.2.21
	*/
	public boolean isTunnel() {
          return ssl;
	}
    /**
     * @return the full qualified URL of the actual request.
     * @since 0.4.0
     */
    public String getFullURL() {
	String sh="";
	if (ssl)
	    sh="s";
	if (remote_port!=80)
		return "http" + sh + "://" + getRemoteHostName()
		+ ":" + remote_port + url;
	else
		return "http" + sh + "://" + getRemoteHostName()
		    + url;
    }
    /**
     * @return status-code for the actual request
     * @since 0.3.5
     */
	public int getStatusCode()
	{
		return statuscode;
	}
	/**
	* @return the (optional) error-description for this request
	*/
	public String getErrorDescription()
	{
		return errordescription;
	}
}
