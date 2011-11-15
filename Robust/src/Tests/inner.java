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
    outerprint();
    t tmp=new t();
    tmp.print();
    outerAnonymousInner(100);
  }
  
  public void outerprint() {
      System.out.println("Outer class print: " + this.outer + "; " + this.f2);
  }
  
  public void outerprintInnerp(innerCallback c) {
      c.call();
  }
  
  public void outerAnonymousInner(final int value) {
      int j = 0; // this should not be included into the following anonymous inner class
      this.outerprintInnerp(new innerCallback() {
	  public void call() {
	      System.out.println("innerCallback: " + value);
	  }
      });
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
      System.out.println("\t Inner class print: ");
      System.out.println(outer);
      System.out.println(super.outer);
      t.super.outer = 1;
      System.out.println(outer);
      System.out.println(t.super.outer);
      System.out.println(inner.this.outer);
      System.out.println(inner.super.outer);
      System.out.println(f1);
      System.out.println(f2);
      System.out.println(f3);
      outerprint();
    }
  }

}
