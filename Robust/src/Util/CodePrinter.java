package Util;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.OutputStream;

public class CodePrinter extends PrintWriter {
  int braceCount=0;
  StringBuffer sb=new StringBuffer();
  public CodePrinter(Writer w) {
    super(w);
  }

  public CodePrinter(Writer w, boolean af) {
    super(w,af);
  }

  public CodePrinter(OutputStream w) {
    super(w);
  }

  public CodePrinter(OutputStream w, boolean af) {
    super(w,af);
  }
  
  StringBuffer genSpacing() {
    StringBuffer sb=new StringBuffer();
    for(int i=0;i<braceCount;i++)
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
    char[] string=str.toCharArray();
    int lastcr=0;
    boolean seenChar=false;
    for(int i=0;i<string.length;i++) {
      char c=string[i];
      switch(c) {
      case '\n': {
	sb.append(string, lastcr, i-lastcr);
	super.println(sb.toString());
	sb=genSpacing();
	lastcr=i+1;//skip carriage return
	seenChar=false;
	break;
      }
      case '{':
	braceCount++;
	seenChar=true;
	break;
      case '}':
	braceCount--;
	seenChar=true;
	break;
      case ' ':
	//skip leading whitespace
	if (!seenChar)
	  lastcr=i+1;
	break;
      default:
	seenChar=true;
      }
    }
    if (lastcr!=(string.length-1)) {
      //dump string
      sb.append(string, lastcr, string.length-lastcr);
    }
  }

  public void flush() {
    super.println(sb.toString());
    sb=genSpacing();
  }

  public void close() {
    super.println(sb.toString());
    super.close();
  }
}