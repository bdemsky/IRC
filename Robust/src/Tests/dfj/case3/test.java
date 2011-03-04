public class Foo {
  public int z;
  public Foo f;
  public Foo g;

  public Foo() {
    z = 0;
    f = null;
    g = null;
  }
}

public class Test {

  static public void main( String args[] ) {
    innerMain();
  }

  static public void innerMain() {
    Foo a = null;
    Foo t = getFoo();
    t.z = 0;

    for( int i = 0; i < 6; ++i ) {
      a = t;
      t = getFoo();
      t.z = a.z + 1;
      t.f = a;
    }

    genreach p1;

    rblock T {
      Foo x = a.f;
      Foo y = x.f;
      y.z = 1;
    }

    rblock S {
      Foo w = a;
      while( w.f != null ) {
        w = w.f;
      }
      w.z = 5;
    }

    int total = 0;
    Foo i = a;
    while( i != null ) {
      total += i.z;
      i = i.f;
    }
    
    System.out.println( total );
  }

  static public Foo getFoo() {
    return new Foo(); 
  }
}
