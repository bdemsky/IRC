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
    System.out.println( "s+t="+r );
  }

  public static int doSomeWork( int x, Foo f ) {
    int total = 0;
    float delta = 0.0f;

    for( int i = 0; i < 10; ++i ) {
      sese parallel {
        int[] d = new int[1];
        d[0] = 2*i;
        //int d = 2*i;
      }
      sese waste {
        if( true ) {
          total = total + 1 + d[0];
          //total = total + 1 + d;
        }

        for( int j=0; j < 1; ++j ) {
          if( true ) {
            delta += 1.0f;
          }
        }
      }
    }    
    int temp = 100 + total + (int)delta;
    return x + temp;
  }

  public static int moreWork( int x, Foo f ) {
    return f.z - 9000;
  }
}
