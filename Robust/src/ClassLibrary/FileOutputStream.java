public class FileOutputStream {
    private int fd;

    public FileOutputStream(String pathname) {
	fd=nativeOpen(pathname.getBytes());
    }

    public FileOutputStream(String pathname, int mode) {
	if(mode==0)	
		fd=nativeAppend(pathname.getBytes());
    	if(mode==1)
		fd=nativeOpen(pathname.getBytes());
    }


    public FileOutputStream(File path) {
	fd=nativeOpen(path.getPath().getBytes());
    }

    private static native int nativeOpen(byte[] filename);
    private static native int nativeAppend(byte[] filename);
    private static native void nativeWrite(int fd, byte[] array);
    private static native void nativeClose(int fd);
    
    public void write(int ch) {
	byte b[]=new byte[1];
	b[0]=(byte)ch;
	write(b);
    }

    public void write(byte[] b) {
	nativeWrite(fd, b);
    }

    public void close() {
	nativeClose(fd);
    }
}
