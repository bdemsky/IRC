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
        atomic {
	    Foo b=base;
        /*
        //Running small test for manual prefetch
        //TODO Remove later 
        Object o = b;
        short noffsets = (short) 2;
        short[] offsets = new short[2];
        offsets[0] = getoffset{Foo, next};
        offsets[1] = (short)5;
        System.rangePrefetch(o, offsets);
        */
	    while(b!=null)
          b=b.next;
        }
    }
}
