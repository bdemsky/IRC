public class SocketOutputStream extends OutputStream {
  Socket s;
  public SocketOutputStream(Socket s) {
    this.s=s;
  }

  public void write(byte[] b) {
    s.write(b);
  }

  public void write(int ch) {
    byte[] b=new byte[1];
    b[0]=(byte)ch;
    s.write(b);
  }

  public void write(byte[] b, int offset, int len) {
    s.write(b, offset, len);
  }

  public void close() {
    s.close();
  }
}
