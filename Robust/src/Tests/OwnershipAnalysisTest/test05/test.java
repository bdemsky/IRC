// x=disjoint "X" new X()
// y=disjoint "Y" new Y()
// x.f=y
// z=x
// a=z.f
// x.f=g
//      
// What is B(a)?


public class X extends Y {
  public Y f;
  public X() {}
}

public class Y {
  public Y() {}
}

public class Foo {
  public Foo f;
  public Foo() {}
}


public class Test {

  static public void main( String[] args ) {
    /*
    X x;
    X z;
    Y y;
    Y a;
    X b = new X();
    Y g = new Y();

    x=disjoint X new X();
    y=disjoint Y new Y();
    x.f=y;
    z=x;
    a=z.f;
    b.f=a;
    x.f=g;
    */
    otherThing();
  }

  static public void otherThing() {
    Foo a = disjoint a new Foo();
    Foo b = disjoint b new Foo();
    Foo c = disjoint c new Foo();

    b.f = c;
    a.f = c;
    c = null;
  }
}
