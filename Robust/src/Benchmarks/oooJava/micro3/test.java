public class Foo {
  int z;
}


public class test {

  public static void main( String argv[] ) {
    
    long count  = 500000;
    long numFoo = 1000;
    
    if( argv.length > 0 ) {
      count = count * Integer.parseInt( argv[0] );
    }

    if( argv.length > 1 ) {
      numFoo = numFoo * Integer.parseInt( argv[1] );
    }
            
    long s = System.currentTimeMillis();
    long e1;
    long e2;

    rblock parent {
      
      Foo[] array = new Foo[numFoo];
      
      for( long i = 0; i < numFoo; i++ ) {
        array[i] = new Foo();
      }
      
      for( long j = 0; j < count; j++ ) {
        for( long i = 0; i < numFoo; i++ ) {

          Foo f = array[i];
          rblock child {
            f.z++;
          }
        }
      }

      // force a coarse grained conflict
      //array[numFoo - 1].z++;

      e1 = System.currentTimeMillis();
    }
    e2 = System.currentTimeMillis();

    double dt1 = ((double)e1-s)/(Math.pow( 10.0, 3.0 ) );
    double dt2 = ((double)e2-s)/(Math.pow( 10.0, 3.0 ) );
    System.out.println( "dt to parent done   ="+dt1+"s" );
    System.out.println( "dt to parent retired="+dt2+"s" );
  }
}
