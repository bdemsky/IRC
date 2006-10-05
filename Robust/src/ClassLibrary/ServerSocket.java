public class ServerSocket {
    /* Socket pending flag */
    external flag SocketPending;    
    /* File Descriptor */
    int fd;

    private native int createSocket(int port);

    public ServerSocket(int port) {
	this.fd=createSocket(port);
    }
    
    public Socket accept() {
	Socket s=new Socket();
	int newfd=nativeaccept(s);
	s.setFD(newfd);
	return s;
    }

    private native int nativeaccept(Socket s);
    
    public void close();

}
