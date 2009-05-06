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
      }
    }

    x = x + 1;
    y = x + 1;
  }
}
