public class Barrier {
  int numthreads;
  int entercount;
  boolean cleared;

  public Barrier(int n) {
    numthreads=n;
    cleared = false;
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

    if (b.numthreads == 1) 
      return;

    do {
      if (!b.cleared) {
        b.entercount++;
        tmp = b.entercount;
        if (tmp==b.numthreads) {
          b.cleared=true;
          b.entercount--;
          return;
        }
        retry=false;
      }
    } while(retry);

    while(true) {
      if (b.cleared) {
        b.entercount--;
        int count = b.entercount;
        if (count==0)
          b.cleared=false;
        return;
      }
    }
  }
}
