public class Foo {
  public int z;
  public Foo f;
  public Foo g;

  public Foo() {
    z = 0;
    f = null;
    g = null;
  }
}

public class Test {

  static public void main( String args[] ) {
    innerMain( args.length );
  }

  static public void innerMain( int x ) {
    Foo a = null;
    Foo t = getFoo1();
    t.z = 3;

    for( int i = 0; i < 3; ++i ) {
      a = t;
      t = getFoo1();
      t.g = a;
    }

    genreach p1;

    Foo b = getFoo2();
    Foo c = getFoo3();

    genreach p2;

    if( x > 0 ) {
      a.f = b;
      b.g = b;
      b.f = getFoo4();
      b.f.z = 1;
    } else {
      a.g = c;
      c.g = getFoo4();
      c.g.z = 2;
    }

    genreach p3;

    int total = 0;

    rblock T {
      if( a.f != null ) {
        total += a.f.g.g.f.z;
      }
      if( a.g != null ) {
        total += a.g.g.z;
      }
    }

    System.out.println( total );
  }

  // these "getFoo" methods are exactly the same, except a heap
  // analysis considers each one a separate allocation site
  static public Foo getFoo1() { return new Foo(); }
  static public Foo getFoo2() { return new Foo(); }
  static public Foo getFoo3() { return new Foo(); }
  static public Foo getFoo4() { return new Foo(); }
}
