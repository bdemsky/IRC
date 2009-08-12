public class Foo {
  public Foo() {}
}


// TODO
// -dynamic variables
// -objects


public class Test {

  public static void main( String args[] ) {
    
    //int x = Integer.parseInt( args[0] );
    //int y = Integer.parseInt( args[1] );
    //System.out.println( "root: x="+x+", y="+y );
    int y = 2;

    //if( x > 3 ) {
    if( true ) {
      sese fi {
	y = y + 10;
      }
    }
    
    // see that values from sese fi are
    // forwarded to this sibling
    //sese fo {
    //System.out.println( "fo: x="+x+", y="+y );
    System.out.println( "y="+y );
    //}

    /*
    float xyz = 2.0f;
    float jjj = Math.abs( xyz );
    */


    // just for testing root's ability to
    // realize a single exit after all returns
    // DOESN'T WORK!
    //if( false ) {
    //  return;
    //}
  }
}
