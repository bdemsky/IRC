public class Foo {
  public Foo() {}
  public Foo f;
}

public class Test {
  static public void main( String[] args ) {
    Foo a = new Foo();
    f1( a );
  }
   
  static public void f1( Foo b ) {
    Foo c = new Foo();
    b.f = c;
    f1( c );
  }
}
