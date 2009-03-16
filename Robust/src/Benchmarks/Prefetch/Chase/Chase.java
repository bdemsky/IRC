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
	int numTraverse = 10000;
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
	System.out.println("Finished");
    }
    
    public void run() {
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
