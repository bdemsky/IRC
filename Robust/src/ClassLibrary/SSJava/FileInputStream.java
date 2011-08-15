public class FileInputStream extends InputStream {
  private int fd;

  public FileInputStream(String pathname) {
    fd = nativeOpen(pathname.getBytes());
  }

  public FileInputStream(File path) {
    fd = nativeOpen(path.getPath().getBytes());
  }

  public int getfd() {
    return fd;
  }

  private static native int nativeOpen(byte[] filename);

  private static native int nativeRead(int fd, byte[] array, int numBytes);

  private static native int nativePeek(int fd);

  private static native void nativeClose(int fd);

  private static native int nativeAvailable(int fd);

  public int read() {
    byte b[] = new byte[1];
    int retval = read(b);
    if (retval == -1 || retval == 0)
      return -1;

    // if carriage return comes back, dump it
    if (b[0] == 13) {
      return read();
    }

    // otherwise return result
    return b[0];
  }

  public int peek() {
    return nativePeek(fd);
  }

  public int read(byte[] b, int offset, int len) {
    if (offset < 0 || len < 0 || offset + len > b.length) {
      return -1;
    }
    byte readbuf[] = new byte[len];
    int rtr = nativeRead(fd, readbuf, len);
    for (int i = offset; i < len + offset; i++) {
      b[i] = readbuf[i - offset];
    }
    return rtr;
  }

  public int read(byte[] b) {
    return nativeRead(fd, b, b.length);
  }

  public String readLine() {
    String line = "";
    int c = read();

    // if we're already at the end of the file
    // or there is an error, don't even return
    // the empty string
    if (c <= 0) {
      return null;
    }

    // ASCII 13 is carriage return, check for that also
    while (c != '\n' && c != 13 && c > 0) {
      line += (char) c;
      c = read();
    }

    // peek and consume characters that are carriage
    // returns or line feeds so the whole line is read
    // and returned, and none of the line-ending chars
    c = peek();
    while (c == '\n' || c == 13) {
      c = read();
      c = peek();
    }

    return line;
  }

  public void close() {
    nativeClose(fd);
  }

  public int available() {
    return nativeAvailable(fd);
  }
}
