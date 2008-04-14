public class Thread {
    private int threadid;

    public void start() {
	nativeCreate();
    }

    private static void staticStart(Thread t) {
	t.run();
    }

    public void join() {
    }

    private native void nativeJoin();

    public native static void sleep(long millis);
    
    public void run() {}

    private native void nativeCreate();

}
