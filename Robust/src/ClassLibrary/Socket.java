public class Socket {
    /* Data pending flag */
    external flag IOPending;    
    /* File Descriptor */
    int fd;
    
    public Socket() {
    }

    public Socket(String host, int port) {
	InetAddress address=InetAddress.getByName(host);
	fd=nativeBind(address.getAddress(), port);
	nativeConnect(fd, address.getAddress(), port);
    }
    
    public Socket(InetAddress address, int port) {
	fd=nativeBind(address.getAddress(), port);
	nativeConnect(fd, address.getAddress(), port);
    }

    public static native int nativeBind(byte[] address, int port);

    public static native int nativeConnect(int fd, byte[] address, int port);
    
    int setFD(int filed) {
	fd=filed;
    }

    public int read(byte[] b) {
	return nativeRead(b);
    }
    public void write(byte[] b) {
	nativeWrite(b);
    }

    private native int nativeRead(byte[] b);
    private native void nativeWrite(byte[] b);
    private native void nativeClose();

    public void close() {
	nativeClose();
    }
}
