public class Thread {
    public void start() {
	nativeCreate();
    }

    private static void staticStart(Thread t) {
	t.run();
    }
    
    public void run() {}

    private native void nativeCreate();
}
