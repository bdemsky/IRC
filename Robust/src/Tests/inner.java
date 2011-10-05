public class inner {
  int outer;


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
    public t() {
      t.this.outer=4;
    }

    public void print() {
      //should print 4 0 35

      System.out.println(outer);
      System.out.println(super.outer);
      System.out.println(inner.this.outer);
    }
  }

}