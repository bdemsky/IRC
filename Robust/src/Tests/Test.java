public class Test {
    public Test() {
      ;
    }
    int a;
    public static void main(String str[]) {
	Test t=new Test();
	for(int i=3;i<10000;i++) {
		boolean flagx=true;
		for(int j=2;flagx&&j<i;j++) {
			if ((i%j)==0)
                            flagx=false;
		}
		if (flagx) {
			System.printInt(i);
			System.printString("\n");
		}
	}
    }
}
