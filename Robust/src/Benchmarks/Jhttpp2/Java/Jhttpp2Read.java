/* Written and copyright 2001-2003 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
/**
	File: Jhttpp2Read.java
	reads from a Jhttpp2ClientInputStream and writes to the BufferedOutputStream

	@author Benjamin Kohl
*/
public class Jhttpp2Read extends Thread
{
	private final int BUFFER_SIZE;
	private BufferedInputStream in;
	private BufferedOutputStream out;
	private Jhttpp2HTTPSession connection;
	private Jhttpp2Server server;

    public Jhttpp2Read(Jhttpp2Server server,Jhttpp2HTTPSession connection,BufferedInputStream l_in, BufferedOutputStream l_out) {
	BUFFER_SIZE=96000;
	in=l_in;
	out=l_out;
	this.connection=connection;
	this.server=server;
	start();
    }
    public void run() {
	read();
    }
    private void read() {
	int bytes_read=0;
	byte[] buf=new byte[BUFFER_SIZE];
	boolean cnt=true;
	while(cnt) {
	    bytes_read=in.read(buf);
	    if (bytes_read!=-1) {
		out.write(buf,0,bytes_read);
		out.flush();
		server.addBytesRead(bytes_read);
	    } else cnt=false;
	}
	if (connection.getStatus()!=connection.SC_CONNECTING_TO_HOST) // *uaaahhh*: fixes a very strange bug
	    connection.getLocalSocket().close();
        // why? If we are connecting to a new host (and this thread is already running!) , the upstream
        // socket will be closed. So we get here and close our own downstream socket..... and the browser
        // displays an empty page because jhttpp2
        // closes the connection..... so close the downstream socket only when NOT connecting to a new host....
    }
    public void close() {
	in.close();
    }
}


