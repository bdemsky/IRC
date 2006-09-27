public class Array2 {
    int a;
    public static void main(String str[]) {
	int a[][]=new int[10][20];
	for(int i=0;i<10;i++) {
	    for(int j=0;j<20;j++) {
		a[i][j]=i*100+j;
	    }
	}

	for(int i=0;i<10;i++) {
	    for(int j=0;j<20;j++) {
		System.printInt(a[i][j]);
		System.printString(" ");
	    }
	    System.printString("\n");
	}
    }
}
