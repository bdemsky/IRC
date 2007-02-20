public class PrintObject {
    PrintObject() {}
    synchronized void print(String n) {
	System.printString(n);
    }
}

public class ThreadTest2 extends Thread {
    String name;
    PrintObject a;
    public ThreadTest2(String name, PrintObject a) {
	this.name=name;
	this.a=a;
    }

    public static void main(String[] st) {
	PrintObject po=new PrintObject();
	System.printString("hello");
	ThreadTest2 tt=new ThreadTest2("AAAAAA\n",po);
	tt.start();
	tt=new ThreadTest2("BBBBBB\n",po);
	tt.start();
    }
    public void run() {
	while(true) {
	    a.print(name);
	}
    }
}
