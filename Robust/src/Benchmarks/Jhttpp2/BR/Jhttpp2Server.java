/* Written and copyright 2001-2003 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 * More Information and documentation: HTTP://jhttp2.sourceforge.net/
 */

import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.net.BindException;

import java.io.*;

import java.util.Vector;
import java.util.Date;

public class Jhttpp2Server
{
    private static final String CRLF;
    private final String VERSION;
    private final String V_SPECIAL;
    private final String HTTP_VERSION;
    private final String MAIN_LOGFILE;

    private final String DATA_FILE;
    private final String SERVER_PROPERTIES_FILE;

    private String http_useragent;
    private ServerSocket listen;
    private BufferedWriter logfile;
    private BufferedWriter access_logfile;
    private long bytesread;
    private long byteswritten;
    private int numconnections;

    private boolean enable_cookies_by_default;
    private WildcardDictionary dic;
    private Vector urlactions;

    public final int DEFAULT_SERVER_PORT;
    public final String WEB_CONFIG_FILE;

    public int port;
    public InetAddress proxy;
    public int proxy_port;

    public long config_auth;
    public long config_session_id;
    public String config_user;
    public String config_password;

    public static boolean error;
    public static String error_msg;

    public boolean use_proxy;
    public boolean block_urls;
    public boolean filter_http;
    public boolean debug;
    public boolean log_access;
    public String log_access_filename;
    public boolean webconfig;
    public boolean www_server;
    

    public initvars() {
	CRLF="\r\n";
	VERSION = "0.4.62";
	V_SPECIAL = " 2003-05-20";
	HTTP_VERSION = "HTTP/1.1";
	MAIN_LOGFILE = "server.log";
	DATA_FILE = "server.data";
	SERVER_PROPERTIES_FILE = "server.properties";
	http_useragent = "Mozilla/4.0 (compatible; MSIE 4.0; WindowsNT 5.0)";
	enable_cookies_by_default=true;
	dic = new WildcardDictionary();
	urlactions = new Vector();
	DEFAULT_SERVER_PORT = 8088;
	WEB_CONFIG_FILE = "admin/jp2-config";
	port = DEFAULT_SERVER_PORT;
	proxy_port = 0;
	config_auth = 0;
	config_session_id = 0;
	config_user = "root";
	config_password = "geheim";
	use_proxy=false;
	block_urls=false;
	filter_http=false;
	debug=false;
	log_access = true;
	log_access_filename="paccess.log";
	webconfig = true;
	www_server = true;
}

  void init()
  {
      if(log_access) {
	  access_logfile=new BufferedWriter(new FileWriter(log_access_filename,true));      
      }

      logfile=new BufferedWriter(new FileWriter(MAIN_LOGFILE,true));
      writeLog("server startup...");

      listen = new ServerSocket(port);
      
      if (error) {
	  writeLog(error_msg);
	  return;
      }
  }
    public Jhttpp2Server() {
	initvars();
	init();
    }
  public Jhttpp2Server(boolean b)
  {
    initvars();
    System.printString("jHTTPp2 HTTP Proxy Server Release " + getServerVersion() + "\r\n"
      +"Copyright (c) 2001-2003 Benjamin Kohl <bkohl@users.sourceforge.net>\r\n"
      +"This software comes with ABSOLUTELY NO WARRANTY OF ANY KIND.\r\n"
      +"http://jhttp2.sourceforge.net/\n");
    init();
  }
  /** calls init(), sets up the serverport and starts for each connection
   * new Jhttpp2Connection
   */
  public void setErrorMsg(String a)
  {
    error=true;
    error_msg=a;
  }
  /**
   * Tests what method is used with the reqest
   * @return -1 if the server doesn't support the method
   */
  public static int getHttpMethod(String d)
  {
    if (startsWith(d,"GET")  || startsWith(d,"HEAD")) return 0;
    if (startsWith(d,"POST") || startsWith(d,"PUT")) return 1;
    if (startsWith(d,"CONNECT")) return 2;
    if (startsWith(d,"OPTIONS")) return 3;

    return -1;/* No match...

    Following methods are not implemented:
    || startsWith(d,"TRACE") */
  }
  public static boolean startsWith(String a,String what)
  {
    int l=what.length();
    int l2=a.length();
    if (l2>l)
	return a.substring(0,l).equals(what);
    else
	return false;
  }
  /**
   *@return the Server response-header field
   */
  public String getServerIdentification()
  {
    return "jHTTPp2/" + getServerVersion();
  }
  public String getServerVersion()
  {
    return VERSION + V_SPECIAL;
  }
  /**
   *  saves all settings with a ObjectOutputStream into a file
   * @since 0.2.10
   */
  /** restores all Jhttpp2 options from "settings.dat"
   * @since 0.2.10
   */
  /**
   * @return the HTTP version used by jHTTPp2
   */
  public String getHttpVersion()
  {
    return HTTP_VERSION;
  }
  /** the User-Agent header field
   * @since 0.2.17
   * @return User-Agent String
   */
  public String getUserAgent()
  {
    return http_useragent;
  }
  public void setUserAgent(String ua)
  {
    http_useragent=ua;
  }
  /**
   * writes into the server log file and adds a new line
   * @since 0.2.21
   */
  public void writeLog(String s)
  {
    writeLog(s,true);
  }
  /** writes to the server log file
   * @since 0.2.21
   */
  public void writeLog(String s,boolean b)
  {
      s=new Date().toString() + " " + s;
      logfile.write(s,0,s.length());
      if (b) logfile.newLine();
      logfile.flush();
      if (debug)System.printString(s);
  }

  public void closeLog()
  {
      writeLog("Server shutdown.");
      logfile.flush();
      logfile.close();
      access_logfile.close();
  }

  public void addBytesRead(long read)
  {
    bytesread+=read;
  }
  /**
   * Functions for the jHTTPp2 statistics:
   * How many connections
   * Bytes read/written
   * @since 0.3.0
   */
  public void addBytesWritten(int written)
  {
    byteswritten+=written;
  }
  public int getServerConnections()
  {
    return numconnections;
  }
  public long getBytesRead()
  {
    return bytesread;
  }
  public long getBytesWritten()
  {
    return byteswritten;
  }
  public void increaseNumConnections()
  {
    numconnections++;
  }
  public void decreaseNumConnections()
  {
    numconnections--;
  }
  public void AuthenticateUser(String u,String p) {
	  if (config_user.equals(u) && config_password.equals(p)) {
	  	config_auth = 1;
	  } else config_auth = 0;
	}
  public String getGMTString()
  {
    return new Date().toString();
  }
  public Jhttpp2URLMatch findMatch(String url)
  {
    return (Jhttpp2URLMatch)dic.get(url);
  }
  public WildcardDictionary getWildcardDictionary()
  {
    return dic;
  }
  public Vector getURLActions()
  {
    return urlactions;
  }
  public boolean enableCookiesByDefault()
  {
    return this.enable_cookies_by_default;
  }
  public void enableCookiesByDefault(boolean a)
  {
    enable_cookies_by_default=a;
  }
  public void resetStat()
  {
    bytesread=0;
    byteswritten=0;
  }
  /**
   * @since 0.4.10a
   */
  /**
   * @since 0.4.10a
   */
  /**
   * @since 0.4.10a
   */
  public void logAccess(String s)
  {
      access_logfile.write("[" + new Date().toString() + "] " + s + "\r\n");
      access_logfile.flush();
  }
  public void shutdownServer() {
	  closeLog();
	  System.exit(0);
  }

}
