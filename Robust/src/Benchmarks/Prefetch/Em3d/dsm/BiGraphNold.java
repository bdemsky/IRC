/** 
 * A class that represents the irregular bipartite graph used in
 * EM3D.  The graph contains two linked structures that represent the
 * E nodes and the N nodes in the application.
 **/
public class BiGraph
{
  public BiGraph() {
  }
  /**
   * Nodes that represent the electrical field.
   **/
  Node[] eNodes;
  /**
   * Nodes that representhe the magnetic field.
   **/
  Node[] hNodes;

  /**
   * Construct the bipartite graph.
   * @param e the nodes representing the electric fields
   * @param h the nodes representing the magnetic fields
   **/ 
  BiGraph(Node[] e, Node[] h)
  {
      eNodes = e;
      hNodes = h;
  }

  /**
   * Create the bi graph that contains the linked list of
   * e and h nodes.
   * @param numNodes the number of nodes to create
   * @param numDegree the out-degree of each node
   * @param verbose should we print out runtime messages
   * @return the bi graph that we've created.
   **/

  static BiGraph create(int numNodes, int numDegree, boolean verbose, Random r)
  {

    // making nodes (we create a table)
    //if (verbose) System.printString("making nodes (tables in orig. version)");
    Node [] eTable=Node.fillTable(numNodes, numDegree, r);
    Node [] hTable=Node.fillTable(numNodes, numDegree, r);

    // making neighbors
    //if (verbose) System.printString("updating from and coeffs");
    for(int i = 0; i< numNodes; i++) {
      Node n = hTable[i];
      n.makeUniqueNeighbors(eTable, r);
    }

    for (int i = 0; i < numNodes; i++) {
      Node n = eTable[i];
      n.makeUniqueNeighbors(hTable, r);
    }

    // Create the fromNodes and coeff field
    //if (verbose) System.printString("filling from fields");
    for(int i = 0; i< numNodes; i++) {
      Node n = hTable[i];
      n.makeFromNodes();
    }

    for (int i = 0; i < numNodes; i++) {
      Node n = eTable[i];
      n.makeFromNodes();
    }

    // Update the fromNodes
    for (int i = 0; i < numNodes; i++) {
      Node n = hTable[i];
      n.updateFromNodes(r);
    }
    for (int i = 0; i < numNodes; i++) {
      Node n = eTable[i];
      n.updateFromNodes(r);
    }

    BiGraph g = global new BiGraph(eTable, hTable);
    return g;
  }

  /**
   * Override the toString method to print out the values of the e and h nodes.
   * @return a string contain the values of the e and h nodes.
   **/
  public String toString()
  {
      StringBuffer retval = new StringBuffer();
      Node tmp = eNodes[0];
      while(tmp!=null) {
          Node n = tmp;
          retval.append("E: " + n + "\n");
          tmp = tmp.next;
      }
      tmp = hNodes[0];
      while(tmp!=null) {
          Node n = tmp;
          retval.append("H: " + n + "\n");
          tmp = tmp.next;
      }
      return retval.toString();
  }

}
