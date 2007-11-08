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
public class Jhttpp2HTTPSession {
    String request;
    flag more;
    flag first;
    
    public Jhttpp2HTTPSession() {
    }
}

