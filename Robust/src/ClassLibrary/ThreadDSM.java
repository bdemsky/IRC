public class ThreadDSM {

	public void start(int mid) {
		run(mid);
	}

	public native static void sleep(long millis);

	public void run(int mid) {

	}
	
	public int startRemoteThread(int mid);
}
