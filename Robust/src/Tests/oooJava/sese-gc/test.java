public class Foo {
  public int z;
  public Foo() {}
}

public class Test {

  static public void main( String args[] ) {
    int x = 1;
    Foo f = new Foo();
    f.z = 1;
    if( false ) {
      rblock a {
        x = 2;
        f.z = 2;
      }
    }
    rblock b {
      int y = 3;
      Foo g = new Foo();
      g.z = 3;
    }

    rblock c {
      System.out.println( x+y+f.z+g.z );
    }
  }
}
