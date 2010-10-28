public class Foo {
  public Foo() {}
  int f;
}


public class test {

  public static void main( String argv[] ) {
    
    //long count  = 500;
    //int  numFoo = 1000;
    long count  = 80;
    int  numFoo = 10;
    
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

      for( int i = 0; i < numFoo; i++ ) {
        array[i] = new Foo();
      }
                  
      for( long j = 0; j < count; j++ ) {
        for( int i = 0; i < numFoo; i++ ) {

          rblock child1 {
            int x = 2;
          }

          Foo foo = array[i];

          // a variable fro sib
          // AND memory dependence
          rblock child2 {
            foo.f += x;
          }
        }
      }

      // force a coarse grained conflict
      long z = 1;
      for( int i = 0; i < numFoo; i++ ) {
        z += array[i].f;
      }


      e1 = System.currentTimeMillis();
    }
    // just read vars so compile doesn't throw them out
    // and force parent of parent to depend on z, for
    // timing
    System.out.println( "ignore: "+z );
    e2 = System.currentTimeMillis();


    double dt1 = ((double)e1-s)/(Math.pow( 10.0, 3.0 ) );
    double dt2 = ((double)e2-s)/(Math.pow( 10.0, 3.0 ) );
    System.out.println( "dt to parent done   ="+dt1+"s" );
    System.out.println( "dt to parent retired="+dt2+"s" );

  }
}
