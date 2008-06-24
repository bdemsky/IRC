
public class Parameter1 {
    flag w;
    Node root;
    public Parameter1() {}
}

public class Node {
    HashSet neighbors;

    public Node() {
	neighbors = new HashSet();
    }

    public static Node makeNode() {
	return new Node();
    }

    public addNeighbor( Node n ) {
	neighbors.add( n );
    }
}

// this empty task should still create a non-empty
// ownership graph showing parameter allocation
// look for the parameter s as a label referencing
// a heap region that is multi-object, flagged, not summary
task Startup( StartupObject s{ initialstate } ) {

    Parameter1 p1 = new Parameter1();

    taskexit( s{ !initialstate } );
}


task MakeGraph( Parameter1 p1{ !w } ) {

    Node n1 = Node.makeNode();
    Node n2 = Node.makeNode();
    Node n3 = Node.makeNode();

    n1.addNeighbor( n2 );
    n2.addNeighbor( n3 );
    n3.addNeighbor( n1 );

    p1.root = n1;


    taskexit( p1{ w } );
}