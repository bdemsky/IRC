public class DirectedGraph implements Graph {
  protected HashSet nodes;

  public DirectedGraph() {
    // nodes = Collections.synchronizedSet(new HashSet());
    nodes = new HashSet();
  }

  public boolean addNeighbor(Node src, Node dest) {
    GraphNode src_c = (GraphNode) src;
    GraphNode dest_c = (GraphNode) dest;
    return src_c.addOutNeighbor(dest_c) ? dest_c.addInNeighbor(src_c) : false;
  }

  public boolean addNode(Node n) {
    return nodes.add((GraphNode) n);
  }

  public boolean containsNode(Node n) {
    return nodes.contains(n);
  }

  public Node createNode(Object n) {
    return new GraphNode(n);
  }

  // Not proper way to do it, but it seems that no code uses it, so
  // this should be okay.
  public Iterator getInNeighbors(Node src) {
    GraphNode src_c = (GraphNode) src;
    // return Collections.unmodifiableCollection(src_c.getInNeighbors());
    return src_c.getInNeighborsCopy().iterator();
  }

  public int getInNeighborsSize(Node node) {
    return ((GraphNode)node).inNeighbors.size();
  }

  public int getNumNodes() {
    return nodes.size();
  }

  public Iterator getOutNeighbors(Node src) {
    GraphNode src_c = (GraphNode) src;
    // return Collections.unmodifiableCollection(src_c.getOutNeighbors());
    return src_c.getOutNeighborsCopy().iterator();
  }

  public int getOutNeighborsSize(Node node) {
    return ((GraphNode)node).outNeighbors.size();
  }

  public Node getRandom() {
    return (Node) nodes.iterator().next();
  }

  public boolean hasNeighbor(Node src, Node dest) {
    GraphNode src_c = (GraphNode) src;
    GraphNode dest_c = (GraphNode) dest;
    return src_c.hasOutNeighbor(dest_c);
  }

  public boolean removeNeighbor(Node src, Node dest) {
    GraphNode src_c = (GraphNode) src;
    GraphNode dest_c = (GraphNode) dest;
    return src_c.removeOutNeighbor(dest_c) ? dest_c.removeInNeighbor(src_c) : false;
  }

  public boolean removeNode(Node n) {
    removeConnectingEdges((GraphNode) n);
    return nodes.remove(n);
  }

  protected void removeConnectingEdges(GraphNode n) {
    GraphNode g;

    for (Iterator iterator1 = n.getOutNeighborsCopy().iterator(); iterator1.hasNext(); removeNeighbor(n, g)) {
      g = (GraphNode) iterator1.next();
    }
    
    for (Iterator iterator2 = n.getInNeighborsCopy().iterator(); iterator2.hasNext(); removeNeighbor(g, n)) {
      g = (GraphNode) iterator2.next();
    }

  }

  public Object getNodeData(Node n) {
    return ((GraphNode) n).data;
  }

  public Object setNodeData(Node n, Object d) {
    GraphNode gn = (GraphNode) n;
    Object retval = gn.data;
    gn.data = d;
    return retval;
  }

  public Iterator iterator() {
    return nodes.iterator();
  }

  public boolean isDirected() {
    return true;
  }
}
