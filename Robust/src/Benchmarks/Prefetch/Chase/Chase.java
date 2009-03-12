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
	atomic {
      System.println("Starting transaction\n");
	    Foo fold=global new Foo();
	    
	    for(int i=0;i<numTraverse;i++) {
		Foo f=global new Foo();
		f.next=fold;
		fold=f;
	    }
	    
	    c=global new Chase(fold);
	}
	c.start((128<<24)|(195<<16)|(136<<8)|162);
	c.join();
    System.out.println("Finished");
    }
    
    public void run() {
<<<<<<< Chase.java
	for (int j=0;j<10;j++) {
      atomic {
        Foo b=base;
        int i = 0;
        while(b!=null) {
=======
      atomic {
        Foo b=base;
        int i = 0;
        while(b!=null) {
>>>>>>> 1.6
          b=b.next;
<<<<<<< Chase.java
          i++;
=======
          i++;
          if((i&127) == 0)
            ;
>>>>>>> 1.6
        }
<<<<<<< Chase.java
      }
	}
=======
      }
>>>>>>> 1.6
    }
}
