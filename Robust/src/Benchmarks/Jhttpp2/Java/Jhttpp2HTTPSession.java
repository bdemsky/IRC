/* Written and copyright 2001-2003 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 */

import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;


/**
	One HTTP connection
	@file Jhttpp2HTTPSession.java
	@author Benjamin Kohl
*/
public class Jhttpp2HTTPSession extends Thread {

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

    private Jhttpp2Server server;
    
    /** downstream connections */
    private Socket client;
    private BufferedOutputStream out;
    private Jhttpp2ClientInputStream in;
    
    /** upstream connections */
    private Socket HTTP_Socket;
    private BufferedOutputStream HTTP_out;
    private Jhttpp2ServerInputStream HTTP_in;
    
	public Jhttpp2HTTPSession(Jhttpp2Server server,Socket client) {
	    init();
	    in = new Jhttpp2ClientInputStream(server,this,client.getInputStream());//,true);
	    out = new BufferedOutputStream(client.getOutputStream());
	    this.server=server;
	    this.client=client;
	    start();
	}
	public Socket getLocalSocket() {
		return client;
	}
	public Socket getRemoteSocket() {
		return HTTP_Socket;
	}
	public boolean isTunnel() {
		return in.isTunnel();
	}
	public boolean notConnected() {
		return HTTP_Socket==null;
	}
    public void sendHeader(int a,boolean b) {
		sendHeader(a);
		endHeader();
		out.flush();
	}
	public void sendHeader(int status, String content_type, long content_length) {
		sendHeader(status);
		sendLine("Content-Length", String.valueOf(content_length));
		sendLine("Content-Type", content_type );
	}
	public void sendLine(String s) {
		write(out,s + "\r\n");
	}
	public void sendLine(String header, String s) {
		write(out,header + ": " + s + "\r\n");
	}
	public void endHeader() {
		write(out,"\r\n");
	}
	public void run() {
		if (server.debug)server.writeLog("begin http session");
		server.increaseNumConnections();
		handleRequest();
		in.close(); // since 0.4.10b
		out.close();
		client.close();
		// close upstream connections (webserver or other proxy)
		if (!notConnected()) {
		    HTTP_Socket.close();
		    HTTP_out.close();
		    HTTP_in.close();
		}
		server.decreaseNumConnections();
		if (server.debug)server.writeLog("end http session");
	}
	/** sends a message to the user */
	public void sendErrorMSG(int a,String info) {
		String statuscode = sendHeader(a);
		String localhost = "localhost"+":"+server.port;
		String msg = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\"><html>\r"
		    + "<!-- jHTTPp2 error message --><HEAD>\r"
		    + "<TITLE>" + statuscode + "</TITLE>\r"
		    + "<link rel=\"stylesheet\" type=\"text/css\" href=\"http://" + localhost + "/style.css\"></HEAD>\r"  // use css style sheet in htdocs
		    + "<BODY BGCOLOR=\"#FFFFFF\" TEXT=\"#000000\" LINK=\"#000080\" VLINK=\"#000080\" ALINK=\"#000080\">\r"
		    + "<h2 class=\"headline\">HTTP " + statuscode + " </h2>\r"
		    + "<HR size=\"4\">\r"
		    + "<p class=\"i30\">Your request for the following URL failed:</p>"
		    + "<p class=\"tiagtext\"><a href=\"" + in.getFullURL() + "\">" + in.getFullURL() + "</A> </p>\r"
		    + "<P class=\"i25\">Reason: " + info + "</P>"
		    + "<HR size=\"4\">\r"
		    + "<p class=\"i25\"><A HREF=\"http://jhttp2.sourceforge.net/\">jHTTPp2</A> HTTP Proxy, Version " + server.getServerVersion() + " at " + localhost
		    + "<br>Copyright &copy; 2001-2003 <A HREF=\"mailto:bkohl@users.sourceforge.net\">Benjamin Kohl</A></p>\r"
		    + "<p class=\"i25\"><A HREF=\"http://" + localhost + "/\">jHTTPp2 local website</A> <A HREF=\"http://" + localhost + "/" + server.WEB_CONFIG_FILE + "\">Configuration</A></p>"
		    + "</BODY></HTML>";
		sendLine("Content-Length",String.valueOf(msg.length()));
		sendLine("Content-Type","text/html; charset=iso-8859-1");
		endHeader();
		write(out,msg);
		out.flush();
	}

