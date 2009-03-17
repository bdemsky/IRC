// an example of a class that should exhibit
// every part of the new parameter model
public class Foo {
  public Foo() {}

  public Foo f;
  public Foo g;
  public String s;
}

// this class should have everything except
// a primary reflexive edge
public class Bar {
  public Bar() {}

  public Foo f;
  public int x;
}

// this class doesn't have a secondary region at all!
public class Zow {
  public Zow() {}

  public int x;
  public String s;
}

// these classes combined with Bar are a set with no
// super-class relationships, for good alias test
public class Bax {
  public Bax() {}

  public Foo f;
  public int x;
}

public class Baf {
  public Baf() {}

  public Foo f;
  public int x;
}


public class Test {

  static public void main( String[] args ) {
    noAliases( new Foo(),
	       new Bar(),
	       new Zow(),
	       new int[10],
	       new Object[10],
	       6 );

    Bar c1 = new Bar();
    Bax c2 = new Bax();
    Baf c3 = new Baf();
    Bar c4 = new Bar();
    c1.f = new Foo();
    c2.f = c1.f;
    c3.f = c1.f;
    Zow z1 = new Zow();
    goodAliases( c1, c2, c3, c4, z1 );

    Foo f1 = new Foo();
    Foo f2 = new Foo();
    Bar b1 = new Bar();
    Bar b2 = new Bar();
    Bar b3 = new Bar();
    Bar b4 = new Bar();
    b1.f = new Foo();
    b2.f = b1.f;
    b3.f = b1.f;
    f1.f = b1.f;
    badAliases( f1, f2, b1, b2, b3, b4, z1 );
  }


  static public void noAliases( Foo p0,
				Bar p1,
				Zow p2,
				int[] p3,
				Object[] p4,
				int p5x ) {    
  }

  // expect p0-p1-p2 aliased with separate primary objects
  static public void goodAliases( Bar p0,
				  Bax p1,
				  Baf p2,
				  Bar p3,
				  Zow p4 ) {
  }

  // expect p0-p2-p3-p4 aliased in a yucky blob
  static public void badAliases( Foo p0,
				 Foo p1,
				 Bar p2,
				 Bar p3,
				 Bar p4,
				 Bar p5,
				 Zow p6 ) {
  }
}
