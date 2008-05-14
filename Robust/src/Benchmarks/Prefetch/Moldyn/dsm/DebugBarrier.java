public class Barrier {
  int numthreads;
  int entercount;
  boolean cleared;

  public Barrier(int n) {
    //System.printString("Initializing barrier for "+n+" threads.\n");
    numthreads=n;
    cleared = false;
    entercount = 0;
  }

  public Barrier() {
  }

  public void reset() {
    cleared = false;
    entercount = 0;
  }

  public static void enterBarrier(Barrier b) {
    int tmp;
    boolean retry=true;

    do {
      atomic {
        //System.printString("Entering barrier with ec="+b.entercount+" cl="+b.cleared+"\n");
        if (!b.cleared) {
          b.entercount++;
          tmp = b.entercount;
          if (tmp==b.numthreads) {
            if(b.numthreads > 1)
              b.cleared=true;
            b.entercount--;
            //System.printString("Exiting Barrier #1\n");
            return;
          }
          retry=false;
        }
      }
    } while(retry);
    //System.printString("Waiting for last thread to enter\n");
    while(true) {
      atomic {
        if (b.cleared) {
          b.entercount--;
          int count = b.entercount;
          if (count==0)
            b.cleared=false;
          //System.printString("Exiting Barrier #2\n");
          return;
        }
      }
    }
  }
}
