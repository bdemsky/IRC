public class DirectedGraph implements Graph {
  protected HashSet nodes;

  protected class GraphNode implements Node {
    protected Object data;
    // protected List inNeighbors;
    // protected List outNeighbors;
    protected LinkedList inNeighbors;
    protected LinkedList outNeighbors;

    protected GraphNode() {
      super();
    }

    public GraphNode(Object n) {
      super();
      data = n;
      inNeighbors = new LinkedList();
      outNeighbors = new LinkedList();
    }

    public Object getData() {
      return getNodeData(this);
    }

    public Object setData(Object n) {
      return setNodeData(this, n);
    }

    public final boolean addInNeighbor(GraphNode n) {
      if (inNeighbors.contains(n)) {
        return false;
      } else {
        inNeighbors.addLast(n);
        return true;
      }
    }

    public final boolean removeInNeighbor(GraphNode n) {
      return inNeighbors.remove(n);
    }

    public final boolean hasInNeighbor(GraphNode n) {
      return inNeighbors.contains(n);
    }

    public final Iterator getInNeighbors() {
      return inNeighbors.iterator();
    }

    public final Iterator getInNeighborsCopy() {
      LinkedList l = new LinkedList();
      Iterator o = inNeighbors.iterator();
      while (o.hasNext()) {
        l.addLast(o);
      }
      return l.iterator();
    }

    public final boolean addOutNeighbor(GraphNode n) {
      if (outNeighbors.contains(n)) {
        return false;
      } else {
        outNeighbors.addLast(n);
        return true;
      }
    }

    public final boolean removeOutNeighbor(GraphNode n) {
      return outNeighbors.remove(n);
    }

    public final boolean hasOutNeighbor(GraphNode n) {
      return outNeighbors.contains(n);
    }

    public final Iterator getOutNeighbors() {
      return outNeighbors.iterator();
    }

    public final Iterator getOutNeighborsCopy() {
      LinkedList l = new LinkedList();
      Iterator o = outNeighbors.iterator();
      while (o.hasNext()) {
        l.addLast(o);
      }
      return l.iterator();
    }
  }

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
    return src_c.getInNeighborsCopy();
  }

  public int getInNeighborsSize(Node node) {
    int i = 0;
    for (Iterator it = getInNeighbors(node); it.hasNext(); i++)
      ;
    return i;
  }

  public int getNumNodes() {
    return nodes.size();
  }

  public Iterator getOutNeighbors(Node src) {
    GraphNode src_c = (GraphNode) src;
    // return Collections.unmodifiableCollection(src_c.getOutNeighbors());
    return src_c.getOutNeighborsCopy();
  }

  public int getOutNeighborsSize(Node node) {
    int i = 0;
    for (Iterator it = getInNeighbors(node); it.hasNext(); i++)
      ;
    return i;
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
    for (Iterator iterator1 = n.getOutNeighborsCopy(); iterator1.hasNext(); removeNeighbor(n, g)) {
      g = (GraphNode) iterator1.next();
    }

    for (Iterator iterator2 = n.getInNeighborsCopy(); iterator2.hasNext(); removeNeighbor(g, n)) {
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
