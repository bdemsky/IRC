/* Written and copyright 2001-2003 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 */

//import java.awt.Dimension;
//import java.awt.Toolkit;
//import javax.swing.UIManager;

//import Jhttpp2MainFrame;
/**
 * Title:        jHTTPp2: Java HTTP Filter Proxy
 * Description: starts thwe Swing GUI or the console-mode only proxy
 * Copyright:    Copyright (c) 2001-2003
 *
 * @author Benjamin Kohl
 */

public class Jhttpp2Launcher {

    public static void main(String[] args) {
	Jhttpp2Server server = new Jhttpp2Server(true);
    	if (server.error) {
	    System.printString("Error: " + server.error_msg);
	}
    	else {
	    server.start();
	    System.printString("Running on port " + server.port+"\n");
    	}
    }
}
