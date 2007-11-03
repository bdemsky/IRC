public class SocketInputStream {
    Socket s;
    public SocketInputStream(Socket s) {
	this.s=s;
    }

    public int read() {
	byte[] x=new byte[1];
	int len=s.read(x);
	if (len==0)
	    return -1;
	else return x[1];
    }
}
