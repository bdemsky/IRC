public class inner extends innerp {
  int outer;
  int f2;

  public inner() {
    super.outer=31;
  }

  public static void main(String x[]) {
    inner i=new inner();
    i.dotest();
  }

  public void dotest() {
    outer=35;
    t tmp=new t();
    tmp.print();
  }

  public class t extends innerpt {
    int outer;
    int f3;
    public t() {
      t.this.outer=4;
      f1=2;
      f2=3;
      f3=4;

    }

    public void print() {
      //should print 4 0 35
      System.out.println(outer);
      System.out.println(super.outer);
      System.out.println(inner.this.outer);
      System.out.println(inner.super.outer);
      System.out.println(f1);
      System.out.println(f2);
      System.out.println(f3);

    }
  }

}