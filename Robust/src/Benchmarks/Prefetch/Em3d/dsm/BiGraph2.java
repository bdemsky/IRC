/** 
 * A class that represents the irregular bipartite graph used in
 * EM3D.  The graph contains two linked structures that represent the
 * E nodes and the N nodes in the application.
 **/
public class BiGraph {
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
    
    EVector [][] reversetable;
    int numNodes;

    /**
     * Construct the bipartite graph.
     * @param e the nodes representing the electric fields
     * @param h the nodes representing the magnetic fields
     **/ 
    BiGraph(Node[] e, Node[] h) {
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
    
    static BiGraph create(int numNodes, int degree, int numThreads) {
	// making nodes (we create a table)
	Node [] eTable = global new Node[numNodes];
	Node [] hTable = global new Node[numNodes];
	BiGraph g = global new BiGraph(eTable, hTable);
	g.numNodes=numNodes;
	g.reversetable=global new EVector[numThreads][];
	return g;
    }
    
    
    /**
     * 
     *
     * @return 
     **/
    public void allocateNodes( int indexBegin, int indexEnd, int threadIndex) { 
	for(int i = indexBegin; i < indexEnd; i++ ) {
	    eNodes[i]=global new Node();
	    hNodes[i]=global new Node();
	}
	reversetable[threadIndex]=global new EVector[numNodes];
    }
    
    public void initializeNodes(Node[] fromnodes, Node[] tonodes, int begin, int end, int degree, Random r, int threadIndex) {
	for(int i = begin; i < end; i++ ) {
	    Node n=fromnodes[i];
	    n.init(degree, r.nextDouble());
	    n.makeUniqueNeighbors(reversetable[threadIndex], tonodes, r, begin, end);
	}
    }
    
    /**
     * 
     *
     * @return 
     **/
    
    public void makeFromNodes(Node[] nodes, int indexBegin, int indexEnd, Random r) {
	// Create the fromNodes and coeff field
	int numthreads=reversetable.length;
	for(int i = indexBegin; i < indexEnd; i++) {
	    Node n = nodes[i];
	    int count=0;
	    for(int j=0;j<numthreads;j++) {
		EVector v=reversetable[j][i];
		if(v!=null)
		    count+=v.size();
	    }
	    n.fromCount=count;
	    n.fromNodes=global new Node[count];
	    n.coeffs=global new double[count];
	    count=0;
	    for(int j=0;j<numthreads;j++) {
		EVector v=reversetable[j][i];
		if(v!=null) {
		    for(int k=0;k<v.size();k++) {
			n.fromNodes[count]=(Node)v.elementAt(k);
			n.coeffs[count++]=r.nextDouble();
		    }
		}
	    }
	}
    }
}
