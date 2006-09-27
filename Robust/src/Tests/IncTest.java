public class IncTest {

    public static void main(String str[]) {
	int x[]=new int[20];
	for(int i=0;i<10;) {
	    x[i++]++;
	}
	for(int i=0;i<20;i++) {
	    System.printInt(x[i]);
	    System.printString("\n");
	}
	System.printString("----------------\n");

	x=new int[20];
	for(int i=0;i<10;) {
	    x[++i]+=1;
	}
	for(int i=0;i<20;i++) {
	    System.printInt(x[i]);
	    System.printString("\n");
	}
	

    }



}
