public class test {
  foo f;
  public test() {}

  public static void main(String x[]) {
    test r=new test();
    r.f=new foo();
    int z;
    sese foo {
      r.f.x=2;
    }
    sese bar {
      z=r.f.x;
    }
    System.out.println(z);
  }
}

class foo {
  foo() {
  }
  int x;
}