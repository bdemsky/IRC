public class Foo { flag f; Foo x; public Foo(){} }

public class Plane {
  public Plane(){}

  public void fly( Foo p0, Foo p1 ) {
    p0.x = new Foo(){f};
  }
}

public class SpitFire extends Plane {
  public SpitFire(){}

  public void fly( Foo p0, Foo p1 ) {
    p1.x = new Foo(){f};
  }
}

public class Jet extends Plane {
  public Jet(){}

  public void fly( Foo p0, Foo p1 ) {
    Foo jet = new Foo(){f};
    jet.x = p0;
  }
}

public class F14 extends Jet {
  public F14(){}

  public void fly( Foo p0, Foo p1 ) {
    Foo f14 = new Foo(){f};
    f14.x = p1;
  }
}

task Startup( StartupObject s{ initialstate } ) {

  Foo a0 = new Foo(){f};
  Foo a1 = new Foo(){f};
  
  Plane p;
  
  if( false ) {
    p = new Plane();
  } else if( false ) {
    p = new SpitFire();
  } else if( false ) {
    p = new Jet();
  } else {
    p = new F14();
  }

  p.fly( a0, a1 );
  
  taskexit( s{ !initialstate } );
}


task SomeOtherTask( Foo foo{f} ) {

  Foo a0 = new Foo(){f};
  Foo a1 = new Foo(){f};
  
  Plane p;
  
  if( false ) {
    p = new Plane();
  } else if( false ) {
    p = new SpitFire();
  } else if( false ) {
    p = new Jet();
  } else {
    p = new F14();
  }

  a0.x = a1;
  p.fly( a0, a1 );

  taskexit( foo{!f} );
}
