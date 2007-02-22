public class ChatServer {

    public static int main(String arg[]) {
	System.printString("Chat Server Benchmark");
	RoomObject ro=new RoomObject();
	ServerSocket ss=new ServerSocket(8000);
	acceptConnection(ss,ro);
    }

    public static void acceptConnection(ServerSocket ss, RoomObject ro) {
	while(true) {
	    Socket s=ss.accept();
	    ChatThread cs=new ChatThread(s, ro);
	    cs.start();
	}
    }
}
