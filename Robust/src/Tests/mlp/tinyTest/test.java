public class Test {

  public static void main( String args[] ) {

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
  }
}
