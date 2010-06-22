public class Foo {
  public Foo() {}
  public Foo f;
  public Foo g;
}

public class Test {

  static public void main( String[] args ) {

    Foo a = new Foo();
    doSomething( a );
  }

  static void doSomething( Foo a ) {

    a.f = new Foo();
    
    rblock r1 {
      Foo b = a.f;
      b.f = new Foo();
    }

    rblock r2 {
      Foo c = a.f.f;
      c.f = new Foo();
    }


    //Foo f = doStuff( a, c );
  }   

  static Foo doStuff( Foo m, Foo n ) {

    m.f = new Foo();
    n.f = new Foo();

    m.g = n.f;

    return new Foo();
  }
}
