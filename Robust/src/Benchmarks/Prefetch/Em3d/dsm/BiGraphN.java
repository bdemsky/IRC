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

  static BiGraph create(int numNodes, int degree, boolean verbose, Random r)
  {
    // making nodes (we create a table)
    //if (verbose) System.printString("making nodes (tables in orig. version)");
    //Node [] eTable=Node.fillTable(numNodes, numDegree, r);
    //Node [] hTable=Node.fillTable(numNodes, numDegree, r);

    Node [] eTable = global new Node[numNodes];
    Node [] hTable = global new Node[numNodes];

    eTable[0] = global new Node(degree, r);
    hTable[0] = global new Node(degree, r);
      
    BiGraph g = global new BiGraph(eTable, hTable);

    return g;
  }

    
  /**
   * 
   *
   * @return 
   **/
  public void allocate( int indexBegin, int indexEnd, int degree, Random r )
  { 
      Node prevNodeE = global new Node(degree, r);
      Node prevNodeH = global new Node(degree, r);

      eNodes[indexBegin] = prevNodeE;
      hNodes[indexBegin] = prevNodeH;

      for( int i = indexBegin + 1; i < indexEnd; i++ ) {
	  Node curNodeE = global new Node(degree, r);
	  Node curNodeH = global new Node(degree, r);

	  eNodes[i] = curNodeE;
	  hNodes[i] = curNodeH;

	  prevNodeE.next = curNodeE;
	  prevNodeH.next = curNodeH;

	  prevNodeE = curNodeE;
	  prevNodeH = curNodeH;
      }
  }

  
  public void linkSegments( int index ) {
      eNodes[index - 1].next = eNodes[index];
      hNodes[index - 1].next = hNodes[index];
  }


  /**
   * 
   *
   * @return 
   **/
  public void makeNeighbors( int indexBegin, int indexEnd, Random r )
  {
      //System.printString( "Making unique neighbors for hNodes...\n" );

      // making neighbors
      //if (verbose) System.printString("updating from and coeffs");
      for(int i = indexBegin; i < indexEnd; i++) {
	  Node n = hNodes[i];
	  n.makeUniqueNeighbors(eNodes, r);
      }

      //System.printString( "Making unique neighbors for eNodes...\n" );

      for (int i = indexBegin; i < indexEnd; i++) {
	  Node n = eNodes[i];
	  n.makeUniqueNeighbors(hNodes, r);
      }
  }


  public void makeFromNodes( int indexBegin, int indexEnd )
  {
      //System.printString( "Making h fromNodes...\n" );

      // Create the fromNodes and coeff field
      //if (verbose) System.printString("filling from fields");
      for(int i = indexBegin; i < indexEnd; i++) {
	  Node n = hNodes[i];
	  n.makeFromNodes();
      }
      
      //System.printString( "Making e fromNodes...\n" );

      for(int i = indexBegin; i < indexEnd; i++) {
	  Node n = eNodes[i];
	  n.makeFromNodes();
      }   
  }


  public void makeFromLinks( int indexBegin, int indexEnd, Random r )
  {
      //System.printString( "Updating h fromNodes...\n" );

      // Update the fromNodes
      for(int i = indexBegin; i < indexEnd; i++) {
	  Node n = hNodes[i];
	  n.updateFromNodes(r);
      }

      //System.printString( "Updating e fromNodes...\n" );

      for(int i = indexBegin; i < indexEnd; i++) {
	  Node n = eNodes[i];
	  n.updateFromNodes(r);
      }      
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
