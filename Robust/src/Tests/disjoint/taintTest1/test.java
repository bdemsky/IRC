public class Foo {
  public Foo() {}
  public Foo f;
  public Foo g;
}

public class Test {

  static public void main( String[] args ) {

    Foo a = new Foo();
    Foo b = new Foo();
    giveParamNames( a, b );
  }

  static void giveParamNames( Foo a, Foo b ) {
    Foo c = doStuff( a, b );
  }   

  static Foo doStuff( Foo m, Foo n ) {

    m.f = new Foo();
    n.f = new Foo();

    m.g = n.f;

    return new Foo();
  }
}
