public class Thread {

    public void start() {
	nativeCreate();
    }

    private static void staticStart(Thread t) {
	t.run();
    }

    public native static void sleep(long millis);
    
    public void run() {}

    private native void nativeCreate();

}
