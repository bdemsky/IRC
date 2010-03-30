public class Foo {
  public Foo() {}
  public Foo f;
}

public class Test {

  static public void main( String[] args ) {
    /*
    Foo x = new Foo();
    Foo y = new Foo();
    x.f = y;

    while( false ) {
      
      y = x;     
      
      while( false )  {
        Foo t = y.f;
        y.f = null;
        y = t;
      }

    }
    */

    Foo x   = new Foo();
    x.f     = new Foo();
    x.f.f   = x;

    Foo y = null;

    while( false ) { 

      //erase all edges in circle list
      Foo tmpy = y.f;
      y.f      = null;
      tmpy.f   = null;
      y        = x.f.f;
    }


  }   
}
