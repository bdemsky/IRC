public class Test {

  public static void main( String args[] ) {
    
    int x = 1;
    int y = 1;

    sese fi {
      if( true ) {
        x = y + 2;
        y = 3;	
      }      
    }


    // just for testing root's ability to
    // realize a single exit after all returns
    // DOESN'T WORK!
    /*
    if( false ) {
      return;
    }
    */


    /*  ADD BACK IN LATER, TO TEST STALLS
    // shouldn't cause a stall
    int z = x;

    // stall and get values for y and z
    x = x + 1;

    // all of these should proceed without stall
    y = y + 1;
    x = x + 1;
    z = z + 1;
    */

    // see that values from sese fi are
    // forwarded to this sibling
    sese fo {
      // expecting x=3, y=3
      System.out.println( "x="+x+", y="+y );
    }





    //Integer i;
    //afunc( i );
  }

  /*
  public static void afunc( Integer i ) {
    i = null;
  }
  */
}
