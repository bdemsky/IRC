public class test {

  public static void main( String argv[] ) {
    
    long count = 500000;
    int x=1;
    
    if( argv.length > 0 ) {
      count = count * Integer.parseInt( argv[0] );
    }
    
    if( argv.length > 1 ) {
      x = Integer.parseInt( argv[1] );
    }
    mytest(count, x);
  }

  static void mytest(long count, int x) {
    for( long i = 0; i < count; i++ ) {
      
      // the parent does a simple variable copy
      // into this child's record at issue because
      // the value is definitely available, the
      // child needs the value read-only
      rblock child {
	for(int j=0;j<x;j++) {
	  ;
	}
      }
    }
  }
}
