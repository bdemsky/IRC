public class RemoteThread extends Thread {
	public RemoteThread() {
	}

	public static void main(String[] st) {
		int mid = 127;
		RemoteThread t =null;
		atomic {
		    t= global new RemoteThread();
		}
		t.start(mid);
	}
}

