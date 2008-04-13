import java.util.Random;
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
  Node eNodes;
  /**
   * Nodes that representhe the magnetic field.
   **/
  Node hNodes;

  /**
   * Construct the bipartite graph.
   * @param e the nodes representing the electric fields
   * @param h the nodes representing the magnetic fields
   **/ 
  BiGraph(Node e, Node h)
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

  BiGraph create(int numNodes, int numDegree, boolean verbose, Random r)
  {
    Node newnode = new Node();

    // making nodes (we create a table)
    if (verbose) System.out.println("making nodes (tables in orig. version)");
    Node[] hTable = newnode.fillTable(numNodes, numDegree, r);
    Node[] eTable = newnode.fillTable(numNodes, numDegree, r);

    // making neighbors
    if (verbose) System.out.println("updating from and coeffs");
    for(int i = 0; i< numNodes; i++) {
      Node n = hTable[i];
      n.makeUniqueNeighbors(eTable, r);
    }

    for (int i = 0; i < numNodes; i++) {
      Node n = eTable[i];
      n.makeUniqueNeighbors(hTable, r);
    }

    // Create the fromNodes and coeff field
    if (verbose) System.out.println("filling from fields");
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

    BiGraph g = new BiGraph(eTable[0], hTable[0]);
    return g;
  }

  /** 
   * Update the field values of e-nodes based on the values of
   * neighboring h-nodes and vice-versa.
   **/
  /*
  void compute()
  {
      Node tmp = eNodes;
      while(tmp!= null) {
          Node n = tmp;
          n.computeNewValue();
          tmp = tmp.next;
      }
      tmp = hNodes;
      while(tmp!=null) {
          Node n = tmp;
          n.computeNewValue();
          tmp = tmp.next;
      }
  }
  */

  /**
   * Override the toString method to print out the values of the e and h nodes.
   * @return a string contain the values of the e and h nodes.
   **/
  public String toString()
  {
      StringBuffer retval = new StringBuffer();
      Node tmp = eNodes;
      while(tmp!=null) {
          Node n = tmp;
          retval.append("E: " + n + "\n");
          tmp = tmp.next;
      }
      tmp = hNodes;
      while(tmp!=null) {
          Node n = tmp;
          retval.append("H: " + n + "\n");
          tmp = tmp.next;
      }
      return retval.toString();
  }

}

