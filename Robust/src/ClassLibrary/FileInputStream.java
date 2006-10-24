public class FileInputStream {
    private int fd;

    public FileInputStream(String pathname) {
	fd=nativeOpen(pathname.getBytes());
    }

    private static native int nativeOpen(byte[] filename);
    private static native int nativeRead(int fd, byte[] array, int numBytes);
    private static native void nativeClose(int fd);
    
    public int read() {
	byte b[]=new byte[1];
	int retval=read(b);
	if (retval==-1)
	    return -1;
	return b[0];
    }

    public int read(byte[] b) {
	return nativeRead(fd, b, b.length);
    }

    public void close() {
	nativeClose(fd);
    }
}
