public class Writer {

    public void write(String s) {
    }

    public void write(String s, int off, int len) {
	write(s.substring(off, off+len));
    }

    public void flush() {
    }
}
