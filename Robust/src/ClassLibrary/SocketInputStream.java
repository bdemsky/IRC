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

  public int readAll(byte[] b) {
      int offset=read(b);
      if (offset<0)
	  return offset;
      int toread=b.length-offset;
      while(toread>0) {
	  byte[] t=new byte[toread];
	  int rd=read(t);
	  if (rd<0)
	      return rd;
	  for(int i=0;i<rd;i++)
	      b[i+offset]=t[i];
	  offset+=rd;
	  toread-=rd;
      }
      return b.length;
  }

  public void close() {
    s.close();
  }
}
