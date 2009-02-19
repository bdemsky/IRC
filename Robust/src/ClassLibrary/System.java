public class System {
  public static void printInt(int x) {
    String s=String.valueOf(x);
    printString(s);
  }

  public static native long currentTimeMillis();

  public static native void printString(String s);

  public static void println(String s) {
    System.printString(s);
    System.printString("\n");
  }

  public static void print(String s) {
    System.printString(s);
  }

  public static void error() {
    System.printString("Error (Use Breakpoint on ___System______error method for more information!)\n");
  }

  public static native void exit(int status);

  public static native void printI(int status);

  public static native void clearPrefetchCache();

  public static native void rangePrefetch(Object o, short[] offsets);

}
