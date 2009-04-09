public class Test {

  public static void main( String args[] ) {

    int n = 10;

    sese top {     
      int x = n;

      for( int i = 0; i < 3; ++i ) {
	sese loop {
	  x = x + i;
	}
      }
    }

    int j = x;    
  }
}
