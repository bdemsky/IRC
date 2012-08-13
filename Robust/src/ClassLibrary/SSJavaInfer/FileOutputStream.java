//import java.io.FileDescriptor;

public class FileOutputStream extends OutputStream {
  private int fd;

  public FileOutputStream(String pathname) {
    fd = nativeOpen(pathname.getBytes());
  }

  public FileOutputStream(String pathname, boolean append) {
    if (append)
      fd = nativeAppend(pathname.getBytes());
    else
      fd = nativeOpen(pathname.getBytes());
  }

  public FileOutputStream(String pathname, int mode) {
    if (mode == 0)
      fd = nativeAppend(pathname.getBytes());
    if (mode == 1)
      fd = nativeOpen(pathname.getBytes());
  }

  public FileOutputStream(File path) {
    fd = nativeOpen(path.getPath().getBytes());
  }

  public FileOutputStreamOpen(String pathname) {
    fd = nativeOpen(pathname.getBytes());
  }

  public FileOutputStream(FileDescriptor fdObj) {
    fd = nativeOpen(fdObj.channel.getBytes());
  }

  private static native int nativeOpen(byte[] filename);

  private static native int nativeAppend(byte[] filename);

  private static native void nativeWrite(int fd, byte[] array, int off, int len);

  private static native void nativeClose(int fd);

  private static native void nativeFlush(int fd);

  public void write(int ch) {
    byte b[] = new byte[1];
    b[0] = (byte) ch;
    write(b);
  }

  public void write(byte[] b) {
    nativeWrite(fd, b, 0, b.length);
  }

  public void write(byte[] b, int index, int len) {
    nativeWrite(fd, b, index, len);
  }

  public void flush() {
    nativeFlush(fd);
  }

  public void close() {
    nativeClose(fd);
  }
}
