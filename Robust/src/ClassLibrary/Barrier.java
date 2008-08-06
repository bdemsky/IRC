public class BarrierServer extends Thread {
    int numthreads;
    
    public BarrierServer(int n) {
	numthreads=n;
    }
    
    public run() {
	atomic {
	    n=numthreads;
	}
	ServerSocket ss=new ServerSocket(2000);
	Socket ar[]=new Socket[numthreads];
	for(int i=0;i<numthreads;i++) {
	    ar[i]=ss.accept();
	}
	
	while(true) {
	    for(int j=0;j<numthreads;j++) {
		Socket s=ar[j];
		byte b[]=new byte[1];
		while(s.read(b)!=1)
		    ;
	    }
	    byte b[]=new byte[1];
	    b[0]='A';
	    for(int j=0;j<numthreads;j++)
		ar[j].write(b);
	}
    }
}

public class Barrier {
    Socket s;
    public Barrier(String name) {
	s=new Socket(name, 2000);
    }
    
    public static void enterBarrier(Barrier b) {
	byte b[]=new byte[1];
	s.write(b);
	while(s.read(b)!=1)
	    ;
    }
}