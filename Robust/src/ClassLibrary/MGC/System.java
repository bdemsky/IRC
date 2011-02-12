import java.util.Properties;

public class System {
  PrintStream out;
  
  public System() {
    out = new PrintStream("System.out");
  }
  
  public static void printInt(int x) {
    String s=String.valueOf(x);
    printString(s);
  }

  public static native long currentTimeMillis();
  
  public static native long microTimes();

  public static native long getticks();

  public static native void printString(String s);

  public static void println(String s) {
    System.printString(s+"\n");
  }

  public static void println(Object o) {
    System.printString(""+o+"\n");
  }

  public static void println(int o) {
    System.printString(""+o+"\n");
  }

  public static void println(double o) {
    System.printString(""+o+"\n");
  }

  public static void println(long o) {
    System.printString(""+o+"\n");
  }
  
  public static void println() {
    System.printString("\n");
  }

  public static void print(String s) {
    System.printString(s);
  }

  public static void print(Object o) {
    System.printString(""+o);
  }

  public static void print(int o) {
    System.printString(""+o);
  }

  public static void print(double o) {
    System.printString(""+o);
  }

  public static void print(long o) {
    System.printString(""+o);
  }

  public static void error() {
    System.printString("Error (Use Breakpoint on ___System______error method for more information!)\n");
  }

  public static native void exit(int status);

  public static native void printI(int status);

  public static native void clearPrefetchCache();

  public static native void rangePrefetch(Object o, short[] offsets);

  public static native void deepArrayCopy(Object dst, Object src);

  public static native void Assert(boolean status);

  /* Only used for microbenchmark testing of SingleTM version */
  public static native void logevent(int event);
  public static native void logevent();

  /* Only used for microbenchmark testing of SingleTM version */
  public static native void initLog();

  public static native void flushToFile(int threadid);
  /* Only used for microbenchmark testing of SingleTM version */

  public static native void arraycopy(Object src, int srcPos, Object dst, int destPos, int length);

  // for disjoint reachability analysis
  public static void genReach();
  
  private static Properties props;
  private static native Properties initProperties(Properties props);
  
  public static Properties getProperties() {
    return props;
  }
}
