public class Atomic {
    int a;
    public static void main(String[] st) {
	Integer z;
	atomic {
	    z=global new Integer(3);
	}
	int q=test(z);
	System.printInt(q);
	System.printString("\n");
    }
    public static atomic int test(Integer y) {
	return y.intValue();
    }
}
