public class Socket {
    /* Data pending flag */
    flag IOPending;    
    /* File Descriptor */
    int fd;
    
    Socket() {
    }
    
    int setFD(int filed) {
	fd=filed;
    }

    public int read(byte[] b) {
	return nativeRead(b, fd);
    }
    public void write(byte[] b) {
	nativeWrite(b, fd);
    }

    private native static int nativeRead(byte[] b, int fd);
    private native static void nativeWrite(byte[] b, int fd);
    private native static void nativeClose(int fd);

    public void close() {
	nativeClose(fd);
    }
}
