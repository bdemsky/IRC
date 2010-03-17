public class Foo {
  public Foo() {}
  public Foo f;
}

public class Test {

  static public void main( String[] args ) {

    Foo top = disjoint topMain new Foo();
    Foo bot = disjoint botMain new Foo();    

    top.f = bot;

    addSomething( bot );   
  }   

  public static void addSomething( Foo x ) {
    //x.f = disjoint added new Foo();
  }

  public static Foo getAFoo() {
    return new Foo();
  }
}
