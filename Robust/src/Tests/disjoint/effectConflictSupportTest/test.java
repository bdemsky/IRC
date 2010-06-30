public class Foo {
  public Foo() {}
  public Foo f;
  public Foo g;
  public int v;
}

public class Test {

  static public void main( String[] args ) {
    
    Foo r1 = new Foo();
    Foo r2 = new Foo();

    Foo a = null;

    while( false ) {
      
      r2.f = r1.f;

      if( true ) {
        r1.f = a;
      }

      a = new Foo();
    }

    rblock r1 {
      
      rblock c1 {
        r1.f.v = 1;
      }

      r2.f.v = 2;
    }


  }   
}
