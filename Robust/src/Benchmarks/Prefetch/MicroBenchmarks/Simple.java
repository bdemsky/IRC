public class Simple extends Thread {
  Counting mycount;
  public Simple(Counting mycount) {
    this.mycount = mycount;
  }

  public void run() {
    for(int i = 0;  i < 10000; i++) {
      atomic {
        mycount.increment();
      }
    }
  }

  public static void main(String[] args) {
    Simple[] s;
    Counting c;
    int numthreads = 2;
    int[] mid = new int[2];
	mid[0] = (128<<24)|(195<<16)|(175<<8)|79; //dw-8
	mid[1] = (128<<24)|(195<<16)|(175<<8)|73; //dw-5
    atomic {
      c = global new Counting();
      s = global new Simple[numthreads];
      for(int i = 0; i < 2; i++) {
        s[i] = global new Simple(c);
      }
    }

    Simple tmp;
    for(int i = 0; i <  2; i++) {
      atomic {
        tmp = s[i];
      }
      tmp.start(mid[i]);
    }

    for(int i = 0; i < 2; i++) {
      atomic {
        tmp = s[i];
      }
      tmp.join();
    }
    //print count
    int finalcount;
    atomic {
      finalcount = c.count;
    }
    System.printString("Count = "+finalcount+"\n");
  }

}

class Counting {
  global int count;
  public Counting() {
    this.count = 0;
  }

  public increment() {
    count = count + 1;
  }
}
