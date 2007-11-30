public class Parameter {
    flag w;
    int a, b;
    Parameter f, g;
    public Parameter() {
	a = 0; b = 0; f = null; g = null;
    }
}

task Startup( StartupObject s{ initialstate } ) {
    Parameter p1 = new Parameter(){!w};
    Parameter p2 = new Parameter(){!w};
    taskexit( s{ !initialstate } );
}

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
