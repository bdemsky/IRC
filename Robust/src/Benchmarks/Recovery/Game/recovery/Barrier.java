public class Barrier extends Thread {
  threadinfo[] tinfo;
  int numthreads;
  GameMap[][] land;
  int maxage;
  int rows;
  int cols;

  public Barrier(int n, threadinfo[] tinfo, GameMap[][] land, int maxage, int rows, int cols) {
    this.land=land;
    this.maxage=maxage;
    this.rows=rows;
    this.cols=cols;
    this.numthreads=n;
    this.tinfo=tinfo;
    /*
    this.tinfo=global new threadinfo[n];
    for(int i=0; i<n; i++) {
      if (tinfo[i]==null)
        tinfo[threadid]=global new threadinfo();
    }
    */
  }

  /**
   ** Update the age of all trees in a given map
   ** @param land The map to be searched
   ** @param maxage The maxage of a tree
   ** @param rows The number of rows in the map
   ** @param cols The number of columns in the map
   **/
  public void updateAge() {
    int countTrees = 0;
    //System.out.println("updateAge -> maxAge : "+maxage + " rows : " + rows + " cols : "+ cols);
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
  }

  public static void enterBarrier(Barrier b, int threadid, int iteration) {
    threadinfo tinfo[];
    int numthreads;
    int x;
    atomic {
      /* This transaction updates the counter...we really want threadinfo objects to be local, so we allocate the first time through */
      tinfo=b.tinfo;
    numthreads=b.numthreads;
    x = (++tinfo[threadid].counter);
    tinfo[threadid].status=0;
    }

    boolean cont=true;
    int count=0;

    /* Here we see if we are the first non-failed machine...if so, we operate the barrier...if not we wait for our signal */
    while(cont&&count!=threadid) {
      if (Thread.getStatus(count)==-1) {
        count++;
      } else {
        atomic {
          if (tinfo[threadid].status==1)
            cont=false;
        }
      }
    }

    if (count==threadid) {
      /* We are the first non-failed machine...*/
      int waitingon=numthreads-threadid-1;
      boolean waiting[]=new boolean[waitingon];

      while(waitingon>0) {
        //we are doing the barrier
        for(int i=threadid+1; i<numthreads; i++) {
          if (!waiting[i-threadid-1]) {
            atomic {
              if(tinfo[i]!=null && tinfo[i].counter == tinfo[threadid].counter) {
                //this one is done
                waitingon--;
                waiting[i-threadid-1]=true;
              } else if (Thread.getStatus(i)==-1) {
                waitingon--;
                waiting[i-threadid-1]=true;
              }
            }
          }
        }
        if (waitingon>0) //small sleep here
          sleep(100);
      }
      //everyone has reached barrier
      atomic {
        //update map
        if((iteration&15) == 0) {
          b.updateAge();
        }
      }
      //think a single transaction is fine here....
      for(int i=threadid+1; i<numthreads; i++) {
        atomic {
          if (tinfo[i]!=null)
            tinfo[i].status=1;
        }
      }
    }
  }
}

public class threadinfo {
  int counter;
  int status;
  public threadinfo() {
    counter = 0;
    status = 0;
  }
}
