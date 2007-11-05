public class Socket {
    /* File Descriptor */
    int fd;
    SocketInputStream sin;
    SocketOutputStream sout;

    public Socket() {
	sin=new SocketInputStream(this);
	sout=new SocketOutputStream(this);
    }

    public InputStream getInputStream() {
	return sin;
    }

    public OutputStream getOutputStream() {
	return sout;
    }

    public Socket(String host, int port) {
	InetAddress address=InetAddress.getByName(host);
	fd=nativeBind(address.getAddress(), port);
	nativeConnect(fd, address.getAddress(), port);
	sin=new SocketInputStream(this);
	sout=new SocketOutputStream(this);
    }
    
    public Socket(InetAddress address, int port) {
	fd=nativeBind(address.getAddress(), port);
	nativeConnect(fd, address.getAddress(), port);
	sin=new SocketInputStream(this);
	sout=new SocketOutputStream(this);
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
	nativeWrite(b, 0, b.length);
    }

    public void write(byte[] b, int offset, int len) {
	nativeWrite(b, offset, len);
    }

    private native int nativeRead(byte[] b);
    private native void nativeWrite(byte[] b, int offset, int len);
    private native void nativeClose();

    public void close() {
	nativeClose();
    }
}
