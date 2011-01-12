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
    Foo x =            new Foo();
    Foo y =            new Foo();
    Foo z = disjoint Z new Foo();
    Foo w = disjoint W new Foo();
    
    Bar bNoZ  = getNewBar();
    Bar bYesZ = getNewBar();
    
    x.b = bNoZ;
    y.b = bYesZ;

    genreach q1;

    z.f = x;

    genreach q2;

    w.f = y;
    
    genreach q3;

    Bar causeSummary = getNewBar();
    causeSummary = getNewBar();

    genreach q4;


    //genreach q5;
  }

  static public Bar getNewBar() {
    return new Bar();
  }
}
