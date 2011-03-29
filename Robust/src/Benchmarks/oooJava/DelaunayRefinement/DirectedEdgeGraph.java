public class DirectedEdgeGraph implements EdgeGraph {
  HashSet nodes;
  public DirectedEdgeGraph() {
    // nodes = Collections.synchronizedSet(new HashSet());
    nodes = new HashSet();
  }

  public boolean addEdge(Edge_d e) {
    GraphEdge ge = (GraphEdge) e;
    EdgeGraphNode src = ge.getSrc();
    EdgeGraphNode dest = ge.getDest();
    return src.addOutEdge(dest, ge) ? dest.addInEdge(src, ge) : false;
  }

  public Edge_d createEdge(Node src, Node dest, Object e) {
    return new GraphEdge((EdgeGraphNode) src, (EdgeGraphNode) dest, e);
  }

  public Node getDest(Edge_d e) {
    return ((GraphEdge) e).getDest();
  }

  public Edge_d getEdge(Node src, Node dest) {
    return ((EdgeGraphNode) src).getOutEdge((EdgeGraphNode) dest);
  }

  public Iterator getInEdges(Node n) {
    return ((EdgeGraphNode) n).getInEdges();
  }

  public Iterator getOutEdges(Node n) {
    return ((EdgeGraphNode) n).getOutEdges();
  }

  public Node getSource(Edge_d e) {
    return ((GraphEdge) e).src;
  }

  public boolean hasEdge(Edge_d e) {
    GraphEdge ge = (GraphEdge) e;
    return ge.getSrc().hasOutNeighbor(ge.getDest());
  }

  public boolean removeEdge(Edge_d e) {
    GraphEdge ge = (GraphEdge) e;
    EdgeGraphNode src = ge.getSrc();
    EdgeGraphNode dest = ge.getDest();
    return src.removeOutEdge(dest) ? dest.removeInEdge(src) : false;
  }

  public boolean addNeighbor(Node src, Node dest) {
    throw new UnsupportedOperationException(
        "addNeighbor not supported in EdgeGraphs. Use createEdge/addEdge instead");
  }

  public Node createNode(Object n) {
    return new EdgeGraphNode(n);
  }

  public Iterator getInNeighbors(Node src) {
    return ((EdgeGraphNode) src).getInNeighbors();
  }

  public int getInNeighborsSize(Node node) {
    int i = 0;
    for (Iterator it = getInNeighbors(node); it.hasNext(); i++)
      ;
    return i;
  }

  public Iterator getOutNeighbors(Node src) {
    return ((EdgeGraphNode) src).getOutNeighbors();
  }

  public int getOutNeighborsSize(Node node) {
    int i = 0;
    for (Iterator it = getOutNeighbors(node); it.hasNext(); i++)
      ;
    return i;
  }

  public boolean removeNeighbor(Node src, Node dest) {
    EdgeGraphNode gsrc = (EdgeGraphNode) src;
    EdgeGraphNode gdest = (EdgeGraphNode) dest;
    return gsrc.removeOutEdge(gdest) ? gdest.removeInEdge(gsrc) : false;
  }

  public Object getEdgeData(Edge_d e) {
    return ((GraphEdge) e).d;
  }

  public Object setEdgeData(Edge_d e, Object d) {
    GraphEdge ge = (GraphEdge) e;
    Object retval = ge.d;
    ge.d = d;
    return retval;
  }

  public Iterator iterator() {
    return nodes.iterator();
  }

  public boolean addNode(Node n) {
    return nodes.add((EdgeGraphNode) n);
  }

  public boolean containsNode(Node n) {
    return nodes.contains(n);
  }

  public Object getNodeData(Node n) {
    EdgeGraphNode egn = (EdgeGraphNode) n;
    return egn.data;
  }

  public int getNumNodes() {
    return nodes.size();
  }

  public Node getRandom() {
    // return (Node)Sets.getAny(nodes);
    return (Node) nodes.iterator().next();
  }

  public boolean hasNeighbor(Node src, Node dest) {
    EdgeGraphNode esrc = (EdgeGraphNode) src;
    EdgeGraphNode edest = (EdgeGraphNode) dest;
    return esrc.hasOutNeighbor(edest);
  }

  public boolean removeNode(Node n) {
    removeConnectingEdges((EdgeGraphNode) n);
    return nodes.remove(n);
  }

  protected void removeConnectingEdges(EdgeGraphNode n) {
    EdgeGraphNode g;
    for (Iterator iterator1 = n.getOutNeighborsCopy(); iterator1.hasNext(); removeNeighbor(n, g)) {
      g = (EdgeGraphNode) iterator1.next();
    }

    for (Iterator iterator2 = n.getInNeighborsCopy(); iterator2.hasNext(); removeNeighbor(g, n)) {
      g = (EdgeGraphNode) iterator2.next();
    }

  }

  public Object setNodeData(Node n, Object d) {
    EdgeGraphNode egn = (EdgeGraphNode) n;
    Object retval = egn.data;
    egn.data = d;
    return retval;
  }

  public boolean isDirected() {
    return true;
  }
}
