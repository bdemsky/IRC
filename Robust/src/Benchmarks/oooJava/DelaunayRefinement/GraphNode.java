public class GraphNode extends Node {
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

//  public Object getData() {
//    return getNodeData(this);
//  }
//
//  public Object setData(Object n) {
//    return setNodeData(this, n);
//  }

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