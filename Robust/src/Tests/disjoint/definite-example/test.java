public class Foo {
  int z;
}

public class Test {

  

  static public void main( String args[] ) {

    int n = 3;

    Foo[] a = getArray();
    for( int j = 0; j < n; ++j ) {
      a[j] = getFoo();
    }

    //Foo a = getFlagged();
    //Foo b = getUnflagged();
    //a.f = b;
    //
    //// a is flagged and b is reachable from
    //// at most one object from that site
    //gendefreach z0;
    //genreach z0;
    //
    //Foo c = new Foo();
    //a.g = c;
    //
    //Foo t = getFlagged();
    //t = getFlagged();
    //
    //Foo u = getUnflagged();
    //u = getUnflagged();
    //
    //// a is flagged and b is reachable from
    //// at most one object from that site, even
    //// though a and b are summarized now.  a
    //// has a reference to a new object c
    //gendefreach z1;
    //genreach z1;
    //
    //c.f = b;
    //
    //// if we had definite reachability analysis
    //// we would realize b is already reachable
    //// from a
    //gendefreach z3;
    //genreach z3;
    //
    //System.out.println( " "+a+b+c );

    int total = 0;
    for( int j = 0; j < n; ++j ) {
      total += a[j].z;
    }
    System.out.println( " "+total );
  }

  static public Foo[] getArray() {
    return disjoint jupiter new Foo[]();
  }

  static public Foo getFoo() {
    Foo f = new Foo();
    f.z = 1;
    return f;
  }

}
