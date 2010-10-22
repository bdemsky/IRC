public class test {
  int f;
  public test() {}

  public static void main(String xz[]) {
    test[] r=new test[10];
    for(int i=0;i<10;i++) {
      r[i]=new test();r[i].f=0;
    }

    for (int z=0;z<100000;z++) {
      for (int i=0;i<10;i++) {
	test x=r[i];
	sese foo {
	  int t=x.f;
	  x.f=t+1;
	}
      }
    }
    sese print {
      for(int i=0;i<10;i++)
	System.out.println(r[i].f);
    }
  }
}

class foo {
  foo() {
  }
  int x;
}