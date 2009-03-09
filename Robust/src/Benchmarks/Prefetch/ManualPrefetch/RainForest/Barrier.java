public class BarrierServer extends Thread {
  int numthreads;
  boolean done;
  GameMap land;

  public BarrierServer(int n) {
    numthreads=n;
    done=false;
  }

  /**
   ** Update the age of all trees in a given map
   ** @param land The map to be searched
   ** @param maxage The maxage of a tree
   ** @param rows The number of rows in the map
   ** @param cols The number of columns in the map
   **/
  public void updateAge(GameMap[][] land, int maxage, int rows, int cols) {
    int countTrees = 0;
    for(int i = 0; i<rows; i++) {
      for(int j = 0; j<cols; j++) {
        if(land[i][j].tree != null) {
          if(land[i][j].tree.getage() > maxage) {
            land[i][j].tree = null;
          } else {
            land[i][j].tree.incrementage();
          }
          countTrees++;
        }
      }
    }
    /* Debugging-> System.println("Tree count=  "+countTrees); */
  }

  public void run() {
    int n;
    ServerSocket ss=new ServerSocket(2000);
    atomic {
      n=numthreads;
      done=true;
    }
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
