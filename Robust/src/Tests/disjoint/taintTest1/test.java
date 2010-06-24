public class Foo {
  public Foo() {}
  public Foo f;
  public Foo g;
}

public class Test {

  static public void main( String[] args ) {

    Foo a = new Foo();

    if( false ) {
      a = new Foo();
    }

    rblock r1 {
      a.f = new Foo();
      doSomething( a );
    }
   
  }

  static void doSomething( Foo a ) {

    a.g = new Foo();

    a.f.f = a.g;

    //Foo x = a.g;

    //    Foo y = new Foo();
    //    y.f = x;

    //Foo f = doStuff( a, c );
  }   

  static Foo doStuff( Foo m, Foo n ) {

    return new Foo();
  }
}
