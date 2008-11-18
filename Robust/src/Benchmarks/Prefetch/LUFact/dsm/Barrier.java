public class OldBarrier {
  int numthreads;
  int entercount;
  boolean cleared;

  public OldBarrier(int n) {
    numthreads=n;
    cleared = false;
    entercount = 0;
  }

  public OldBarrier() {
  }

  public void reset() {
    cleared = false;
    entercount = 0;
  }

  public static void enterBarrier(OldBarrier b) {
    int tmp;
    boolean retry=true, ret1=false, ret2=true;;

    do {
      atomic {
        if (!b.cleared) {
          b.entercount++;
          tmp = b.entercount;
          if (tmp==b.numthreads) {
            if(b.numthreads > 1)
              b.cleared=true;
            b.entercount--;
            ret1 = true;
          }
          retry=false;
        }
      }
    } while(retry);
    if (ret1) return;
    while(ret2) {
      atomic {
        if (b.cleared) {
          b.entercount--;
          int count = b.entercount;
          if (count==0)
            b.cleared=false;
          ret2=false;
        }
      }
    }
  }
}
