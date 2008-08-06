public class BarrierServer extends Thread {
    int numthreads;
    
    public BarrierServer(int n) {
	numthreads=n;
    }
    
    public run() {
      int n;
	atomic {
	    n=numthreads;
	}
	ServerSocket ss=new ServerSocket(2000);
	Socket ar[]=new Socket[n];
	for(int i=0;i<n;i++) {
	    ar[i]=ss.accept();
	}
	
	while(true) {
	    for(int j=0;j<n;j++) {
		Socket s=ar[j];
		byte b[]=new byte[1];
		while(s.read(b)!=1)
		    ;
	    }
	    byte b[]=new byte[1];
	    b[0]= (byte) 'A';
	    for(int j=0;j<n;j++)
		ar[j].write(b);
	}
    }
}

public class Barrier {
    Socket s;
    public Barrier(String name) {
      s=new Socket(name, 2000);
    }
    
    public static void enterBarrier(Barrier barr) {
      byte b[]=new byte[1];
      s.write(b);
      while(s.read(b)!=1)
        ;
    }
}
