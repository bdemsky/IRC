public class test {

  public static void main( String argv[] ) {
    
    long count = 50000;
    
    if( argv.length > 0 ) {
      count = count * Integer.parseInt( argv[0] );
    }
            
    long s = System.currentTimeMillis();
    long e1;
    long e2;

    // THIS PARENT SHOULD NOT AFFECT ANYTHING
    // BUT IT BREAKS THE OOO BINARY!!
    //rblock parent {

      long y = 0;
      
      for( long i = 0; i < count; i++ ) {

        // the subsequent sibling has a dependence
        // on the first
        rblock child1 {
          long x = 3;
        }

        rblock child2 {
          if( x + 4 == 7 ) {
            ++y;
          }
        }

      }
      e1 = System.currentTimeMillis();
    //long z = 1;
    //}
    // just read vars so compile doesn't throw them out
    // and force parent of parent to depend on z, for
    // timing
    System.out.println( "ignore: "+y );
    e2 = System.currentTimeMillis();


    double dt1 = ((double)e1-s)/(Math.pow( 10.0, 3.0 ) );
    double dt2 = ((double)e2-s)/(Math.pow( 10.0, 3.0 ) );
    System.out.println( "dt to parent done   ="+dt1+"s" );
    System.out.println( "dt to parent retired="+dt2+"s" );
  }
}
