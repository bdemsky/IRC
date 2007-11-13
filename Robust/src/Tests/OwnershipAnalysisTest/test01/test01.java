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

public class P3 {
    public P2(){}
    flag b;
    int y;
    Thing n;
}

task Startup( StartupObject s{ initialstate } ) {
    P1 p1f = new P1(){!a};
    P2 p2f = new P2(){!b};
    P1 p1t = new P1(){ a};
    P2 p2t = new P2(){ b};
    taskexit( s{ !initialstate } );
}

task A( P1 p1f{!a}, 
	P2 p2f{!b} )
{
    p1f.m = p2f.n;

    //p2t.n = p2f.n;

    taskexit( p1f{ a}, 
	      p2f{ b} );
}