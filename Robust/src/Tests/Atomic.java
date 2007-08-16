public class Array {
    int a;
    public static void main(String[] st) {
	int a[]=new int[10];
	Integer z;
	atomic {
	    z=global new Integer(3);
	}
	int i=2;
	int q=test(z);
	System.printInt(q);
    }
    public static atomic int test(Integer y) {
	int x=3;
	int z;
	z=y.intValue();
	return z;
    }

}
