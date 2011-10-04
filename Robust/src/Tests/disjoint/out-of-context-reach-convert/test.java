///////////////////////////////////////////////////////////////////
//
//  THIS BUG IS FIXED, the graphs created from the genreach statements
//  in this example program show that (particularly var x2 in the innerMain method)
//  the objects retain the correct reachability states throughout the contexts.
//
///////////////////////////////////////////////////////////////////





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
    // BOOM! Right now this shows nodes/edges downstream of x
    // lose their reach states!!
    genreach context0post;

    System.out.println( "" + z + x + z2 + x2 );
  }

  static public void context1( Foo x, Foo x2 ) {
    // x is reachable from z (site 1), but z is out of the callee
    // context.  Watch what happens to z's out-of-callEE context name
    // when we allocate another object from site 1 in this context.
    genreach context1before;
    Foo y = get1();
    genreach context1mid;
    context2( x2 );
    genreach context1post;

    // consume x and y to prevent optimizing them away
    System.out.println( "" + x + y + x2 );
  }

  static public void context2( Foo q ) {
    // I think whatever you pass in here will accidentally lose its
    // out-of-context edge information after applying the effects of
    // this call, even though it does nothing.
    // YES, THAT IS TRUE
    genreach context2before;
    context3( q );
    genreach context2post;
  }

  static public void context3( Foo q ) {
    genreach context3;
  }

  static public Foo get1() {
    return disjoint F1 new Foo();
  }
}
