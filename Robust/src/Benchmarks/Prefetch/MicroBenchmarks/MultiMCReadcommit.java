public class ReadArrayObj extends Thread {
  int id, nthreads;
  ReadArrayObj[] myra;
  int val;

  public ReadArrayObj() {
  }

  public ReadArrayObj(int id, int nthreads, ReadArrayObj[] ra) {
    this.id = id;
    this.nthreads = nthreads;
    myra = ra;
  }

  public void run() {
    int tmpid;
    Barrier barr;
    barr = new Barrier("128.195.175.84");
    //Create array objects locally
    atomic {
      tmpid = id;
      myra[tmpid] = global new ReadArrayObj();
      myra[tmpid].val = tmpid*10+1;
    }

    Barrier.enterBarrier(barr);

    //All machines reading data from array
    int val;
    for(int i=0; i<10000; i++) {
      atomic {
        for(int j=0; j<nthreads; j++) {
          val = myra[j].val;
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

    int[] mid = new int[5];
    mid[0] = (128<<24)|(195<<16)|(175<<8)|84;//dw-10
    mid[1] = (128<<24)|(195<<16)|(175<<8)|85;//dw-11
    mid[2] = (128<<24)|(195<<16)|(175<<8)|86;//dw-12
    mid[3] = (128<<24)|(195<<16)|(175<<8)|87;//dw-13
    mid[4] = (128<<24)|(195<<16)|(175<<8)|88;//dw-14

    ReadArrayObj[] a;
    atomic {
      a=global new ReadArrayObj[nthreads]; //create object a
      mybarr = global new BarrierServer(nthreads);
    }

    mybarr.start(mid[0]);

    ReadArrayObjWrap[] ra = new ReadArrayObjWrap[nthreads];

    atomic {
      for(int i=0;i<nthreads; i++) {
        ra[i] = new ReadArrayObjWrap(global new ReadArrayObj(i, nthreads, a));
      }
    }

    boolean waitfordone=true;
    while(waitfordone) {
      atomic {  //Master aborts are from here
        if (mybarr.done)
          waitfordone=false;
      }
    }

    for(int i =0; i<nthreads; i++)
      ra[i].ra.start(mid[i]);

    for(int i =0; i<nthreads; i++)
      ra[i].ra.join();

	System.printString("Finished\n");
  }
}
