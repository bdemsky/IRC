public class ServerSocket {
    /* Socket pending flag */
    flag SocketPending;    
    /* File Descriptor */
    int fd;

    private native int createSocket(int port);

    public ServerSocket(int port) {
	this.fd=createSocket(port);
    }
    
    public Socket accept() {
	Socket s=new Socket();
	int newfd=nativeaccept(s, fd);
	s.setFD(newfd);
	return s;
    }

    private static native int nativeaccept(Socket s,int fd);
    
    public void close();

}
