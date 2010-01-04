import java.io.*;

public class Series {
  PrintWriter out;
  public Series(PrintWriter out) {
    this.out=out;
  }

  public void addPoint(long x, int y) {
    addPoint(Long.toString(x), Integer.toString(y));
  }

  public void addPoint(int x, long y) {
    addPoint(Integer.toString(x), Long.toString(y));
  }

  public void addPoint(String time, String value) {
    out.println(time+" "+value);
  }

  public void close() {
    out.close();
  }
}