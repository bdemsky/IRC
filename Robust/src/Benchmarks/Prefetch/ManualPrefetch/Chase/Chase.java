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
	int numTraverse = 1000000;
	if (argv.length>0)
	    numTraverse=Integer.parseInt(argv[0]);

	atomic {
	    Foo fold=global new Foo();
	    
	    for(int i=0;i<numTraverse;i++) {
		Foo f=global new Foo();
		f.next=fold;
		fold=f;
	    }
	    
	    c=global new Chase(fold);
	}
	System.out.println("Starting");
	c.start((128<<24)|(195<<16)|(136<<8)|162);
	c.join();
    }
    
    public void run() {
      short[] offsets = new short[4];
      short[] offsets1 = new short[2];

      offsets[0] = getoffset{Chase, base};
      offsets[1] = (short) 0;
      offsets[2] = getoffset{Foo, next};
      offsets[3] = (short) 4000;
      System.rangePrefetch(this, offsets);
      atomic {
        Foo b=base;
        int i = 0;
        while(b!=null) {
          b=b.next;
          i++;
          if(i == 4001) {
	      i=0;
            offsets1[0] = getoffset{Foo, next};
            offsets1[1] = (short) 4000;
            System.rangePrefetch(b, offsets1);
          }
        }
      }
    }
}
