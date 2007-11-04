public class BufferedOutputStream extends OutputStream {
    OutputStream o;

    public BufferedOutputStream(OutputStream o) {
	this.o=o;
    }

    public void write(byte []b, int off, int len) {
	o.write(b, off, len);
    }

    public void flush() {
	o.flush();
    }

    public void close() {
	o.close();
    }
}
