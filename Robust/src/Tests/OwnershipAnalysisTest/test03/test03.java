
public class Parameter {
  flag w;
  Node root;
  public Parameter() {}
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

task Startup( StartupObject s{ initialstate } ) {
  
  Parameter p1 = new Parameter();
  
  taskexit( s{ !initialstate } );
}


task MakeGraph( Parameter p1{ !w } ) {

  while( false ) {
    Parameter p2 = new Parameter();
    
    Node n1 = Node.makeNode();
    Node n2 = Node.makeNode();
    Node n3 = Node.makeNode();
    
    n1.addNeighbor( n2 );
    n2.addNeighbor( n3 );
    n3.addNeighbor( n1 );
    
    p2.root = n1;
  }

  taskexit( p1{ w } );
}
