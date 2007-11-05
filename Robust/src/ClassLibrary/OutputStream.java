public class OutputStream {
    public OutputStream() {
    }

    public void write(int ch) {
	System.printString("Called unimplemented write(int)\n");
    }

    public void write(byte[] b) {
	System.printString("Called unimplemented write(byte[])\n");
    }

    public void write(byte[] b, int off, int len) {
	System.printString("Called unimplemented write(byte[],int,int)\n");
    }

    public void flush() {
    }
    
    public void close() {
	System.printString("Called unimplemented close()\n");
    }
}
