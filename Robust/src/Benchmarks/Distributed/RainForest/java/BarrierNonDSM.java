public class BarrierServer extends Thread {
  int numthreads;
  boolean done;

  public BarrierServer(int n) {
    numthreads=n;
    done=false;
  }

  public void run() {
    int n;
    ServerSocket ss=new ServerSocket(2000);
    n=numthreads;
    done=true;
    Socket ar[]=new Socket[n];
    for(int i=0; i<n; i++) {
      ar[i]=ss.accept();
    }

    while(true) {
      for(int j=0; j<n; j++) {
        Socket s=ar[j];
        byte b[]=new byte[1];
        while(s.read(b)!=1) {
          ;
        }
      }
      byte b[]=new byte[1];
      b[0]= (byte) 'A';
      for(int j=0; j<n; j++)
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
    b[0]=(byte)'A';
    barr.s.write(b);
    while(barr.s.read(b)!=1) {
      ;
    }
  }
}
