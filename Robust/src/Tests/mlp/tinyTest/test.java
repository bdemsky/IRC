
public class Test {

  public static void main( String args[] ) {
    int x = Integer.parseInt( args[0] );
    doSomeWork( x );
  }

  public static void doSomeWork( int x ) {
    for( int i = 0; i < x; ++i ) {
      int sum = 0;
	
      sese change {
	sum = sum + 1;	
      }	

      for( int l = 0; l < 3; ++l ) {
	sum = calculateStuff( sum, 2, 2 );
      }      

      sese prnt {
	mightPrint( x, i, sum );
      }
    }
  }

  public static int calculateStuff( int sum, int num, int mode ) {
    return sum + 10;
  }

  public static void mightPrint( int x, int i, int sum ) {
    if( i == x - 1 ) {
      System.out.println( "sum of integers 0-"+i+"("+x+") is "+sum );
    }
  }
}
