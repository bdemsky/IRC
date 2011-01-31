public class Foo {
  public int z;
  public Foo() {}
}

public class Test {

  /*
  static public void main( String args[] ) {
    FOOO();
  }

  static public void FOOO() {

    int x = 1000000;

    for( int i = 0; i < 200000; ++i ) {
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
        y = 1000;
      }

      if( f.z > 0 ) {
        System.out.println( "WHOA WHOA WHOA" );
      }
      x += f.z + y;
    }

    System.out.println( x );
  }
  */

  /*
  static public void main( String args[] ) {

    int x = 1000000;

    for( int i = 0; i < 200000; ++i ) {

      Foo f = new Foo();
      f.z = 0;

      int y = -1000;
      if( i % 2 == 0 ) {
        y = 1000;
      }

      rblock g {
        f.z--;
      }

      aCall( f );
    
      x += f.z + y;
    }

    System.out.println( x );
  }

  static public void aCall( Foo f ) {
    f.z++;

    if( f.z > 0 ) {
      System.out.println( "WHOA WHOA WHOA" );
    }
  }

  static public void yo() {
    rblock h {
      int x = 2;
    }
  }
  */


  static public void main( String args[] ) {

    int x = 1;

    rblock a {
      x++;
    }

    x--;

    int y = yo();

    x = x - y;

    rblock c {
      x++;
    }

    x--;

    System.out.println( x );
  }

  static public int yo() {
    int j = 0;

    rblock b {
      j++;
    }

    j--;
    return j;
  }
}
