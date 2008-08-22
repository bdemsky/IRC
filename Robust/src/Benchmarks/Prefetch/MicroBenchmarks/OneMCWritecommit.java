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

    int[] mid = new int[5];
    mid[0] = (128<<24)|(195<<16)|(175<<8)|84;//dw-10
    mid[1] = (128<<24)|(195<<16)|(175<<8)|85;//dw-11
    mid[2] = (128<<24)|(195<<16)|(175<<8)|86;//dw-12
    mid[3] = (128<<24)|(195<<16)|(175<<8)|87;//dw-13
    mid[4] = (128<<24)|(195<<16)|(175<<8)|88;//dw-14

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
    /*
    Integer val;
    for(int j=0;  j<10000; j++) {
      atomic {
        val = global new Integer(10);
        for(int i=0; i<nthreads; i++) {
          wa[i].val = val.intValue();
        }
      }
    }
    */
	System.printString("Finished\n");
  }
}
