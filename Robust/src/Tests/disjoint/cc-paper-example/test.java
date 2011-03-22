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
    Graph g  = disjoint G new Graph();
    Node  u  = nodeFactory();
    Node  v  = nodeFactory();
    Config c = disjoint C new Config();

    g.n = u;
    u.n = v; u.c = c;
    v.n = u; v.c = c;
  }
}
