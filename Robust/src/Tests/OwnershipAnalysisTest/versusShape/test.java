public class SparseGraph {
  Node head;
  public SparseGraph() {
    head = null;
  }
}

public class DenseGraph {
  Node head;
  public DenseGraph() {
    head = null;
  }
}

public class Node {
  public int value;
  public HashSet nodes;
  public Node( int v ) {
    value = v;
    nodes = new HashSet();
  }
}


public class Test {

  static public void main( String[] args ) {
    HashSet sSet = new HashSet();
    HashSet dSet = new HashSet();
    for( int i = 0; i < 100; ++i ) {
      SparseGraph sNew = disjoint sparse new SparseGraph();
      DenseGraph  dNew = disjoint dense  new DenseGraph();
      sSet.add( sNew );
      dSet.add( dNew );
      SparseGraph s = (SparseGraph) sSet.iterator().next();
      DenseGraph  d = (DenseGraph)  dSet.iterator().next();
      
    }
  }
}
