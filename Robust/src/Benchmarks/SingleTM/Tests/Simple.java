public class Simple extends Thread {
  Counting mycount;
  public Simple(Counting mycount) {
    this.mycount = mycount;
  }

  public void run() {
    for(int i = 0;  i < 10; i++) {
      atomic {
        mycount.increment();
      }
    }
  }

  public static void main(String[] args) {
    Simple[] s;
    Counting c;
    int numthreads = 2;

    atomic {
      c = new Counting();
      s = new Simple[numthreads];
      for(int i = 0; i < 2; i++) {
        s[i] = new Simple(c);
      }
    }

    Simple tmp;
    for(int i = 0; i <  2; i++) {
      atomic {
        tmp = s[i];
      }
      tmp.start();
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
  int count;
  public Counting() {
    this.count = 0;
  }

  public increment() {
    count = count + 1;
  }
}
