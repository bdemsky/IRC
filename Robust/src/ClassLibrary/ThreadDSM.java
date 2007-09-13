public class Thread {
    /* Don't allow overriding this method.  If you do, it will break dispatch
     * because we don't have the type information necessary. */

    public final native void start(int mid);

    public native static void sleep(long millis);
    
    public void run() {
    }
}
