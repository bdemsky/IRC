public class Foo {
    public Foo() {
	next=null;
    }
    Foo next;
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
	c.start((128<<24)|(195<<16)|(175<<8)|79);
	c.join();
    }
    
    public void run() {
        atomic {
	    Foo b=base;
	    while(b!=null)
		b=b.next;
        }
    }
}
