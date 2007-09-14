public class RemoteThread extends Thread {
    public RemoteThread() {
    }
    
    public static void main(String[] st) {
	int mid = (128<<24)|(200<<16)|(9<<8)|26;
	RemoteThread t =null;
	atomic {
	    t= global new RemoteThread();
	}
	System.printString("Starting\n");
	t.start(mid);
	System.printString("Finished\n");
	//this is ugly...
	while(true) ;
    }

    public int run() {
	System.printString("Remote machine\n");
    }
}

