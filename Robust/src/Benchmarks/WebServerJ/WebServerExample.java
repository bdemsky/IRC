import java.net.*;

public class WebServerExample {

    public static void main(String arg[]) {
	// Create New ServerSocket
	//	System.printString("W> Starting\n");
	ServerSocket ss=null;
	try {
	    ss= new ServerSocket(9000);
	} catch (Exception e){}
	//	System.printString("W> Creating ServerSocket\n");
	Logger log = new Logger();
	Inventory inventorylist = new Inventory();
	acceptConnection(ss, log, inventorylist);
    }

    //Listen for a request and accept request 
    public static void acceptConnection(ServerSocket ss, Logger log, Inventory inventorylist) {
	//	System.printString("W> Waiting for connection...\n");
	while(true) {
	    Socket s=null;
	    try {
		s=ss.accept();
	    } catch (Exception e) {}
	    WebServerThread web = new WebServerThread(s, log, inventorylist);
	    web.start();
	    //	System.printString("W> Connected... \n");
	}
    }
}
