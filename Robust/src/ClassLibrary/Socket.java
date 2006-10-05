public class Socket {
    /* Data pending flag */
    flag IOPending;    
    /* File Descriptor */
    int fd;
    
    private Socket(int fd) {
	this.fd=fd;
    }
    
    public int read(byte[] b);
    public void write(byte[] b);
    void close();
}
