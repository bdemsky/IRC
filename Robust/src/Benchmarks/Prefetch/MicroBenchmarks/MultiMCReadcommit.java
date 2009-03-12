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
    barr = new Barrier("128.195.136.162");
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

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc-1.calit2
	mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc-2.calit2
	mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3.calit2
	mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc-4.calit2
	mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc-5.calit2
	mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc-6.calit2
	mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc-7.calit2
	mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc-8.calit2
 

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
