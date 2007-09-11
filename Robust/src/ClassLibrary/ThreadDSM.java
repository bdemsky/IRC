public class Thread {

	public void start(int mid) {
		remotethreadstart(mid);
	}

	public native static void sleep(long millis);

	public void run() {

	}
	
	public  native void remotethreadstart(int mid);
}
