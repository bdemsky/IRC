public class Thread {
  /* Don't allow overriding this method.  If you do, it will break dispatch
   * because we don't have the type information necessary. */
  public boolean threadDone;
  public int mid;

  public Thread() {
    threadDone = false;
  }

  public static native void yield();

  public final native void join();

  public final native void start(int mid);

  public static void myStart(Thread t, int mid)
  {
    atomic  {
      t.mid = mid;
    }
    t.start(mid);
  }

  public native static int nativeGetStatus(int mid);

  public native static void sleep(long millis);

  public void run() {
  }

  public static int getStatus(int mid)
  {
    if(nativeGetStatus(mid)==1)
      return 1;
    //TODO:check if this is safe to add only for the DSM without the recovery version
    if(nativeGetStatus(mid)==0)
      return 1;
    else
      return -1;

  }

  public int getStatus() {
    if(nativeGetStatus(mid)==1)
      return 1;
    //TODO:check if this is safe to add only for the DSM without the recovery version
    if(nativeGetStatus(mid)==0)
      return 1;
    else
      return -1;
  }
}




