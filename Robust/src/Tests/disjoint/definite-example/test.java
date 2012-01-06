public class Tree {
  Node root;
}

public class Node {
  int z;
  Node left;
  Node right;
  Node() {
    z     = 1;
    left  = null;
    right = null;
  }
}


public class Test {

  

  static public void main( String args[] ) {

    int n = 3;
    Tree[] trees = new Tree[n];
    for( int i = 0; i < n; ++i ) {
      trees[i] = getTree();
      build( trees[i] );
    }

    // every Node is reachable from only one Tree
    gendefreach z0;
    genreach z0;

    int total = 0;
    for( int i = 0; i < n; ++i ) {
      Tree t = trees[i];

      // select a tree
      gendefreach z1;
      genreach z1;

      sillyRemove( t.root );

      // after remove
      gendefreach z2;
      genreach z2;

      total += t.root.z;
    }

    System.out.println( " "+total );
  }
  
  static public Tree getTree() {
    return disjoint jupiter new Tree();
  }

  static public Node getNode() {
    return new Node();
  }

  static public void build( Tree t ) {
    t.root             = getNode();
    t.root.left        = getNode();
    t.root.left.left   = getNode();
    t.root.left.right  = getNode();
    t.root.right       = getNode();
    t.root.right.left  = getNode();
    t.root.right.right = getNode();
  }

  static public void sillyRemove( Node n ) {
    if( n != null &&
        n.left != null &&
        n.left.left != null ) {
      n.left = n.left.left;
    }
  }
}
