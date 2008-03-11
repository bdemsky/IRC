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
*/

/*
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
*/

/*
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
*/