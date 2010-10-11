public class test {
  int x;
  public test() {}

  public static void main(String x[]) {
    test r=new test();
    int z;
    sese foo {
      r.x=2;
    }
    sese bar {
      z=r.x;
    }
    System.out.println(z);
  }
}