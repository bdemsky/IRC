
public class Parameter {
    flag w;
    int a, b;
    Parameter f, g;
    Penguin penguin;

    public Parameter() { a = 0; b = 0; f = null; g = null; }

    public void bar() { foo(); }
    public void foo() { bar(); }
}

public class Penguin {
    int x, y;

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
    public Foo() {}

    public Foo x;

    public void ruinSomeFoos( Foo a, Foo b ) {
	a.x = b.x;
    }
}

// this empty task should still create a non-empty
// ownership graph showing parameter allocation
// look for the parameter s as a label referencing
// a heap region that is multi-object, flagged, not summary
task Startup( StartupObject s{ initialstate } ) {

    while( false ) {
	Foo a = new Foo();
	a.x   = new Foo();
	a.x.x = new Foo();
    }

    taskexit( s{ !initialstate } );
}


/*
// this task allocates a new object, so there should
// be a heap region for the parameter, and several
// heap regions for the allocation site, but the label
// merely points to the newest region
task NewObject( Voo v{ f } ) {
    Voo w = new Voo();
    Baw b = new Baw();
    b.doTheBaw( w );

    taskexit( v{ !f } );
}


// this task 
task Branch( Voo v{ f } ) {
    Voo w = new Voo();
    Baw j = new Baw();
    Baw k = new Baw();

    if( v.x == 0 ) {
	w.b = j;
    } else {
	w.b = k;
    }

    taskexit( v{ !f } );
}
*/

task NoAliasNewInLoop( Voo v{ f } ) {

    for( int i = 0; i < 10; ++i ) {
	Voo w = new Voo();
	w.b   = new Baw();
	w.b.f = new Foo();
    }

    taskexit( v{ !f } );
}

/*
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

task ClobberInitParamReflex( Voo v{ f }, Voo w{ f } ) {
    v.b = v.bb;

    taskexit( v{ !f }, w{ !f } );
}

*/