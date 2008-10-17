
public class Parameter {
  flag w;
  Node root;
  public Parameter() {}
}

public class Node {
  Node f;
  
  public Node() {}
  
  public static void makeGraph( Node head ) {
    Node s = new Node();
    Node t = new Node();
    s.f = t; 
    t.f = s;
    head.f = s;
  }  
}

task Startup( StartupObject s{ initialstate } ) {
  
  Parameter p1 = new Parameter();
  
  taskexit( s{ !initialstate } );
}

task graphLoop( Parameter p1{ !w } ) {

  Node[] a = new Node[3];

  for( int i = 0; i < 3; ++i ) {
    Parameter p = new Parameter();

    Node n = new Node();
    p.root = n;

    Node.makeGraph( n );
    a[i] = n;
  }

  taskexit( p1{ w } );
}
