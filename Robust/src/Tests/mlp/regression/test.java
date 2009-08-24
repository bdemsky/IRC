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

      if( i % 2 == 0 ) {
	sese change {
	  for( int k = 0; k < i*2; ++k ) {
	    sum = sum + 1;	    
	  }
	  sum = sum + 1;
	}	

	sese changeAgain {
	  for( int l = 0; l < 3; ++l ) {
	    sum = sum / 2;
	  }
	}
      }

      sese prnt {
	mightPrint( x, i, sum );
      }

    }


    
    //Foo foo = new Foo();
    //foo.f = x;
    //setTo3( foo );
  }

  public static void mightPrint( int x, int i, int sum ) {
    if( i == x - 1 ) {
      System.out.println( "sum of integers 0-"+i+" is "+sum );
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
