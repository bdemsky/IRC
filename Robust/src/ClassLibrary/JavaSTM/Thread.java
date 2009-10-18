public class Thread {
  private boolean finished;

  public void start() {
    nativeCreate();
  }

  public static native void abort();

  private static void staticStart(Thread t) {
    t.run();
  }

  public static native void yield();

  public void join() {
    nativeJoin();
  }

  private native void nativeJoin();

  public native static void sleep(long millis);

  public void run() {
  }

  private native void nativeCreate();

}
