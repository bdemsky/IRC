public class CGTestParam {
    flag w;
    int a, b;
    public CGTestParam() { a = 0; b = 0; }
    public void CGTestFoo() {}
    public void CGTestBar() {}
}

public class CGTestParamChild1 extends CGTestParam {
    flag x;
    public CGTestParamChild1() {}
    public void CGTestBar() {}
}

public class CGTestParamChild2 extends CGTestParam {
    flag y;
    public CGTestParamChild2() {}
    public void CGTestFoo() {}
    public void CGTestBar() {}
}

task Startup( StartupObject s{ initialstate } ) {
    CGTestParam p = new CGTestParam(){!w};
    taskexit( s{ !initialstate } );
}

task CGTestTask1( CGTestParam p{!w} ) {
    p.CGTestFoo();
    CGTestParamChild1 p1 = new CGTestParamChild1(){!x};
    CGTestParamChild2 p2 = new CGTestParamChild2(){!y};
    taskexit( p{w} );
}

task CGTestTask2( CGTestParamChild1 p{!x} ) {
    p.CGTestFoo();
    p.CGTestBar();
    taskexit( p{x} );
}

task CGTestTask3( CGTestParamChild2 p{!y} ) {
    p.CGTestFoo();
    p.CGTestBar();
    taskexit( p{y} );
}
