public class Foo {
  public Foo() {}
  public Foo f;
}

public class Test {
  static public void main( String[] args ) {
    Foo a = disjoint A new Foo();
    Foo b = disjoint B new Foo();
    f0( a, b );
  }

  static public void f0( Foo a, Foo b ) {
    a.f = b;
    f1( b );
    genreach qqq;
  }
   
  static public void f1( Foo c ) {
    Foo d = new Foo();
    c.f = d;
  }
}
