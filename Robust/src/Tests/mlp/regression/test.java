
public class Test {

  public static void main( String args[] ) {        
    int x = Integer.parseInt( args[0] );
    doSomeWork( x );
    nullMethodBodyFinalNode();
  }

  public static void doSomeWork( int x ) {
    for( int i = 0; i < x; ++i ) {
      sese calc {
	int sum = 0;
	for( int j = 0; j <= i; ++j ) {
	  sum = calculateStuff( sum, 1, 0 );
	}
      }
      sese forceVirtualReal {
	if( i % 3 == 0 ) {
	  sum = sum + (i % 20);
	}
      }
      if( i % 2 == 0 ) {
	sese change {
	  for( int k = 0; k < i*2; ++k ) {
	    sum = calculateStuff( sum, k, 1 );
	  }
	  sum = sum + 1;
	}	
	
	for( int l = 0; l < 3; ++l ) {
	  sum = calculateStuff( sum, 2, 2 );
	}
      }
      sese prnt {
	mightPrint( x, i, sum );
      }
    }
  }

  public static int calculateStuff( int sum, int num, int mode ) {
    int answer = sum;    
    sese makePlaceholderStallAfter {
      sum = sum + 1;
    }
    sum = sum + 1;
    if( mode == 0 ) {
      sese mode1 {
	answer = sum + num;
      }
    } else if( mode == 1 ) {
      sese mode2 {
	answer = sum + (num/2);
      }
    } else {
      sese mode3 {
	answer = sum / num;
      }
    }    
    return answer;
  }

  public static void nullMethodBodyFinalNode() {
    int y = 1;
    sese nothing {
      int x = 0;
    }
    y = x;
    if( x > y ) {
      return;
    } else {
      return;
    }
  }

  public static void mightPrint( int x, int i, int sum ) {
    if( i == x - 1 ) {
      System.out.println( "sum of integers 0-"+i+"("+x+") is "+sum );
    }
  }
}