	public String sendHeader(int a) {
	    String stat;
	    if (a==200)
		stat="200 OK";
	    else if (a==202)
		stat="202 Accepted";
	    else if (a==300)
		stat="300 Ambiguous";
	    else if (a==301)
		stat="301 Moved Permanently";
	    else if (a==400)
		stat="400 Bad Request";
	    else if (a==401)
		stat="401 Denied";
	    else if (a==403)
		stat="403 Forbidden";
	    else if (a==404)
		stat="404 Not Found";
	    else if (a==405)
		stat="405 Bad Method";
	    else if (a==413)
		stat="413 Request Entity Too Large";
	    else if (a==415)
		stat="415 Unsupported Media";
	    else if (a==501)
		stat="501 Not Implemented";
	    else if (a==502)
		stat="502 Bad Gateway";
	    else if (a==504)
		stat="504 Gateway Timeout";
	    else if (a==505)
		stat="505 HTTP Version Not Supported";
	    else 
		stat="500 Internal Server Error";
	    sendLine(server.getHttpVersion() + " " + stat);
	    sendLine("Server",server.getServerIdentification());
	    if (a==501) 
		sendLine("Allow","GET, HEAD, POST, PUT, DELETE, CONNECT");
	    sendLine("Cache-Control", "no-cache, must-revalidate");
	    sendLine("Connection","close");
	    return stat;
	}
    
    /** the main routine, where it all happens */
    public void handleRequest() {
	InetAddress remote_host;
	Jhttpp2Read remote_in=null;
	int remote_port;
	byte[] b=new byte[65536];
	int numread=in.read(b);
	boolean cnt=true;
	while(cnt) { // with this loop we support persistent connections
	    if (numread==-1) { // -1 signals an error
		if (in.getStatusCode()!=SC_CONNECTING_TO_HOST) {
		    int code=in.getStatusCode();
		    if (code==SC_CONNECTION_CLOSED) {
		    } else if (code==SC_CLIENT_ERROR) {
			sendErrorMSG(400,"Your client sent a request that this proxy could not understand. (" + in.getErrorDescription() + ")");
		    } else if (code==SC_HOST_NOT_FOUND)
			sendErrorMSG(504,"Host not found.<BR>jHTTPp2 was unable to resolve the hostname of this request. <BR>Perhaps the hostname was misspelled, the server is down or you have no connection to the internet.");
		    else if (code==SC_INTERNAL_SERVER_ERROR) 
			sendErrorMSG(500,"Server Error! (" + in.getErrorDescription() + ")");
		    else if (code==SC_NOT_SUPPORTED)
			sendErrorMSG(501,"Your client used a HTTP method that this proxy doesn't support: (" + in.getErrorDescription() + ")");
		    else if (code==SC_URL_BLOCKED) {
			if (in.getErrorDescription()!=null && in.getErrorDescription().length()>0)
			    sendErrorMSG(403,in.getErrorDescription());
			else
			    sendErrorMSG(403,"The request for this URL was denied by the jHTTPp2 URL-Filter.");
		    } else if (code==SC_HTTP_OPTIONS_THIS) {
			sendHeader(200); endHeader();
		    } else if (code==SC_FILE_REQUEST)
			file_handler();
		    else if (code==SC_CONFIG_RQ) 
			admin_handler(b);
		    //case SC_HTTP_TRACE:
		    else if (code==SC_MOVED_PERMANENTLY) {
			sendHeader(301);
			write(out,"Location: " + in.getErrorDescription() + "\r\n");
			endHeader();
			out.flush();
		    }
		    cnt=false;// return from main loop.
		} else { // also an error because we are not connected (or to the wrong host)
		    // Creates a new connection to a remote host.
		    if (!notConnected()) {
			HTTP_Socket.close();
		    }
		    numread=in.getHeaderLength(); // get the header length
		    if (!server.use_proxy) {// sets up hostname and port
			remote_host=in.getRemoteHost();
			remote_port=in.remote_port;
		    } else {
			remote_host=server.proxy;
			remote_port=server.proxy_port;
		    }
		    connect(remote_host,remote_port);
		    if (!in.isTunnel()  || (in.isTunnel() && server.use_proxy))
			{ // no SSL-Tunnel or SSL-Tunnel with another remote proxy: simply forward the request
			    HTTP_out.write(b, 0, numread);
			    HTTP_out.flush();
			}
		    else
			{ //  SSL-Tunnel with "CONNECT": creates a tunnel connection with the server
			    sendLine(server.getHttpVersion() + " 200 Connection established");
			    sendLine("Proxy-Agent",server.getServerIdentification());
			    endHeader(); out.flush();
			}
		    remote_in = new Jhttpp2Read(server,this, HTTP_in, out); // reads data from the remote server
		    server.addBytesWritten(numread);
		}
	    }
	    if (cnt) {
		while(cnt) { // reads data from the client
		    numread=in.read(b);
		    if (numread!=-1) {
			HTTP_out.write(b, 0, numread);
			HTTP_out.flush();
			server.addBytesWritten(numread);
		    } else cnt=false;
		} // end of inner loop
		cnt=true;
	    }
	}// end of main loop
	out.flush();
	if (!notConnected() && remote_in != null)
	    remote_in.close(); // close Jhttpp2Read thread
	return;
    }
    /** connects to the given host and port */
    public void connect(InetAddress host,int port) {
	HTTP_Socket = new Socket(host,port);
	HTTP_in = new Jhttpp2ServerInputStream(server,this,HTTP_Socket.getInputStream(),false);
	HTTP_out = new BufferedOutputStream(HTTP_Socket.getOutputStream());
    }
  /** converts an String into a Byte-Array to write it with the OutputStream */
  public void write(BufferedOutputStream o,String p) {
    o.write(p.getBytes(),0,p.length());
  }

