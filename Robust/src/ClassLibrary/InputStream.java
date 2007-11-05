public class InputStream {
    public int read() {
	System.printString("called unimplemented read\n");
    }
    public int read(byte[] b) {
	System.printString("called unimplemented read(byte[]b)\n");
    }

    public void close() {
	System.printString("Called unimplemented close()\n");
    }
}
