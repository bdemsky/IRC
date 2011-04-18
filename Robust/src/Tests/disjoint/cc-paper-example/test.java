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
    Graph[] a = new Graph[3];

    for( int i = 0; i < 3; ++i ) {

      Graph g  = disjoint G new Graph();

      genreach p0BeforeUVgen;
      Node  u  = nodeFactory();
      genreach p1afterUgen;
      Node  v  = nodeFactory();
      genreach p2afterVgen;

      //Node u = disjoint U new Node();
      //Node v = disjoint V new Node();

      //Node u=null;
      //      Node v=null;
      //      do {
      //        u = v;
      //        v = disjoint N new Node();
      //      } while(false);

      Config c = disjoint C new Config();

      g.n = u;
      genreach p3AfterConnectG2U;

      u.n = v; 
      genreach p4AfterConnectU2V;

      v.n = u; 
      genreach p5AfterConnectV2U;

      u.c = c;
      v.c = c;
      genreach p6AfterConfig;

      

      a[i] = g;

    }
    
    genreach p7Last;

    System.out.println( a );
  }
}
