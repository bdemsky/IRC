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
    barr = new Barrier("128.195.136.162");
    //Create array objects locally
    atomic { //Remote machine aborts come from here
      for(int i=lower; i<nthreads*nthreads; i=i+nthreads) {
        mywa[i] = global new WriteArrayObj();
        //System.printString("Creating " + i + " element\n");
      }
    }

    Barrier.enterBarrier(barr);
    //System.clearPrefetchCache(); //without invalidation it always reads an old array object therefore we need a clearCache
    //Write into array elements
    Integer val;
     for(int j=0; j<10000; j++) {
      atomic {
        val = global new Integer(10);
        for(int i=start;i<start+nthreads; i++) {
        //System.printString("Reading element " + i + "\n");
          mywa[i].val = val.intValue();
        }
      }
    }
  }

  public static void main(String[] args) {
    int nthreads;
    BarrierServer mybarr;
    if(args.length>0) {
      nthreads = Integer.parseInt(args[0]);
    }

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc-1.calit2
	mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc-2.calit2
	mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3.calit2
	mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc-4.calit2
	mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc-5.calit2
	mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc-6.calit2
	mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc-7.calit2
	mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc-8.calit2
 

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
