public class Foo {
  public Foo f;
  public Foo g;
}

public class Test {

  

  static public void main( String args[] ) {
    
    Foo x = getFlagged();
    Foo y = getUnflagged();
    x.f = y;

    // x is flagged and y is reachable from
    // at most one object from that site
    genreach y0;

    Foo t = getFlagged();
    t = getFlagged();

    // x is flagged and y is reachable from
    // at most one object from that site, even
    // though x is summarized now
    genreach y1;

    x.g = y;

    // if we had definite reachability analysis
    // we would realize y is already reachable
    // from x, but we don't and x is summarized
    // so we conservatively increase the arity
    // of objects y is reachable from.
    genreach y2;


    //gendefreach yo;
    System.out.println( x+","+y );
  }

  static public Foo getFlagged() {
    return disjoint jupiter new Foo();
  }

  static public Foo getUnflagged() {
    return new Foo();
  }

}
