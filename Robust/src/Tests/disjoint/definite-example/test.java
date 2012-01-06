public class Foo {
  int z;
}

public class Test {

  

  static public void main( String args[] ) {

    int m = 2;
    int n = 3;

    Foo[] a;
    Foo b;

    Foo[][] top = new Foo[m][];
    for( int i = 0; i < m; ++i ) {
      a = getArray( n );
      for( int j = 0; j < n; ++j ) {
        b = getFoo();
        a[j] = b;
      }
      top[i] = a;
    }

    // every Foo is reachable from only one Foo array
    gendefreach z0;
    genreach z0;

    // resize array...
    //Foo[] b = getArray( n + 1 );
    //Foo[] notused = getArray( 1 );
    //b[0] = getFoo();
    //for( int j = 0; j < n; ++j ) {
    //  b[j+1] = a[j];
    //}

    // after array resize?
    gendefreach z1;
    genreach z1;

    // use info to keep compiler from optimizing anything away
    int total = 0;
    for( int i = 0; i < m; ++i ) {
      for( int j = 0; j < n; ++j ) {
        total += top[i][j].z;
      }
    }
    System.out.println( " "+total );
  }

  static public Foo[] getArray( int n ) {
    return disjoint jupiter new Foo[n];
  }

  static public Foo getFoo() {
    Foo f = new Foo();
    f.z = 1;
    return f;
  }

}
