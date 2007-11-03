public class BufferedWriter extends Writer {
    Writer out;

    public void write(String s) {
	out.write(s);
    }

    public void newLine() {
	out.write("\n");
    }

    public void flush() {
	out.flush();
    }

}
