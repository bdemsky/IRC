public class Foo {
  public Foo() {}

  public Bar b;
}

public class Bar {
  public Bar() {}  
}

public class Test {

  static public void main( String[] args ) {
    Foo x = disjoint foo new Foo();
    Bar y = disjoint bar new Bar();

    //x.b = y;
    
    virginia( x, y );
  }

  static public void virginia( Foo x, Bar y ) {
    x.b = y;
  }
}
