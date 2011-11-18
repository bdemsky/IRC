public class Foo {
  public Foo f;
  public Foo g;
}

public class Test {

  static public void main( String args[] ) {

    Vector v = new Vector();

    for( int i = 0; i < 3; ++i ) {
      Foo a = getFlagged();
      Foo b = getUnflagged();
      a.f = b;

      // At this point it is clear all b's are
      // reachable from at most one a
      gendefreach z0;
      genreach z0;

      v.addElement( a );
    }

    // However, the v.addElement may resize the
    // vector, so analysis takes resize into account
    // and ruins the precision
    gendefreach z1;
    genreach z1;

    System.out.println( v.elementAt( 0 ).toString() );
  }

  static public Foo getFlagged() {
    return disjoint jupiter new Foo();
  }

  static public Foo getUnflagged() {
    return new Foo();
  }

}
