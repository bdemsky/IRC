public class Foo {
  public Foo() {}
}


// TODO
// -objects


public class Test {

  public static void main( String args[] ) {
    
    int x = Integer.parseInt( args[0] );
    int y = Integer.parseInt( args[1] );
    //System.out.println( "root: x="+x+", y="+y );

    if( x > 3 ) {
      sese fee {
	y = y + 10;
	//System.out.println( "fee: y="+y );
      }
    }

    /*
    sese fie {
      float xyz = -2.0f;
    }
    float jjj = Math.abs( xyz );
    */

    // see that values from sese fi are
    // forwarded to this sibling
    sese foe {
    //System.out.println( "fo: x="+x+", y="+y );
    //System.out.println( "y="+y+" xyz="+xyz );
      System.out.println( "foe: y="+y );
    }



    // just for testing root's ability to
    // realize a single exit after all returns
    // DOESN'T WORK!
    //if( false ) {
    //  return;
    //}
  }
}
