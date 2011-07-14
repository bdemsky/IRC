package Fibheaps;

// the fibheap class
public class TestRunner extends Thread {

  public TestRunner() {}

  public void run() {
    // generate test data
    int iter = 1200; //200;
    int seed = 1967;
    //Vector testdata = new Vector(iter);
    FibHeap fh = new FibHeap();
    FibHeap fh_t = new FibHeap();
    for(int i = 0; i < iter; i++) {
      int rand = (77 * seed + 1) % 1024;
      //testdata.addElement(new Integer(rand));
      seed++;
      fh = fh.insertFH(rand);
      fh_t = fh_t.insertFH(rand);
    }
    // makeFH from the test data
    /*FibHeap fh = new FibHeap();
    for(int i = testdata.size(); i > 0; i++) {
      fh = fh.insertFH((Integer)(testdata.elementAt(i-1)).intValue());
    }
    FibHeap fh_t = new FibHeap();
    for(int i = testdata.size(); i > 0; i++) {
      fh_t = fh_t.insertFH((Integer)(testdata.elementAt(i-1)).intValue());
    }*/

    int[] rfh = new int[iter];
    int[] rfh_t = new int[iter];

    int i = 0;
    while(!fh.isEmpty()) {
      rfh[i] = fh.minFH();
      fh = fh.deleteMinFH();
      i++;
    }
    int j = 0;
    while(!fh_t.isEmpty()) {
      rfh_t[j] = fh_t.minFH();
      fh_t = fh_t.deleteMinFH_t();
      j++;
    }

    if(i != j) {
      // error!
      System.exit(0xaa);
    } else {
      for(i = 0; i < j; i++) {
        if(rfh[i] != rfh_t[i]) {
          // error!
          System.exit(0xbb);
        }
      }
    }
  }

  public static void main(String[] args) {
    int threadnum = 62;
    for(int i = 0; i < threadnum; ++i) {
      TestRunner tr = new TestRunner();
      tr.start();
    }
  }
}
