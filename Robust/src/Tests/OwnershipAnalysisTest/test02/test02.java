public class Parameter {
  flag w;
  int a;
  int b;
  Parameter f;
  Parameter g;
  Penguin penguin;
  public Parameter() { a = 0; b = 0; f = null; g = null; }
  public void bar() { foo(); }
  public void foo() { bar(); }
}

public class Penguin {
  int x;
  int y;  
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

public class Foo {
  flag f;
  public Foo() {}
  public Foo x;
  public Foo y;
  public Foo z;

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
}

public class Zub {
  flag q;
  public Zub() {}
  public Fub f;
  public Zod z;

  public void addStuff() {
    f = new Fub();
    z.h = new Hod();
  }
}

public class Zod {
  flag r;
  public Zod() {}
  public Hod h;
}

public class Fub { public Fub() {} }
public class Hod { public Hod() {} }

task improveMethodCallWithGlobal( Parameter notUsed{w} ) {

  Zub a0 = new Zub();
  a0.z = new Zod();
  a0.addStuff();

  taskexit( notUsed{!w} );
}


task Startup( StartupObject s{ initialstate } ) {
  
  /*
    Parameter p = new Parameter(){!w};
    p.foo();
    
    Penguin g = new Penguin();
    g.bar();
  */
  
  Parameter p;
  
  for( int i = 0; i < 3; ++i ) {
    p = new Parameter();
    p.penguin = new Penguin();
    p.penguin.bar();
  }
  
  p = null;
  
  taskexit( s{ !initialstate } );
}

/*
task aliasFromObjectAssignment
  ( Parameter p1{!w}, Parameter p2{!w} ) {
  
  p1.f = p2.g;
  
  taskexit( p1{w}, p2{w} );
}

task noAliasFromPrimitiveAssignment
  ( Parameter p1{!w}, Parameter p2{!w} ) {
  
  p1.a = p2.b;
  
  taskexit( p1{w}, p2{w} );
}

task aliasWithTwoLinks
  ( Parameter p1{!w}, Parameter p2{!w} ) {
  
  Parameter j = p1.f;
  p2.f = j;
  
  taskexit( p1{w}, p2{w} );
}

task aliasWithThreeLinks
  ( Parameter p1{!w}, Parameter p2{!w} ) {
  
  Parameter j = p1.f;
  Parameter k = j;
  p2.f = k;
  
  taskexit( p1{w}, p2{w} );
}

task noAliasBreakLinks
  ( Parameter p1{!w}, Parameter p2{!w} ) {
  
  Parameter j = p1.f;
  Parameter k = j;
  k = p2.f;
  p2.f = k;
  
  taskexit( p1{w}, p2{w} );
}

task possibleAliasConditional
  ( Parameter p1{!w}, Parameter p2{!w} ) {
  
  Parameter y;
  
  if( p1.a == 0 ) {
    y = p1.f;
  } else {
    y = p2.f;
  }
  
  p2.g = y;
  
  taskexit( p1{w}, p2{w} );
}

task bunchOfPaths
  ( Parameter p1{!w}, Parameter p2{!w} ) {
  
  Parameter y;
  
  for( int i =0; i < 100; ++i ) {
    
    if( y == p1 ) {
      Parameter z;
      
      for( int j = 0; i < 50; ++j ) {
	if( z == y ) {
	  p1.f = y;
	} else {
	  z = p2.g;
	}
	
	p1.f = z;
      }
      
      y = p1.g;
    } else {
      
      p2.f = y;
    }
  }
  
  p1.f = p2.g;
  
  
  taskexit( p1{w}, p2{w} );
}

task literalTest( Parameter p1{!w} ) {
  Parameter x = null;
  int y = 5;
  String s = "Dude";
  
  taskexit( p1{w} );
}

task newNoAlias
  ( Parameter p1{!w}, Parameter p2{!w} ) {
  
  for( int i = 0; i < 1; ++i ) {
    p1.f = new Parameter();
  }
  
  taskexit( p1{w}, p2{w} );
}

task newPossibleAlias
  ( Parameter p1{!w}, Parameter p2{!w} ) {
  
  Parameter x, y;
  
  for( int i = 0; i < 1; ++i ) {
    p1.f = new Parameter();
    if( true ) {
      x = p1.f;
    } else {
      y = p1.f;
    }
  }
  
  p2.f = y;
  
  taskexit( p1{w}, p2{w} );
}

task NoAliasNewInLoop( Voo v{ f } ) {
  
  for( int i = 0; i < 10; ++i ) {
    Voo w = new Voo();
    w.b   = new Baw();
    w.b.f = new Foo();
  }
  
  taskexit( v{ !f } );
}


task NoAliasNewInLoopAnotherWay( Voo v{ f } ) {
  
  for( int i = 0; i < 10; ++i ) {
    Voo w = new Voo();
    Baw b = new Baw();
    Foo f = new Foo();
    
    w.b = b;
    b.f = f;
  }
  
  taskexit( v{ !f } );
}


task AliasParamToNew( Voo v{ f }, Voo w{ f } ) {
  
  Baw m = new Baw();
  w.b = m;

  Voo x = new Voo();
  x.b = m;

  taskexit( v{ !f }, w{ !f } );
}

task AliasNewToNew( Voo v{ f }, Voo w{ f } ) {
  
  Baw m = new Baw();

  Voo x = new Voo();
  x.b = m;

  taskexit( v{ !f }, w{ !f } );
}
*/
