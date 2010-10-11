public class Foo {
  public Foo() {}
}

public class Test {
  static public void main( String[] args ) {
    Foo[] x = new Foo[10];

    genreach yo;

    for( int i = 0; i < 10; i++ ) {
      x[i] = new Foo();
    }

    System.out.println( x[0] );
  }
}
