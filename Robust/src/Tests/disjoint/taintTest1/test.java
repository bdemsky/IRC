public class Foo {
  public Foo() {}
  public Foo f;
  public Foo g;
}

public class Test {

  static public void main( String[] args ) {

    Foo a = new Foo();
    Foo b = new Foo();

    /*
    rblock r1 {
      a.f = new Foo();
    }
    */
    
    rblock r1 {
      doSomething( a, b );
    }
  }

  static void doSomething( Foo a, Foo b ) {
    
    Foo x = b;
    a.g = x; 

    a.f = new Foo();
    b.f = new Foo();

    Foo f = doStuff( a.f, b.f );
  }   

  static Foo doStuff( Foo m, Foo n ) {

    m.g = n;

    return new Foo();
  }
}
