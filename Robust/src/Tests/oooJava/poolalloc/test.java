public class Foo {
  public int z;
  public Foo() {}
}

public class Test {

  static public void main( String args[] ) {

    int x = 1000000;

    for( int i = 0; i < 200000; ++i ) {
      //for( int i = 0; i < 98; ++i ) {
      rblock a {
        Foo f = new Foo();
        f.z = 0;
        ++f.z;
      }
      rblock b {
        --f.z;
      }
      int y = -1000;
      if( i % 2 == 0 ) {
        //rblock c {
        y = 1000;
        //}
      }
      if( f.z > 0 ) {
        System.out.println( "WHOA WHOA WHOA" );
      }
      x += f.z + y;
    }

    System.out.println( x );
  }
}
