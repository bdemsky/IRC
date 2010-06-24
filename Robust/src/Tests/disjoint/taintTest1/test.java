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
    if( false ) {
      a = new Foo();
    }
    */

    rblock r1 {
      a.f = new Foo();

      b.f = new Foo();

      doSomething( a, b );
    }
   
  }

  static void doSomething( Foo a, Foo b ) {

    a.g = new Foo();

    a.f.f = a.g;

    Foo f = doStuff( a, b );
  }   

  static Foo doStuff( Foo m, Foo n ) {

    m.f.g = n.f;

    return new Foo();
  }
}
