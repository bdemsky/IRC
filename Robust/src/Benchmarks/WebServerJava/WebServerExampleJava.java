public class WebServerExample {

    public static int main(String arg[]) {
	// Create New ServerSocket
	//	System.printString("W> Starting\n");
	ServerSocket ss = new ServerSocket(9000);
	//	System.printString("W> Creating ServerSocket\n");
	Logger log = new Logger();
	Inventory inventorylist = new Inventory();
	acceptConnection(ss, log, inventorylist);
    }

    //Listen for a request and accept request 
    public static void acceptConnection(ServerSocket ss, Logger log, Inventory inventorylist) {
	//	System.printString("W> Waiting for connection...\n");
	while(true) {
	    Socket s=ss.accept();
	    WebServerThread web = new WebServerThread(s, log, inventorylist);
	    web.start();
	    //	System.printString("W> Connected... \n");
	}
    }
}
