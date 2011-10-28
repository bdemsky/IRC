public class inner{
  int outer;
  int f2;

  public inner() {
    outer=31;
  }

  public static void main(String x[]) {
    inner i=new inner();
    i.dotest();
  }

  public void dotest() {
    outer=35;
    inner ij = new inner();
    t tmp=ij. new t();
    //t.f2 = 3;
    tmp.print();
  }

  public inner createOne() {
	return new inner();	
	}

  public class t {
   // int outer;
    int f3 = 23;
    public t() {
	//this.outer=4;
	//f2=3;
      f3=4;

    }

    public void print() {
      //should print 4 0 3
      System.out.println(outer);
     // System.out.println(this$0.outer);

    }
  }

}
