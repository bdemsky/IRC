public class FileInputStream extends InputStream {
  private int fd;

  public FileInputStream(String pathname) {
    fd=nativeOpen(pathname.getBytes());
  }

  public FileInputStream(File path) {
    fd=nativeOpen(path.getPath().getBytes());
  }
  public int getfd() {
    return fd;
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

  public String readLine() {
    String line = "";
    int c = read();
    
    // if we're already at the end of the file
    // don't even return the empty string
    if( c == -1 ) { 
      return null;
    }

    while( c != '\n' && c != -1 ) {
      line += (char)c;
      c = read();      
    } 

    return line;
  }

  public void close() {
    nativeClose(fd);
  }
}
