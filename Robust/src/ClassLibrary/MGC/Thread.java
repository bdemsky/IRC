public class Thread implements Runnable {
  private boolean finished;
  Runnable target;
  
  public Thread(){
    finished = false;
    target = null;
  }
  
  public Thread(Runnable r) {
    finished = false;
    target = r;
  }

  public void start() {
    nativeCreate();
  }

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
    if(target != null) {
      target.run();
    }
  }

  private native void nativeCreate();

}