  /**
   * Small webserver for local files in {app}/htdocs
   * @since 0.4.04
   */
  public void file_handler() {
	if (!server.www_server) {
		sendErrorMSG(500, "The jHTTPp2 built-in WWW server module is disabled.");
		return;
	}
    String filename=in.url;
    if (filename.equals("/")) filename="index.html"; // convert / to index.html
    else if (filename.startsWith("/")) filename=filename.substring(1);
    if (filename.endsWith("/")) filename+="index.html"; // add index.html, if ending with /
    File file = new File("htdocs/" + filename); // access only files in "htdocs"
    if (true// !file.exists() || !file.canRead() // be sure that we can read the file
		|| filename.indexOf("..")!=-1 // don't allow ".." !!!
	//		|| file.isDirectory() 
) { // dont't read if it's a directory
      sendErrorMSG(404,"The requested file /" + filename + " was not found or the path is invalid.");
      return;
    }
    int pos = filename.lastIndexOf("."); // MIME type of the specified file
    String content_type="text/plain"; // all unknown content types will be marked as text/plain
    if (pos != -1) {
    	String extension = filename.substring(pos+1);
    	if (extension.equalsIgnoreCase("htm") || (extension.equalsIgnoreCase("html"))) content_type="text/html; charset=iso-8859-1";
    	else if (extension.equalsIgnoreCase("jpg") || (extension.equalsIgnoreCase("jpeg"))) content_type="image/jpeg";
    	else if (extension.equalsIgnoreCase("gif")) content_type = "image/gif";
    	else if (extension.equalsIgnoreCase("png")) content_type = "image/png";
    	else if (extension.equalsIgnoreCase("css")) content_type = "text/css";
    	else if (extension.equalsIgnoreCase("pdf")) content_type = "application/pdf";
    	else if (extension.equalsIgnoreCase("ps") || extension.equalsIgnoreCase("eps")) content_type = "application/postscript";
    	else if (extension.equalsIgnoreCase("xml")) content_type = "text/xml";
	}
    sendHeader(200,content_type, file.length() );
    endHeader();
    BufferedInputStream file_in = new BufferedInputStream(new FileInputStream(file));
    byte[] buffer=new byte[4096];
    int a=file_in.read(buffer);
    while (a!=-1) { // read until EOF
      out.write(buffer,0,a);
      a = file_in.read(buffer);
    }
    out.flush();
    file_in.close(); // finished!
  }
  /**
   * @since 0.4.10b
   */
  public int getStatus()
  {
    return in.getStatusCode();
  }
  /**
     * @since 0.4.20a
     * admin webpage
   */
    public void admin_handler(byte[] b) {
    }
}

