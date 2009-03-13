
public class Foo {
  public Foo f;
  public Foo g;
  public Foo() {}
}


public class Test {

  static public void main( String[] args ) {
    Foo a = disjoint A new Foo();
    Foo b = new Foo();
    Foo c = new Foo();
    Foo d = new Foo();

    carolina( a, b, c, d );
  }

  static public void carolina( Foo p1, Foo p2, Foo p3, Foo p4 ) {
    Foo z = disjoint Z new Foo();

    p1.f = z;

    vermont( p1, p2, p3, p4 );

    texas( p1, p2, p3, p4 );
  }

  static public void texas( Foo p1, Foo p2, Foo p3, Foo p4 ) {
    if( false ) {
      carolina( p3, p4, p1, p2 );
    }
  }


  static public void vermont( Foo p1, Foo p2, Foo p3, Foo p4 ) {
    Foo y = new Foo();
    p3.f = y;
    p4.g = y;

    utah( p1.f, p2, p3, p4 );
  }

  static public void utah( Foo p1, Foo p2, Foo p3, Foo p4 ) {
    Foo x = new Foo();
    p1.f = x;
    p2.f = x;
  }
}
