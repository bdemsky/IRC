/*
public class Parameter {
    flag w;
    int a, b;
    Parameter f, g;
    Penguin p;
    Foo h;

    public Parameter() { a = 0; b = 0; f = null; g = null; }

    public void bar() { foo(); }
    public void foo() { bar(); }
}

public class Penguin {
    int x, y;
    Foo h;    

    public Penguin() { x = 0; y = 0; }

    public void bar() { x = 1; }
}

public class Voo {
    flag f; int x; Baw b; Baw bb;

    public Voo() {}
}

public class Baw {
    int y;
    Foo f;

    public Baw() {}

    public void doTheBaw( Voo v ) { v = new Voo(); }
}
*/

public class Foo {
    flag f;

    public Foo() {}

    public Foo x;
    public Foo y;
    public Foo z;

    /*
    public void ruinSomeFoos( Foo a, Foo b ) {
	a.x = b.x;
    }

    static public void aStaticMethod( Foo p0, Foo p1 ) {
	Foo f0 = new Foo();
	Foo f1 = new Foo();
	Foo f2 = new Foo();

	f0.x = f1;
	p0.x = f0;
	p1.x = f1;
	p1.x = f2;
    }
    */

  static public void m1_( Foo p0 ) {
    Foo g0 = new Foo();
    Foo g1 = new Foo();

    g0.x = p0;
    p0.x = g1;
  }

  static public void m2_( Foo p0 ) {
    Foo g0 = new Foo();

    g0.x = p0;
    g0.y = p0;
  }

  static public void m3_( Foo p0 ) {
    Foo g0 = new Foo();

    p0.x = g0;
    p0.y = g0;
  }

  static public void m4_( Foo p0 ) {
    p0.x = p0;
  }

  static public void m5_( Foo p0 ) {
    Foo g0 = new Foo();
    p0.x = g0;
    g0.x = p0;
  }

  static public void m6_( Foo p0, Foo p1 ) {
    Foo g0 = new Foo();
    Foo g1 = new Foo();
    Foo g2 = new Foo();
    Foo g3 = new Foo();

    g0.x = p0;
    p0.x = g1;

    g2.x = p1;
    p1.x = g3;

    p0.y = p1;
  }

  static public void m7_( Foo p0, Foo p1 ) {
    Foo g0 = new Foo();
    Foo g1 = new Foo();
    Foo g2 = new Foo();
    Foo g3 = new Foo();

    g0.x = p0;
    p0.x = g1;

    g2.x = p1;
    p1.x = g3;

    p0.y = p1;
    p0.z = p1;
  }

  static public void m8_( Foo p0, Foo p1 ) {
    Foo g0 = new Foo();
    Foo g1 = new Foo();
    Foo g2 = new Foo();
    Foo g3 = new Foo();

    g0.x = p0;
    p0.x = g1;

    g2.x = p1;
    p1.x = g3;

    p0.y = p1;
    p1.y = p0;
  }
}


// this empty task should still create a non-empty
// ownership graph showing parameter allocation
// look for the parameter s as a label referencing
// a heap region that is multi-object, flagged, not summary
task Startup( StartupObject s{ initialstate } ) {
    taskexit( s{ !initialstate } );
}

/*
task NewObjectA( Foo a{ f }, Foo b{ f } ) {

    Foo c = new Foo();
    Foo d = new Foo();

    c.x = d;
    a.x = c;

    taskexit( a{ !f }, b{ !f } );
}

task NewObjectB( Foo a{ f }, Foo b{ f } ) {

    Foo c = new Foo();
    Foo d = new Foo();

    a.x = c;
    c.x = d;

    taskexit( a{ !f }, b{ !f } );
}
*/

/*
task NewObjectC( Foo a{ f }, Foo b{ f } ) {

    Foo z = new Foo();
    a.x = z;
    a.y = z;

    Foo c;

    while( false ) {
	c     = new Foo();
	Foo f = new Foo();
	c.x   = f;
	c.y   = f;
    }

    taskexit( a{ !f }, b{ !f } );
}
*/

/*
task forMethod( Foo p0{ f } ) {

    Foo a0;
    Foo a1;

    while( false ) {	
	a1 = a0;	    
	if( false ) {
	    a0 = new Foo();
	}
    }

    Foo z = new Foo();
    a1.x = z;
    z.x  = a1;


    taskexit( p0{ !f } );
}
*/


/*
// this task allocates a new object, so there should
// be a heap region for the parameter, and several
// heap regions for the allocation site, but the label
// merely points to the newest region
task NewObjectInMethod( Voo v{ f } ) {
    Voo w = new Voo();
    Baw b = new Baw();
    b.doTheBaw( w );

    taskexit( v{ !f } );
}


task ClobberInitParamReflex( Voo v{ f }, Voo w{ f } ) {
    v.b = v.bb;

    taskexit( v{ !f }, w{ !f } );
}


task BackToItself( Parameter p0{ w } ) {

    Penguin p = new Penguin();
    p0.p = p;
    p.h = p0.h;

    while( false ) {
	Parameter p1   = new Parameter();
	          p1.h = new Foo();
	Penguin   q    = new Penguin();
	p1.p = q;
	q.h  = p1.h;
    }

    taskexit( p0{ !w } );
}


task SummaryNodeTokens( Foo p0{ f } ) {

    while( false ) {
	Foo a = new Foo();
	a.x   = new Foo();
       	a.x.x = new Foo();
    }
    
    Foo b;
    while( false ) {
	Foo c = new Foo();
	c.x = b;
	b = c;
    }

    taskexit( p0{ !f } );
}


task strongUpdates( Foo p0{ f } ) {

    Foo b = new Foo();

    Foo a = new Foo();
    if( false ) {
	a.x = new Foo();
	a.y = new Foo();
    } else if( false ) {
	a.x = new Foo();
	a.y = new Foo();
    }

    // this should effect a strong update
    a.x = b;


    if( false ) {
	p0.x = new Foo();
	p0.y = new Foo();
    } else if( false ) {
	p0.x = new Foo();
	p0.y = new Foo();
    }

    // p0 points to a multiple-object heap region
    // so this should not make a strong update
    p0.x = b;
    
    taskexit( p0{ !f } );
}


task methodTest( Foo p0{ f } ) {

    Foo up0 = new Foo();
    Foo up1 = new Foo();
    Foo up2 = new Foo();

    Foo a0;
    Foo a1;

    if( false ) {
	a0    = new Foo();
	up0.x = a0;	
	a0.x  = new Foo();
	//Foo temp = new Foo();
    }

    if( false ) {
	a0    = new Foo();
	a0.x  = new Foo();
	a1    = a0;
	up1.x = a0;
    }

    if( false ) {
	a1    = new Foo();
	up2.x = a1;
    }

    // Foo.test( a0, a1 );

    taskexit( p0{ !f } );
}
*/


