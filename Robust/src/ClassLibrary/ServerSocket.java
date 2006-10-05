public class ServerSocket {
    /* Socket pending flag */
    flag SocketPending;    
    /* File Descriptor */
    int fd;

    private native int createSocket(int port);

    public ServerSocket(int port) {
	this.fd=createSocket(port);
    }
    
    public Socket accept();
    public void close();

}
