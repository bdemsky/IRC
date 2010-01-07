public class Foo {
  public Foo() {}
  public Foo f;
}

public class Test {
  static public void main( String[] args ) {
    Foo a = disjoint A new Foo();
    Foo b = disjoint B new Foo();
    a.f = b;
    f1( b );
  }
   
  static public void f1( Foo c ) {
    Foo d = new Foo();
    c.f = d;
  }
}
