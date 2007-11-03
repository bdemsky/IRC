public class BufferedInputStream extends InputStream {
    InputStream in;
    public BufferedInputStream(InputStream in) {
	this.in=in;
    }
    public int read() {
	return in.read();
    }

    public int read(byte[] b) {
	return in.read(b);
    }
}
