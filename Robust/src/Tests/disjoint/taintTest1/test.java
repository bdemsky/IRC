public class Foo {
  public Foo() {}
  public Foo f;
}

public class Test {

  static public void main( String[] args ) {

    Foo f = new Foo();

    Foo g = doStuff( f );
  }   

  static Foo doStuff( Foo m ) {
    
    Foo n = new Foo();
    return n;
  }
}
