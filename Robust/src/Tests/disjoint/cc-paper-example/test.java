public class Graph {
  public Node n;
}

public class Node {
  public Config c;
  public Node   n;
}

public class Config {
}



public class Test {

  static public Node nodeFactory() {
    return disjoint N new Node();
  }

  static public void main( String[] args ) {
    //Graph[] a = new Graph[3];

    for( int i = 0; i < 3; ++i ) {
      Graph g  = disjoint G new Graph();

      Node  u  = nodeFactory();
      Node  v  = nodeFactory();

      //Node u = disjoint U new Node();
      //Node v = disjoint V new Node();

      //Node u=null;
      //      Node v=null;
      //      do {
      //        u = v;
      //        v = disjoint N new Node();
      //      } while(false);

      genreach p0;

      Config c = disjoint C new Config();

      g.n = u;
      u.n = v; u.c = c;
      v.n = u; v.c = c;

      //a[i] = g;
    }
    
    //System.out.println( a );
  }
}
