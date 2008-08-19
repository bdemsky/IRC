public class OutputStreamWriter extends Writer {
  OutputStream fos;
  public OutputStreamWriter(OutputStream fos) {
    this.fos=fos;
  }

  public void write(String s) {
    fos.write(s.getBytes());
  }

  public void flush() {
    fos.flush();
  }
}
