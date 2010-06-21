public class Foo {
  public Foo() {}
  public Foo f;
  public Foo g;
}

public class Test {

  static public void main( String[] args ) {

    Foo a = new Foo();
    Foo b = new Foo();
    Foo c = new Foo();
    Foo d = new Foo();
    giveParamNames( a, b, c );
  }

  static void giveParamNames( Foo a, Foo b, Foo c ) {
    Foo e = doStuff( a, b );
    Foo f = doStuff( a, c );
  }   

  static Foo doStuff( Foo m, Foo n ) {

    m.f = new Foo();
    n.f = new Foo();

    m.g = n.f;

    return new Foo();
  }
}
