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
public class Jhttpp2ClientInputStream {
    private boolean filter;
    /**
     * This is set to true with requests with bodies, like "POST"
     */
    
    public boolean ssl;
    flag foo;
    
    private void init() {
	ssl = false;
    }

    public Jhttpp2ClientInputStream() {
	init();
    }


    public Request read(String str, Jhttpp2HTTPSession connection, tag ct, tag tc) {
	//can check for ssl first
	String s=readstr(str, connection);
	if (s!=null) {
	    return new Request(s){}{ct, tc};
	} else
	    return null;
    }

    public Request readfirst(String str, Jhttpp2HTTPSession connection, tag ct, tag tc) {
	String s=readstr(str, connection);
	if (s!=null) {
	    return new Request(s){first}{ct, tc};
	} else
	    return null;
    }
    
    public String readstr(String str, Jhttpp2HTTPSession connection) {
	String rq="";
	int content_len=0;
	boolean tempssl=false;

	if (ssl) 
	    return str;

	String buf = getLine(str); // reads the first line
	str = updateBuffer(str, buf);
	if (buf==null)
	    return null;
	rq += buf;
	if (buf.startsWith("CONNECT"))
	    tempssl=true;
	boolean cnt=true;
	while(cnt) {
	    buf = getLine(str); // reads the first line
	    str = updateBuffer(str, buf);
	    if (buf==null)
		return null;
	    rq += buf;
	    
	    if (buf.length()<=2) {
		cnt=false;
	    } else {
		if (buf.toUpperCase().startsWith("CONTENT-LENGTH")) {
		    String clen=buf.substring(16);
		    if (clen.indexOf("\r")!=-1) 
			clen=clen.substring(0,clen.indexOf("\r"));
		    else 
			if(clen.indexOf("\n")!=-1) clen=clen.substring(0,clen.indexOf("\n"));
		    content_len=Integer.parseInt(clen);
		}
	    }
	}
	if (!tempssl) {
	    buf=getAdditional(str, content_len);
	    str = updateBuffer(str, buf);
	    if (buf==null)
		return null;
	    rq+=buf;
	}
	ssl=tempssl;
	return rq;
    }


    /**
     * reads a line
     * @exception IOException
     */
    public static String getLine(String str) {
	int l=str.indexOf('\n');
	if (l!=-1)
	    return str.substring(0, l+1);
	else
	    return null;
    }
    
    public static String getAdditional(String str, int content_len) {
	if (content_len>str.length())
	    return null;
	else
	    return str.substring(0, content_len);

    }

    public static String updateBuffer(String buf, String str) {
	if (str!=null) {
	    return buf.substring(str.length(), buf.length());
	} else
	    return buf;
    }

	/**
        * @return boolean whether the actual connection was established with the CONNECT method.
	* @since 0.2.21
	*/
	public boolean isTunnel() {
          return ssl;
	}
}
