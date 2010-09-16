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
        f.z = 1;
        x += f.z;
      }
      rblock b {
        x -= f.z;
      }
    }

    System.out.println( x );
  }
}
