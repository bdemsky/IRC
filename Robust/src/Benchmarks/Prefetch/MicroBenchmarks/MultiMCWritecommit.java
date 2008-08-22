public class WriteArrayObj extends Thread {
  WriteArrayObj[] mywa;
  int nthreads;
  int val, lower, start;

  public WriteArrayObj() {
  }

  public WriteArrayObj(int lower, WriteArrayObj[] wa, int nthreads, int start) {
    this.lower = lower;
    mywa = wa;
    this.nthreads = nthreads;
    this.start = start;
  }

  public void run() {
    Barrier barr;
    barr = new Barrier("128.195.175.84");
    //Create array objects locally
    atomic { //Remote machine aborts come from here
      for(int i=lower; i<nthreads*nthreads; i=i+nthreads) {
        mywa[i] = global new WriteArrayObj();
      }
    }

    Barrier.enterBarrier(barr);
/*
    //Write into array elements
    Integer val;
    for(int j=0; j<10000; j++) {
      atomic {
        val = global new Integer(10);
        for(int i=start;i<start+nthreads; i++) {
          mywa[i].val = val.intValue();
        }
      }
    }
    */
  }

  public static void main(String[] args) {
    int nthreads;
    BarrierServer mybarr;
    if(args.length>0) {
      nthreads = Integer.parseInt(args[0]);
    }

    int[] mid = new int[5];
    mid[0] = (128<<24)|(195<<16)|(175<<8)|84;//dw-10
    mid[1] = (128<<24)|(195<<16)|(175<<8)|85;//dw-11
    mid[2] = (128<<24)|(195<<16)|(175<<8)|86;//dw-12
    mid[3] = (128<<24)|(195<<16)|(175<<8)|87;//dw-13
    mid[4] = (128<<24)|(195<<16)|(175<<8)|88;//dw-14

    WriteArrayObj[] wao;
    atomic {
      wao=global new WriteArrayObj[nthreads*nthreads]; //create array of objects
      mybarr = global new BarrierServer(nthreads);
    }
    mybarr.start(mid[0]);

    WriteArrayObjWrap[] wawrap = new WriteArrayObjWrap[nthreads];

    atomic {
      for(int i=0;i<nthreads; i++) {
        int start = i * nthreads; 
         wawrap[i] = new WriteArrayObjWrap(global new WriteArrayObj(i,wao, nthreads,start));
      }
    }

    boolean waitfordone=true;
    while(waitfordone) {
      atomic { //Master aborts come from here
        if (mybarr.done)
          waitfordone=false;
      }
    }

    for(int i =0; i<nthreads; i++)
      wawrap[i].wa.start(mid[i]);

    for(int i =0; i<nthreads; i++)
      wawrap[i].wa.join();

	System.printString("Finished\n");
  }
}
