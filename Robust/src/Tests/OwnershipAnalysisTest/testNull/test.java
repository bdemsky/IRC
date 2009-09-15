public class Foo {
  public Foo() {}

  Bar x;
  Bar y;
}

public class Bar {
  public Bar() {}
}


public class Test {

  static public void main( String[] args ) {
    
    Foo foo = new Foo();

    foo.x = new Bar();
    foo.y = new Bar();

    Bar a1 = foo.x;

    a1 = null;
  }
}
