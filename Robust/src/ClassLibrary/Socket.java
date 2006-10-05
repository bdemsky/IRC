public class Socket {
    /* Data pending flag */
    external flag IOPending;    
    /* File Descriptor */
    int fd;
    
    Socket() {
    }
    
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
