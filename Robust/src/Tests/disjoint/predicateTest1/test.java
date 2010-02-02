public class Foo {
  public Foo() {}
  public Bar b;
}

public class Foo1 extends Foo {
  public Foo1() {}
}

public class Foo2 extends Foo {
  public Foo2() {}
}

public class Bar {
  public Bar() {}
}

public class Bar1 extends Bar {
  public Bar1() {}
}

public class Bar2 extends Bar {
  public Bar2() {}
}

///////////////////////////////////////
//
//  These classes define a virtual
//  method addBar() that will, depending
//  the dispatch, allocate a Bar from
//  one of two allocation sites
//
///////////////////////////////////////
public class DoodadBase {
  public DoodadBase() {}

  public void addBar( Foo f ) {}
}

public class Doodad1 extends DoodadBase {
  public Doodad1() {}

  public void addBar( Foo f ) {
    f.b = new Bar1();
  }
}

public class Doodad2 extends DoodadBase {
  public Doodad2() {}

  public void addBar( Foo f ) {
    f.b = new Bar2();
  }
}


//////////////////////////////////////
//
//  Now set up two call sites, one that
//  invokes one virtual, and another for
//  other, and verify that predicates
//  can sort out the conservative merge
//  of virtual results by call site
//
//////////////////////////////////////
public class Test {
  static public void main( String[] args ) {

    DoodadBase d1 = new Doodad1();
    Foo f1 = new Foo1();
    d1.addBar( f1 );

    DoodadBase d2 = new Doodad2();
    Foo f2 = new Foo2();
    d2.addBar( f2 );
  }   
}
