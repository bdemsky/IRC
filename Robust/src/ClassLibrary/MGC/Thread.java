public class Thread implements Runnable {
  private boolean finished;
  Runnable target;
  private boolean daemon;
  
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
    t.finished = true;
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
    this.finished = true;
  }

  private native void nativeCreate();
  
  public final boolean isAlive() {
    return !this.finished;
  }
  
  public native ThreadLocalMap getThreadLocals();
  
  public final synchronized void setDaemon(boolean daemon) {
    /*if (vmThread != null)
      throw new IllegalThreadStateException();
    checkAccess();*/
    this.daemon = daemon;
  }

}
