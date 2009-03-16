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


public class Test {

  static public void main( String[] args ) {
    think( new Foo(),
	   new Bar(),
	   new Zow(),
	   new int[10],
	   new Object[10],
	   6 );
  }

  static public void think( Foo p0,
			    Bar p1,
			    Zow p2,
			    int[] p3,
			    Object[] p4,
			    int p5x ) {    
  }
}
