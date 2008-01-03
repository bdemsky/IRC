public class Atomic2 extends Thread {
    public Atomic2() {
    }
    int count;
    public static void main(String[] st) {
	//int mid = (128<<24)|(200<<16)|(9<<8)|26;
	int mid = (128<<24)|(195<<16)|(175<<8)|70;
	Atomic2 t =null;
	atomic {
	    t= global new Atomic2();
	    t.count=0;
	}
	System.printString("Starting\n");
	t.start(mid);
	System.printString("Finished\n");
	/* Test thread join */
	t.join();
	//this is ugly...
	while(true) {
	    atomic {
		t.count++;
	    }
	}
    }

    public int run() {
	while(true) {
	    int tmpcount;
	    atomic {
		tmpcount=count;
	    }
	    System.printString("Current count=");
	    System.printInt(tmpcount);
	    System.printString("\n");
	}
    }
}

