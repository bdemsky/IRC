public class Foo {
  int f;
  public Foo() {}
}


public class Test {

  public static void main( String args[] ) {
    
    int x = Integer.parseInt( args[0] );
    //int y = Integer.parseInt( args[1] );

    for( int i = 0; i < x; ++i ) {

      sese calc {
	int sum = 0;
	for( int j = 0; j <= i; ++j ) {
	  sum = sum + j;
	}
      }

      sese prnt {
	mightPrint( x, i, sum );
      }

    }


    // just for testing root's ability to
    // realize a single exit after all returns
    // DOESN'T WORK!
    //if( false ) {
    //  return;
    //}

    
    //Foo foo = new Foo();
    //foo.f = x;
    //setTo3( foo );
  }

  public static void mightPrint( int x, int i, int sum ) {    
    if( i == x - 1 ) {
      sese output {
	System.out.println( "sum of integers 0-"+i+" is "+sum );
      }
    }
  }

  /*
  public static void setTo3( Foo foo ) {
    sese func {
      foo.f = 3;
    }   
  }
  */
}
