public class Foo {
  public int z;

  public Foo( int z ) {
    this.z = z;
  }  
}

public class Test {

  public static void main( String args[] ) {        
    int x = Integer.parseInt( args[0] );
    Foo f = new Foo( x + 10000 );
    int s = doSomeWork( x, f );
    int t = moreWork( x, f );
    int r = s+t;
    System.out.println( "s = "+s+
                        ", t = "+t+
                        ", r = "+r );
  }

  public static int doSomeWork( int x, Foo f ) {
    float delta = 1.0f;
    int out = 0;
    return out;
  }

  public static int moreWork( int x, Foo f ) {
    int total = 0;
    for( int j = 0; j < x; ++j ) {
      sese doe {
        Foo g = new Foo( j );
        int prod = 1;
      }
      sese rae {
        if( j % 7 == 0 ) {
          prod = prod * j;
        }
        g.z = prod / x;
      }
      sese mi {
        g.z = g.z + f.z;
      }
      if( j % 3 == 0 ) {
        sese fa {
          prod = prod / 2;
        }
      }

      total = total + prod - g.z;
    }

    return total;
  }
}
