public class WriteArray extends Thread {
  WriteArray[] mywa;
  int val, id;

  public WriteArray() {
  }

  public WriteArray(WriteArray[] wa, int id) {
    mywa = wa;
    this.id = id;
  }

  public void run() {
    //Create idth array object locally
    atomic {
      mywa[id] = global new WriteArray();
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
 
    WriteArray[] wa;
    atomic {
      wa=global new WriteArray[nthreads]; //create nthreads objects
    }

    WriteArrayWrap[] wawrap = new WriteArrayWrap[nthreads];
    atomic {
      for(int i=0;i<nthreads; i++) {
        wawrap[i] = new WriteArrayWrap(global new WriteArray(wa, i));
      }
    }

    for(int i =0; i<nthreads; i++)
      wawrap[i].wa.start(mid[i]);

    for(int i =0; i<nthreads; i++)
      wawrap[i].wa.join();

    //Write into Array
    
    Integer val;
    for(int j=0;  j<10000; j++) {
      atomic {
        val = global new Integer(10);
        for(int i=0; i<nthreads; i++) {
          wa[i].val = val.intValue();
        }
      }
    }
  
	System.printString("Finished\n");
  }
}
