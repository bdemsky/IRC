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


  //////////////////////////////////////////////
  //
  //  @@@@@@@@@ UPDATE!!!!  @@@@@@@@@@@@@@
  //
  //  This is not a bug.  When you declare the
  //  variable c and then initialize it later,
  //  the optimization passes use a temp src
  //  that is correctly represented in the 
  //  underlying points-to information, AND
  //  OoOJava (and presumablely DOJ) recognize
  //  the temp src as the in-set variable, so
  //  all is well.  The reason I thought it was
  //  a bug is that the temp "c" is totally
  //  optimized out, but I interpreted that by
  //  mistake as being dropped from the points-to
  //  graph.
  //
  //////////////////////////////////////////////

  static public void innerMain( int x ) {
    Foo c;

    Foo b = new Foo();

    genreach p1;
    c = new Foo();
    genreach p2;

    sese thePrinter {
      System.out.println( b.z+c.z );
    }
  }
}
