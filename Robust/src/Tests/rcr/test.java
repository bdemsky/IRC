public class test {
  foo f;
  public test() {}

  public static void main(String xz[]) {
    test[] r=new test[10];
    for(int i=0;i<10;i++) {
      r[i]=new test();
      //      if (i%2==0)
	r[i].f=new foo();
	//      else
	//	r[i].f=new foo();
    }

    for (int z=0;z<100000;z++) {
      for (int i=0;i<10;i++) {
	test x=r[i];
	sese foo {
	  int t=x.f.x;
	  x.f.x=t+1;
	}
      }
    }
    for(int i=0;i<10;i++)
      System.out.println(r[i].f.x);
  }
}

class foo {
  foo() {
  }
  int x;
}