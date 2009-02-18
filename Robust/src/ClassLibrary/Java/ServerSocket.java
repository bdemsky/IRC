public class ServerSocket {
  /* File Descriptor */
  int fd;

  private native int createSocket(int port);

  public ServerSocket(int port) {
    this.fd=createSocket(port);
  }

  public Socket accept() {
    Socket s=new Socket();
    int newfd=nativeaccept(s);
    s.setFD(newfd);
    return s;
  }

  /* Lets caller pass in their own Socket object. */
  public void accept(Socket s) {
    int newfd=nativeaccept(s);
    s.setFD(newfd);
  }

  private native int nativeaccept(Socket s);

  public void close();

}
