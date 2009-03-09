
public class Foo {
  public Foo f;
  public Foo g;
  public Foo() {}
}


public class Test {

  static public void main( String[] args ) {
    Foo a = disjoint A new Foo();
    Foo b = disjoint B new Foo();
    Foo c = disjoint C new Foo();

    Foo q = disjoint Q new Foo();

    while( false ) {
      york( a, b, c );
      a.g = q;
      b.g = q;
    }
  }

  static public void york( Foo p1, Foo p2, Foo p3 ) {
    p3.f = new Foo();

    carolina( p1 );
  }

  static public void carolina( Foo p1 ) {
    Foo z = disjoint Z new Foo();

    p1.f = z;
  }

}
