public class Test {

  public static void main( String args[] ) {
    // no code is outside the root sese
    // in the main method
    sese root {
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
    }
  }
}
