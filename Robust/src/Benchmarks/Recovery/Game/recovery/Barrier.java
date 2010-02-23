public class Barrier extends Thread {
  threadinfo[] tinfo;
  int numthreads;

  public Barrier(int n, threadinfo[] tinfo) {
    numthreads=n;
    this.tinfo=tinfo;
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
//    System.println("Tree count=  "+countTrees);
  }

  public static void enterBarrier(int threadid, threadinfo[] tinfo, int numthreads) {
    int x;
    atomic {
      tinfo[threadid].counter++;
      x = tinfo[threadid].counter;
    }

    for(int i=0; i<numthreads; i++) {
      if(threadid == i) {
        continue;
      }
      boolean check = false;
      atomic {
        if(tinfo[i].status != -1) {
          if(tinfo[i].counter >= tinfo[threadid].counter)  {
            check = true;
          }
        } else {
          check = true;
        }
      }
      if(!check) {
        int status = Thread.getStatus(i);
        if(status==-1) {//Thread is dead
          atomic {
            tinfo[i].status = -1;
          }
          //System.out.println("DEBUG -> Dead\n");
          continue;
        }
        int y;
        atomic {
          y=tinfo[i].counter;
        }

        //System.out.println("i= " + i + " i's count= " + y + " threadid= " + threadid + " mycount= " + x);

        while(y!=x && (Thread.getStatus(i) != -1)) {
          //Wait for 100 microseconds
          sleep(100);
          atomic {
            y = tinfo[i].counter;
          }
        }
      }
    }
    return;
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

