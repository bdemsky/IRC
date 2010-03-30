public class Foo {
  public Foo() {}
  public Foo f;
}

public class Test {

  static public void main( String[] args ) {

    Foo x = null;
    Foo y = new Foo();
    
    while( false ) {
      addSomething( x );
      x = y;
    }
  }   

  public static void addSomething( Foo z ) {
    z.f = new Foo();
  }
}
