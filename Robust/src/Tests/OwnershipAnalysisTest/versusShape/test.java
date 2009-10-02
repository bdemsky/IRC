/*
public class SparseGraph {
  Node head;
  public SparseGraph( Node n ) {
    head = n;
  }
}

public class DenseGraph {
  Node head;
  public DenseGraph( Node n ) {
    head = n;
  }
}
*/

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
    //HashSet dSet = new HashSet();
    for( int i = 0; i < 100; ++i ) {
      /*
      SparseGraph sNew = disjoint sparse new SparseGraph( new Node( 3*i ) );
      DenseGraph  dNew = disjoint dense  new DenseGraph ( new Node( 2*i ) );
      sSet.add( sNew );
      dSet.add( dNew );
      SparseGraph s = (SparseGraph) sSet.iterator().next();
      DenseGraph  d = (DenseGraph)  dSet.iterator().next();
      process(  );
      */
      
      
      Node sNew = disjoint sparse new Node( 3*i );
      //Node dNew = disjoint dense  new Node( 2*i );
      
      //dSet.add( dNew );
      //Node s = (Node) sSet.iterator().next();
      //Node d = (Node) dSet.iterator().next();
      growSparseAndDense( sNew /*, d*/ );
    
      sSet.add( sNew );
    }
  }

  static public void growSparseAndDense( Node s /*, Node d*/ ) {
    
    Node sOld = s;
    for( int j = 0; j < 2; ++j ) {
      if( !sOld.nodes.isEmpty() ) {
        sOld = (Node) sOld.nodes.iterator().next();
      }
    }
    Node sNew = new Node( 4/**d.value*/ );
    sOld.nodes.add( sNew );
  

    /*
    Node dOld = d;
    for( int j = 0; j < 2; ++j ) {
      if( !dOld.nodes.isEmpty() ) {
        dOld = (Node) dOld.nodes.iterator().next();
      }
    }
    Node dNew1 = new Node( 5*s.value );
    Node dNew2 = new Node( 6*s.value );
    dOld.nodes.add( dNew1 );
    dOld.nodes.add( dNew2 );
    */
  }
}
