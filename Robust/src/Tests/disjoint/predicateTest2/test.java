public class Foo {
  public Foo() {}
  public Bar b;
}

public class Bar {
  public Bar() {}
}

public class Test {

  static public void main( String[] args ) {

    Foo f1 = new Foo();
    Foo extraVar = f1;
    addSomething( f1 );
    
    Foo f2 = new Foo();
    addSomething( f2 );    
  }   

  public static void addSomething( Foo f ) {
    f.b = new Bar();
    //addBar( f );
  }

  public static void addBar( Foo g ) {
    if( true ) {
      g.b = new Bar();
    } else {
      g.b = new Bar();
    }
  }
}
