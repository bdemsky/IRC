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
    public Node() {
    }
    
    public void init(int degree, double val) {
	this.value=val;
	// create empty array for holding toNodes
	toNodes = new Node[degree];
    }
    
    /** 
     * Create unique `degree' neighbors from the nodes given in nodeTable.
     * We do this by selecting a random node from the give nodeTable to
     * be neighbor. If this neighbor has been previously selected, then
     * a different random neighbor is chosen.
     * @param nodeTable the list of nodes to choose from.
     **/
    public void makeUniqueNeighbors(EVector[] reversetable,Node[] nodeTable, Random rand, int begin, int end) {
	Node[] toN=toNodes;
	int len=toN.length;
	for (int filled = 0; filled < len; filled++) {
	    int k;
	    Node otherNode;
	    int index;
	    do {
		boolean isBreak = false;
		// generate a random number in the correct range
		index = rand.nextInt();
		if (index < 0) index = -index;
		//local vs remote from em3d benchmark
		if (filled<(len/4))
		    index=index%nodeTable.length;
		else
		    index=begin+(index%(end-begin));
		
		// find a node with the random index in the given table
		otherNode = nodeTable[index];
		
		for (k = 0; (k < filled) && (isBreak==false); k++) {
		    if (otherNode == toN[k]) 
			isBreak = true;
		}
	    } while (k < filled);
	    
	    // other node is definitely unique among "filled" toNodes
	    toN[filled] = otherNode;
	    
	    // update fromCount for the other node
	    if (reversetable[index]==null)
		reversetable[index]=new EVector();
	    reversetable[index].addElement(this);
	}
    }

    /** 
     * Allocate the right number of FromNodes for this node. This
     * step can only happen once we know the right number of from nodes
     * to allocate. Can be done after unique neighbors are created and known.
     *
     * It also initializes random coefficients on the edges.
     **/

    public void makeFromNodes() {
	fromNodes = new Node[fromCount]; // nodes fill be filled in later
	coeffs = new double[fromCount];
    }

    /**
     * Fill in the fromNode field in "other" nodes which are pointed to
     * by this node.
     **/
    public void updateFromNodes(Random rand) { 
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
    public String toString() {
	String returnString;
	returnString = "value " + (long)value + ", from_count " + fromCount;
    }
}
