public class Test {
    public Test() {
      ;
    }
    int a;
    public static void main() {
	Test t=new Test();
	for(int i=3;i<100000;i++) {
		boolean flag=true;
		for(int j=2;flag&&j<i;j++) {
			if ((i%j)==0)
                            flag=false;
		}
//		if (flag)
//			System.printInt(i);
	}
    }
}
