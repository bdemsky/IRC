public class Foo {
  public int z;
  public Foo() {}
}

public class Test {

  static public void main( String args[] ) {

    int x = 12345;

    for( int i = 0; i < 200000; ++i ) {
      rblock a {
        Foo f = new Foo();
        f.z = 0;
        ++f.z;
      }
      rblock b {
        --f.z;
      }
      int y = -1;
      if( i % 2 == 0 ) {
        rblock c {
          y = 1;
        }
      }
      x += f.z + y;
    }

    System.out.println( x );
  }
}
