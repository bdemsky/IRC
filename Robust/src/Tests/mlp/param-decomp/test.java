public class Foo {
  public Foo f;
  public Bar b;
  public Foo() {}
}

public class Bar {
  public Foo f;
  public Bar b;
  public Bar() {}
}

public class ChildFoo extends Foo {
  public ChildFoo() {}
}

public class ChildBar extends Bar {
  public ChildBar() {}
}


public class Test {

  public static void main( String args[] ) {        
    m0();
  }

  public static void m0() {
    Foo f1 = new Foo();
    f1.b   = new Bar();
    f1.b.b = new ChildBar();
    if( true ) {
      f1.b = new Bar();
    }
    m1( f1 );
  }

  public static void m1( Foo f ) {
    Bar b2 = new Bar();
    b2.f   = f;
    b2.b   = new Bar();
    Foo f2 = new ChildFoo();
    if( true ) {
      f2.b = b2.b;
    } else if( true ) {
      f2.b = new ChildBar();
    } else {
      f2.b = f.b;
    }
    m2( f2, b2 );
  }

  public static void m2( Foo f, Bar b ) {
    f.f = new ChildFoo();
    b.f = new ChildFoo();
    f.b.f = b.f;
  }
}
