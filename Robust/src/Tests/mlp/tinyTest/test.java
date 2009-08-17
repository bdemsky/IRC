public class Foo {
  int f;
  public Foo() {}
}


public class Test {

  public static void main( String args[] ) {
    
    int x = Integer.parseInt( args[0] );
    int y = Integer.parseInt( args[1] );
    //System.out.println( "root: x="+x+", y="+y );

    /*
    Foo foo = new Foo();
    foo.f = x;
    */

    /*
    int[] a = new int[x];
    for( int i = 0; i < x; ++i ) {
      sese fill {
	a[i] = i;
      }
    }
    */

    int total = 0;
    for( int i = 0; i < x; ++i ) {
      sese sum {
	total = total + i;
      }
    }


    //setTo3( foo );

    System.out.println( "total="+total );

    // just for testing root's ability to
    // realize a single exit after all returns
    // DOESN'T WORK!
    //if( false ) {
    //  return;
    //}
  }

  /*
  public static void setTo3( Foo foo ) {
    sese func {
      foo.f = 3;
    }   
  }
  */
}
