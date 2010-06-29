public class Foo {
  public Foo() {}
  public Foo f;
  public Foo g;
    public int a;
}

public class Test {

  static public void main( String[] args ) {

    Foo a = new Foo();
    Foo b = new Foo();

    rblock r1 {
      Foo x = doSomething( a, b );

      // 1 - STALL
      // 2 - NO STALL
      b.f = x.g;
    }
  }
   
  static Foo doSomething( Foo a, Foo b ) {

    Foo z = new Foo();

    rblock c1 {
      z.g = new Foo();
    }

    // 1 (this line commented)
    // 2 (STALL HERE!)
    //z.g = b;

    return z;
  }   

  static Foo doStuff( Foo m, Foo n ) {
      
      m.f.g = n.f;
      return new Foo();

  }
}
