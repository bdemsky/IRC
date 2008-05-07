/** 
 * This class implements nodes (both E- and H-nodes) of the EM graph. Sets
 * up random neighbors and propagates field values among neighbors.
 */
public class Node {
  /**
   * The value of the node.
   **/
  double value;
  /**
   * The next node in the list.
   **/
  protected Node next;
  /**
   * Array of nodes to which we send our value.
   **/
  Node[] toNodes;
  /**
   * Array of nodes from which we receive values.
   **/
  Node[] fromNodes;
  /**
   * Coefficients on the fromNodes edges
   **/
  double[] coeffs;
  /**
   * The number of fromNodes edges
   **/
  int fromCount;
  /**
   * Used to create the fromEdges - keeps track of the number of edges that have
   * been added
   **/
  int fromLength;

  /** 
   * Constructor for a node with given `degree'.   The value of the
   * node is initialized to a random value.
   **/
  public Node(int degree, Random r) 
  {
    value = r.nextDouble();
    // create empty array for holding toNodes
    toNodes = global new Node[degree];
  }

  /**
   * Create the linked list of E or H nodes.  We create a table which is used
   * later to create links among the nodes.
   * @param size the no. of nodes to create
   * @param degree the out degree of each node
   * @return a table containing all the nodes.
   **/
  public static Node[] fillTable(int size, int degree, Random r)
  {
    Node[] table;
    Node prevNode;
    table = global new Node[size];
    prevNode = global new Node(degree, r);
    table[0] = prevNode;
    for (int i = 1; i < size; i++) {
        Node curNode = global new Node(degree, r);
        table[i] = curNode;
        prevNode.next = curNode;
        prevNode = curNode;
    }
    return table;
  }

  /** 
   * Create unique `degree' neighbors from the nodes given in nodeTable.
   * We do this by selecting a random node from the give nodeTable to
   * be neighbor. If this neighbor has been previously selected, then
   * a different random neighbor is chosen.
   * @param nodeTable the list of nodes to choose from.
   **/
  public void makeUniqueNeighbors(Node[] nodeTable, Random rand)
  {
    for (int filled = 0; filled < toNodes.length; filled++) {
      int k;
      Node otherNode;

      do {
        boolean isBreak = false;
        // generate a random number in the correct range
        int index = rand.nextInt();
        if (index < 0) index = -index;
        index = index % nodeTable.length;

        // find a node with the random index in the given table
        otherNode = nodeTable[index];

        for (k = 0; (k < filled) && (isBreak==false); k++) {
          if (otherNode == toNodes[filled]) 
            isBreak = true;
        }
      } while (k < filled);

      // other node is definitely unique among "filled" toNodes
      toNodes[filled] = otherNode;

      // update fromCount for the other node
      otherNode.fromCount++;
    }
  }

  /** 
   * Allocate the right number of FromNodes for this node. This
   * step can only happen once we know the right number of from nodes
   * to allocate. Can be done after unique neighbors are created and known.
   *
   * It also initializes random coefficients on the edges.
   **/
  public void makeFromNodes()
  {
    fromNodes = global new Node[fromCount]; // nodes fill be filled in later
    coeffs = global new double[fromCount];
  }

  /**
   * Fill in the fromNode field in "other" nodes which are pointed to
   * by this node.
   **/
  public void updateFromNodes(Random rand)
  { 
    for (int i = 0; i < toNodes.length; i++) {
      Node otherNode = toNodes[i];
      int count = otherNode.fromLength++;
      otherNode.fromNodes[count] = this;
      otherNode.coeffs[count] = rand.nextDouble();
    }
  }

  /**
   * Override the toString method to return the value of the node.
   * @return the value of the node.
   **/
  public String toString()
  {
    String returnString;
    returnString = "value " + (long)value + ", from_count " + fromCount;
  }

}
