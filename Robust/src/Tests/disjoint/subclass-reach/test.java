public class Jibba {
  public Foo f;
}

public class Foo {
  public int z;
}

public class Bar extends Foo {
  public Jibba j;
}



public class Test {

  static public void main( String args[] ) {
    innerMain( args.length );
  }

  /////////////////////////////////////
  // 
  //  I thought a bug exhibited in Barnes-Hut
  //  might be this:
  //
  //  Jibba x references a Foo y;
  //  y is also a Bar, and the Bar part has
  //     a field that references x.
  //
  //  These two objects should have reach
  //  states showing they reach each other.
  //
  //  TURNS OUT THIS WORKS JUST FINE.
  //
  /////////////////////////////////////

  static public void innerMain( int x ) {

    Bar b = disjoint BAR new Bar();
    Foo f = (Foo) b;

    Jibba j = disjoint JIBBA new Jibba();
    
    b.j = j;
    j.f = f;

    genreach shouldBeCyclic;

    doNothingImportant( j );

    genreach andAfterCall;

    System.out.println( ""+b+j );
  }


  static public void doNothingImportant( Jibba j ) {
    // just a method to create context and pass it back
    j.f.z = 1;
  }
}
