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
    mid[0] = (128<<24)|(195<<16)|(175<<8)|84;//dw-10
    mid[1] = (128<<24)|(195<<16)|(175<<8)|85;//dw-11
    mid[2] = (128<<24)|(195<<16)|(175<<8)|86;//dw-12
    mid[3] = (128<<24)|(195<<16)|(175<<8)|87;//dw-13
    mid[4] = (128<<24)|(195<<16)|(175<<8)|88;//dw-14
    mid[5] = (128<<24)|(195<<16)|(175<<8)|89;//dw-15
    mid[6] = (128<<24)|(195<<16)|(175<<8)|90;//dw-16
    mid[7] = (128<<24)|(195<<16)|(175<<8)|91;//dw-17

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
