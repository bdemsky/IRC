public class Writer {
    public void write(String s) {
	System.printString("Unimplemented write(String) in Writer\n");
    }

    public void write(String s, int off, int len) {
	write(s.substring(off, off+len));
    }

    public void flush() {
	System.printString("Unimplemented flush in Writer\n"); 
   }

    public void close() {
	System.printString("Unimplemented close in Writer\n");
    }
}
