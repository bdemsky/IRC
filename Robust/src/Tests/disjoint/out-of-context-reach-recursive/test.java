public class Foo {
  public Foo f;
}


public class Test {

  static public void main( String args[] ) {
    // The initial context is special in the sense that it gets
    // tinkered with by the analysis even though it is not special
    // semantically.  When probing the analysis for bugs, it can't
    // hurt to just always skip this context.
    innerMain();
  }

  static public void innerMain() {
    Foo z = get1();
    Foo x = new Foo();
    z.f = x;

    Foo z2 = get1();
    Foo x2 = new Foo();
    z2.f = x2;

    genreach context0before;
    context1( x, x2 );
    genreach context0post;

    System.out.println( "" + z + x + z2 + x2 );
  }

  static public void context1( Foo x, Foo x2 ) {
    genreach context1before;
    Foo y = get1();
    genreach context1mid;
    context2( x2 );
    genreach context1post;

    // consume x and y to prevent optimizing them away
    System.out.println( "" + x + y + x2 );
  }

  static public void context2( Foo q ) {
    genreach context2before;
    context3( q );
    genreach context2post;
  }

  static public void context3( Foo q ) {
    genreach context3before;
    if( false ) {
      context3( q );
    }
    genreach context3post;
  }

  static public Foo get1() {
    return disjoint F1 new Foo();
  }
}
