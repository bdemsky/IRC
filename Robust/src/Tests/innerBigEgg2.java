public class innerBigEgg2 extends innerEgg2 {
  public class Yolk extends innerEgg2.Yolk {
    public Yolk() { 
		//(new Egg2()).
	        super();
		System.out.println("innerBigEgg2.Yolk() getValue() = " + getValue() + " value = "+value); 
	}
    public void f() {
      System.out.println("innerBigEgg2.Yolk.f()");
    }
  }
  public int value = 2;
  public int getValue(){ return value;}
  public innerBigEgg2() { super(); super.value = 1; insertYolk(new Yolk()); }
  public static void main(String[] args) {
    innerEgg2 e2 = new innerBigEgg2();
    e2.g();
  }
}

