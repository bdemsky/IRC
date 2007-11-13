public class Thing { int z; public Thing(){} }

public class P1 {
    public P1(){}
    flag a;
    int x;
    Thing m;
}

public class P2 {
    public P2(){}
    flag b;
    int y;
    Thing n;
}

task Startup( StartupObject s{ initialstate } ) {
    P1 p1 = new P1(){};
    P2 p2 = new P2(){};
    taskexit( s{ !initialstate } );
}


task A( P1 p1{!a}, P2 p2{!b} )
{
    p1.m = p2.n;

    taskexit( p1{a}, p2{b} );
}