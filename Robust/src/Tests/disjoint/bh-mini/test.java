///////////////////////////////////
//
//  This tiny version of Barnes-Hut
//  might have enough of the elements
//  to exhibit the same reachability state
//  bug as the full benchmark, but wackily
//  it either analyzes longer than I'm
//  willing to wait (over an hour!) or
//  it exhausts the memory on dc-11.
//
///////////////////////////////////


public class ArrayIndexedNode {
  public ArrayIndexedNode[] neighbors;
  public OctTreeNodeData data;
}

public class OctTreeNodeData {
  public int z;
}

public class OctTreeLeafNodeData extends OctTreeNodeData {
  public ArrayIndexedNode root;
}


public class ArrayIndexedGraph {
  public ArrayIndexedNode createNode( OctTreeNodeData d ) {
    ArrayIndexedNode node = disjoint AIN new ArrayIndexedNode();
    node.data = d;
    node.neighbors = new ArrayIndexedNode[1];
    return node;
  }
}


public class Test {

  static public void main( String args[] ) {
    innerMain( args.length );
  }


  static int numBodies = 3;


  static public void innerMain( int x ) {

 
    OctTreeLeafNodeData bodies[] = new OctTreeLeafNodeData[numBodies];
    for( int i = 0; i < numBodies; ++i ) {
      bodies[i] = disjoint BODY new OctTreeLeafNodeData();
      bodies[i].z = 0;
    }

    genreach b0;


    for( int step = 0; step < 1; ++step ) {

      genreach b1;

      ArrayIndexedGraph octree = new ArrayIndexedGraph();

      ArrayIndexedNode root = octree.createNode( new OctTreeNodeData() );

      genreach b2;

      for( int i = 0; i < numBodies; ++i ) {
        insert( octree, root, bodies[i] );
        bodies[i].root = root;
      }

      genreach b3;

    }

  }


  static public void insert( ArrayIndexedGraph   octree,
                             ArrayIndexedNode    root,
                             OctTreeLeafNodeData b 
                             ) {
    ArrayIndexedNode newNode = octree.createNode( b );
    root.neighbors[0] = newNode;
    if( false ) {
      insert( octree, newNode, b );
    }
  }
}
