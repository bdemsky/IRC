public class Foo {
  int f;
  public Foo() {}
}


public class Test {

  public static void main( String args[] ) {
    
    int x = Integer.parseInt( args[0] );
    int y = Integer.parseInt( args[1] );
    //System.out.println( "root: x="+x+", y="+y );


    Foo foo = new Foo();
    foo.f = x;

    sese jumbo {
      foo.f = y;
    }

    System.out.println( "f="+foo.f );


    /*
    if( x > 3 ) {
      sese fee {
	y = y + 10;
	//System.out.println( "fee: y="+y );
      }
    }

    System.out.println( "yo" );

    sese fie {
      double xyz = -2.0;
    }

    System.out.println( "go" );

    double jjj = Math.abs( xyz );



    // see that values from sese fi are
    // forwarded to this sibling
    sese foe {
      //System.out.println( "fo: x="+x+", y="+y );
      System.out.println( "foe: y="+y+" jjj="+jjj );
      //System.out.println( "foe: y="+y );
    }
    */


    // just for testing root's ability to
    // realize a single exit after all returns
    // DOESN'T WORK!
    //if( false ) {
    //  return;
    //}
  }
}
