public class Foo {
  public Foo() {}
  public Foo f;
  public Bar b;
}

public class Bar {
  public Bar() {}
}

public class Test {

  static public void main( String[] args ) {

    Foo f1 = disjoint high1 new Foo();
    Foo extraVar = f1;
    addSomething( f1 );
    
    Foo f2 = disjoint high2 new Foo();
    addSomething( f2 );    


    Foo f3 = getAFoo();
    Foo f4 = getAFoo();
    f3.f = f4;
  }   

  public static void addSomething( Foo f ) {
    addBar( f );
  }

  public static void addBar( Foo g ) {    
    if( true ) {
      g.b = disjoint low1 new Bar();
    } else {
      g.b = disjoint low2 new Bar();
    }    
  }

  public static Foo getAFoo() {
    return new Foo();
  }
}
