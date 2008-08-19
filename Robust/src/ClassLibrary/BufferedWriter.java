public class BufferedWriter extends Writer {
  Writer out;

  public BufferedWriter(Writer out) {
    this.out=out;
  }

  public void write(String s) {
    out.write(s);
  }

  public void newLine() {
    out.write("\n");
  }

  public void flush() {
    out.flush();
  }

  public void close() {
    out.close();
  }

}
