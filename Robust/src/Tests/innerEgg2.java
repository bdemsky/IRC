class innerEgg2 {
  protected class Yolk {
    public Yolk() { System.out.println("innerEgg2.Yolk() : getValue() = "+getValue()+" value = "+value); }
    public void f() { System.out.println("innerEgg2.Yolk.f()");}
  }
  public int value = -1;
  public int getValue(){ return value;}
  private Yolk y = new Yolk();
  public innerEgg2() { System.out.println("New innerEgg2()"); }
  public void insertYolk(Yolk yy) { y = yy; }
  public void g() { y.f(); }
}
