public class ReadArray extends Thread {
  int id;
  ReadArray[] myra;
  int val;

  public ReadArray() {
  }

  public ReadArray(int id, ReadArray[] ra) {
    this.id = id;
    myra = ra;
  }

  public void run() {
    int tmpid;
    //Create tmpidth array object locally
    atomic {
      tmpid = id;
      myra[tmpid] = global new ReadArray();
      myra[tmpid].val = tmpid*10+1;
    }
  }


  public static void main(String[] args) {
    int nthreads;
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
 
    ReadArray[] a;
    atomic {
      a=global new ReadArray[nthreads]; //create object a
    }

    ReadArrayWrap[] ra = new ReadArrayWrap[nthreads];

    atomic {
      for(int i=0;i<nthreads; i++) {
        ra[i] = new ReadArrayWrap(global new ReadArray(i, a));
      }
    }
    for(int i =0; i<nthreads; i++)
      ra[i].ra.start(mid[i]);

    for(int i =0; i<nthreads; i++)
      ra[i].ra.join();

    //Read Array elements
    for(int j=0; j<10000; j++) {
      atomic {
        int val;
        for(int i=0; i<nthreads; i++) {
          val = a[i].val;
        }
      }
    }

	System.printString("Finished\n");
  }
}
