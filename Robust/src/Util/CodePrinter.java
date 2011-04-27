package Util;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.OutputStream;
import java.io.File;

public class CodePrinter extends PrintWriter {
  int braceCount=0;
  boolean seenChar=false;
  StringBuffer sb=new StringBuffer();
  public CodePrinter(Writer w) {
    super(w);
  }

  public CodePrinter(Writer w, boolean af) {
    super(w,af);
  }

  public CodePrinter(File w) throws java.io.FileNotFoundException {
    super(w);
  }

  public CodePrinter(OutputStream w) {
    super(w);
  }

  public CodePrinter(OutputStream w, boolean af) {
    super(w,af);
  }

  StringBuffer genSpacing() {
    StringBuffer sb=new StringBuffer();
    for(int i=0; i<braceCount; i++)
      sb.append("  ");
    return sb;
  }

  public void println() {
    addString("\n");
  }

  public void println(boolean x) {
    addString(x+"\n");
  }

  public void println(char x) {
    addString(x+"\n");
  }

  public void println(char[] x) {
    addString(x+"\n");
  }

  public void println(double x) {
    addString(x+"\n");
  }

  public void println(float x) {
    addString(x+"\n");
  }

  public void println(int x) {
    addString(x+"\n");
  }

  public void println(Object x) {
    addString(x+"\n");
  }

  public void println(String x) {
    addString(x+"\n");
  }

  public void print(boolean x) {
    addString(x+"\n");
  }

  public void print(char x) {
    addString(String.valueOf(x));
  }

  public void print(char[] x) {
    addString(String.valueOf(x));
  }

  public void print(double x) {
    addString(String.valueOf(x));
  }

  public void print(float x) {
    addString(String.valueOf(x));
  }

  public void print(int x) {
    addString(String.valueOf(x));
  }

  public void print(Object x) {
    addString(String.valueOf(x));
  }

  public void print(String x) {
    addString(x);
  }

  public void write(String x) {
    addString(x);
  }

  public void write(char[] x) {
    addString(new String(x));
  }

  public void addString(String str) {
    char[] string = str.toCharArray();
    int lastcr = 0;

    for (int i = 0; i < string.length; i++) {
      char c = string[i];

      switch (c) {
      case '\n': {
        // get the cr
        sb.append(string, lastcr, (i - lastcr) + 1);
        super.write(sb.toString());
        sb = genSpacing();
        lastcr = i + 1; // skip carriage return
        seenChar = false;
        break;
      }

      case '{':
        braceCount++;
        seenChar = true;
        break;

      case '}':
        braceCount--;
        // fix up close brace...
        if (!seenChar)
          sb = genSpacing();
        seenChar = true;
        break;

      case ' ':
        // skip leading whitespace
        if (!seenChar)
          lastcr = i + 1;
        break;

      default:
        seenChar = true;
      }
    }
    if (lastcr < string.length) {
      // dump string
      sb.append(string, lastcr, string.length - lastcr);
    }
  }

  public void flush() {
    super.write(sb.toString());
    sb=genSpacing();
    super.flush();
  }

  public void close() {
    super.write(sb.toString());
    super.close();
  }
}