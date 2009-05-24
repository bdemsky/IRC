public class Test {

  public static void main( String args[] ) {
    /*
    int n = 10;
      
    sese top {     
      int x = 0;
      
      for( int i = 0; i < 3; ++i ) {
	sese iter {
	  x = x + i;
	}
      }      	
      
      int j = x + n;
    }
    
    int z = n + j;
    */









    
    int x = 1;
    int y = 1;

    sese fi {
      if( true ) {
        x = y + 2;
        y = 3;	
      }      
    }

    /*
    // shouldn't cause a stall
    int z = x;

    // stall and get values for y and z
    x = x + 1;

    // all of these should proceed without stall
    y = y + 1;
    x = x + 1;
    z = z + 1;
    */

    // expecting x=3, y=3
    System.out.println( "x="+x+", y="+y );





    //Integer i;
    //afunc( i );
  }

  public static void afunc( Integer i ) {
    i = null;
  }
}
