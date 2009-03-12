public class Foo {
    Foo next;
    public Foo() {
      next=null;
    }
}

public class Chase extends Thread {
    Foo base;
    public Chase(Foo b) {
	base=b;
    }
    
    public static void main(String [] argv) {
	Chase c;
	atomic {
	    Foo fold=global new Foo();
	    
	    for(int i=0;i<10000;i++) {
		Foo f=global new Foo();
		f.next=fold;
		fold=f;
	    }
	    
	    c=global new Chase(fold);
	}
	c.start((128<<24)|(195<<16)|(136<<8)|162);
	c.join();
    }
    
    public void run() {
      short[] offsets = new short[4];
      offsets[0] = getoffset{Chase, base};
      offsets[1] = (short) 0;
      offsets[2] = getoffset{Foo, next};
      offsets[3] = (short) 32;
      System.rangePrefetch(this, offsets);
      atomic {
        Foo b=base;
        int i = 0;
        while(b!=null) {
          b=b.next;
          i++;
        }
      }
    }
}
