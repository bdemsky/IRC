public class Foo {
  public Foo() {}
}


public class Test {

  public static void main( String args[] ) {
    
    int x = Integer.parseInt( args[0] );
    int y = Integer.parseInt( args[1] );

    //Foo f;

    sese fi {
      //if( true ) {

      System.out.println( "fi: x="+x+", y="+y );

      x = y + 2;
      y = 3;	     

      //f = new Foo();
      //}      
    }


    // just for testing root's ability to
    // realize a single exit after all returns
    // DOESN'T WORK!
    /*
    if( false ) {
      return;
    }
    */
       
    // see that values from sese fi are
    // forwarded to this sibling
    sese fo {
      System.out.println( "root: x="+x+", y="+y );
    }

    /*
    float xyz = 2.0f;
    float jjj = Math.abs( xyz );
    */


    //Integer i;
    //afunc( i );
  }

  /*
  public static void afunc( Integer i ) {
    i = null;
  }
  */
}
