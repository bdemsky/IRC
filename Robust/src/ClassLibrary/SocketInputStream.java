public class SocketInputStream extends InputStream {
  Socket s;
  public SocketInputStream(Socket s) {
    this.s=s;
  }

  public int read() {
    byte[] x=new byte[1];
    int len=s.read(x);
    if (len<=0)
      return -1;
    else return x[0];
  }

  public int read(byte[] b) {
    return s.read(b);
  }

  public void close() {
    s.close();
  }
}
