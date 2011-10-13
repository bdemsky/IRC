public class Thread implements Runnable {
  static long id = 0;
  private boolean finished;
  Runnable target;
  private boolean daemon;
  private long threadId;
  
  public Thread(){
    finished = false;
    target = null;
    daemon = false;
    threadId = Thread.id++;
  }
  
  public long getId()
  {
    return threadId;
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
  
  public static Thread currentThread()
  {
    System.out.println("Unimplemented Thread.currentThread()!");
    return null;
  }

}
