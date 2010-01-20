public class Foo {
  public int z;

  public Foo( int z ) {
    this.z = z;
  }  
}

public class Test {

  public static void main( String args[] ) {        
    int x = Integer.parseInt( args[0] );
    Foo f = new Foo( x + 10000 );
    doSomeWork( x, f );
  }

  public static void doSomeWork( int x, Foo f ) {

    int total = 0;

    for( int i = 0; i < x; ++i ) {
      sese calc {
	Foo g = new Foo( i );
        int sum = 0;
      }      
      sese forceVirtualReal {
	if( i % 3 == 0 ) {
	  sum = sum + (i % 20);
	}
	g.z = sum + 1000;
      }
      sese modobj {
	g.z = g.z + f.z;
      }
      if( i % 2 == 0 ) {
	sese change {
	  sum = sum + 1;
	}		
      }       
      total = total + sum + g.z;
    }

    sese prnt {
      System.out.println( "Results "+x+", "+total );
    }
  }
}
