public class Foo {
  public Foo() {}
  public Foo f;
  public Foo g;
}

public class Test {

  static public void main( String[] args ) {

    Foo a = new Foo();
    Foo b = new Foo();

    if( false ) {
      a = new Foo();
    }

    rblock p1 {
      a.f = new Foo();
      a.g = new Foo();

      Foo x = a.f;
    }

    rblock p2 {
      a.f = new Foo();      
      b.f = new Foo();

      rblock c1 {
        Foo d = a;
        d.g = new Foo();
        Foo e = d.g;
      }

      Foo y = a.f;
    }


    
    //doSomething( a );
  }

  static void doSomething( Foo a ) {

    //Foo f = doStuff( a, c );
  }   

  static Foo doStuff( Foo m, Foo n ) {

    return new Foo();
  }
}
