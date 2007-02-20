public class Foo {
    Foo x;
    Foo() {}

}

public class ThreadTest extends Thread {
    public ThreadTest() {
    }

    public static void main(String[] st) {
	System.printString("hello");
	ThreadTest tt=new ThreadTest();
	tt.start();
	tt=new ThreadTest();
	tt.start();
	System.printString("main\n");
	System.printString("main\n");
	System.printString("main\n");
	System.printString("main\n");
    }
    public void run() {
	System.printString("thread\n");
	Foo x=null;
	for(int i=0;i<1000;i++) {
	    Foo y=new Foo();
	    y.x=x;
	    x=y;
	}
    }
}
