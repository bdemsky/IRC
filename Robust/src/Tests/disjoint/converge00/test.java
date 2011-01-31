public class Foo {
  public Foo() {
    f = null;
  }
  public Foo f;
}

public class Test {
  static public void main( String[] args ) {
    Foo a = new Foo();
    //Foo b = f0( a );
    //b.f   = new Foo();

    /*
    Foo ptr = a;
    int cnt = 0;
    while( ptr != null ) {
      cnt++;
      ptr = ptr.f;
    }

    System.out.println( "expecting 7, "+cnt );
    */
  }

  static public Foo f0( Foo a ) {
    a.f   = new Foo();
    Foo b = f1( a.f );
    b.f   = new Foo();
    return b.f;
  }

  static public Foo f1( Foo a ) {
    a.f   = new Foo();
    Foo b = f2( a.f );
    b.f   = new Foo();
    return b.f;
  }

  static public Foo f2( Foo a ) {
    a.f = new Foo();
    return a.f;
  }
}