task methodTest01_( Foo p0{ f }, Foo p1{ f } ) {

  Foo a0before = new Foo();
  if( false ) {
    a0before.x = new Foo();
  } else {
    a0before.x = new Foo();
  }

  Foo a0after = new Foo();
  if( false ) {
    a0after.x = new Foo();
  } else {
    a0after.x = new Foo();
  }

  Foo.m1_( a0after );


  taskexit( p0{ !f }, p1{ !f } );
}


task methodTest02_( Foo p0{ f }, Foo p1{ f } ) {

  Foo a0before = new Foo();
  if( false ) {
    a0before.x = new Foo();
  } else {
    a0before.x = new Foo();
  }

  Foo a0after = new Foo();
  if( false ) {
    a0after.x = new Foo();
  } else {
    a0after.x = new Foo();
  }

  Foo.m2_( a0after );


  taskexit( p0{ !f }, p1{ !f } );
}


task methodTest03_( Foo p0{ f }, Foo p1{ f } ) {

  Foo a0before = new Foo();
  if( false ) {
    a0before.x = new Foo();
  } else {
    a0before.x = new Foo();
  }

  Foo a0after = new Foo();
  if( false ) {
    a0after.x = new Foo();
  } else {
    a0after.x = new Foo();
  }

  Foo.m3_( a0after );


  taskexit( p0{ !f }, p1{ !f } );
}


task methodTest04_( Foo p0{ f }, Foo p1{ f } ) {

  Foo a0before = new Foo();
  if( false ) {
    a0before.x = new Foo();
  } else {
    a0before.x = new Foo();
  }

  Foo a0after = new Foo();
  if( false ) {
    a0after.x = new Foo();
  } else {
    a0after.x = new Foo();
  }

  Foo.m4_( a0after );


  taskexit( p0{ !f }, p1{ !f } );
}


task methodTest05_( Foo p0{ f }, Foo p1{ f } ) {

  Foo a0before = new Foo();
  if( false ) {
    a0before.x = new Foo();
  } else {
    a0before.x = new Foo();
  }

  Foo a0after = new Foo();
  if( false ) {
    a0after.x = new Foo();
  } else {
    a0after.x = new Foo();
  }

  Foo.m5_( a0after );


  taskexit( p0{ !f }, p1{ !f } );
}


task methodTest06_( Foo p0{ f }, Foo p1{ f } ) {

  Foo a0before = new Foo();
  if( false ) {
    a0before.x = new Foo();
  } else {
    a0before.x = new Foo();
  }

  Foo a0after = new Foo();
  if( false ) {
    a0after.x = new Foo();
  } else {
    a0after.x = new Foo();
  }

  Foo a1before = new Foo();
  if( false ) {
    a1before.x = new Foo();
  } else {
    a1before.x = new Foo();
  }

  Foo a1after = new Foo();
  if( false ) {
    a1after.x = new Foo();
  } else {
    a1after.x = new Foo();
  }

  Foo.m6_( a0after, a1after );


  taskexit( p0{ !f }, p1{ !f } );
}


task methodTest07_( Foo p0{ f }, Foo p1{ f } ) {

  Foo a0before = new Foo();
  if( false ) {
    a0before.x = new Foo();
  } else {
    a0before.x = new Foo();
  }

  Foo a0after = new Foo();
  if( false ) {
    a0after.x = new Foo();
  } else {
    a0after.x = new Foo();
  }

  Foo a1before = new Foo();
  if( false ) {
    a1before.x = new Foo();
  } else {
    a1before.x = new Foo();
  }

  Foo a1after = new Foo();
  if( false ) {
    a1after.x = new Foo();
  } else {
    a1after.x = new Foo();
  }

  Foo.m7_( a0after, a1after );


  taskexit( p0{ !f }, p1{ !f } );
}


task methodTest08_( Foo p0{ f }, Foo p1{ f } ) {

  Foo a0before = new Foo();
  if( false ) {
    a0before.x = new Foo();
  } else {
    a0before.x = new Foo();
  }

  Foo a0after = new Foo();
  if( false ) {
    a0after.x = new Foo();
  } else {
    a0after.x = new Foo();
  }

  Foo a1before = new Foo();
  if( false ) {
    a1before.x = new Foo();
  } else {
    a1before.x = new Foo();
  }

  Foo a1after = new Foo();
  if( false ) {
    a1after.x = new Foo();
  } else {
    a1after.x = new Foo();
  }

  Foo.m8_( a0after, a1after );


  taskexit( p0{ !f }, p1{ !f } );
}
