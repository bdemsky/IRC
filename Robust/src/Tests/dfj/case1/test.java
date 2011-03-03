public class Foo {
  public int z;
  public Foo() {}
  public Foo f;
  public Foo g;
}

public class Test {

  static public void main( String args[] ) {
    innerMain( args.length );
  }



  //////////////////////////////////////////////
  //
  //  this program exhibits a bug in the basic
  //  points-to analysis!!! GAH!!
  //
  //  Variable reuse is behaving wackily.
  //  Look at the declaration of Foo c.  In the
  //  generated reach graphs Foo c nodes are
  //  dropped or never appear.  If instead you
  //  move the declaration of Foo c = getFoo3()
  //  where it is initialized, you see the right
  //  points-to graph.  FIX IT

  static public void innerMain( int x ) {
    Foo a = null;
    Foo t = getFoo1();
    Foo c;

    for( int i = 0; i < 1; ++i ) {
      a = t;
      t = getFoo1();
      a.g = t;
    }

    genreach p1;

    Foo b = getFoo2();
    c = getFoo3();

    genreach p2;

    if( x > 0 ) {
      a.f = b;
      b.g = b;
      b.f = getFoo4();
    } else {
      a.f = c;
      c.g = getFoo4();
    }

    genreach p3;

    rblock T {
      System.out.println( a.z+b.z+c.z );
    }
  }

  // these "getFoo" methods are exactly the same, except a heap
  // analysis considers each one a separate allocation site
  static public Foo getFoo1() { return new Foo(); }
  static public Foo getFoo2() { return new Foo(); }
  static public Foo getFoo3() { return new Foo(); }
  static public Foo getFoo4() { return new Foo(); }
}
