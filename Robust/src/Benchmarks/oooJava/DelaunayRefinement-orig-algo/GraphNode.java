public class GraphNode extends Node {
  protected Object data;
  // protected List inNeighbors;
  // protected List outNeighbors;
  protected Vector inNeighbors;
  protected Vector outNeighbors;

  protected GraphNode() {
    super();
  }

  public GraphNode(Object n) {
    super();
    data = n;
    inNeighbors = new Vector();
    outNeighbors = new Vector();
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
      inNeighbors.addElement(n);
      return true;
    }
  }

  public final boolean removeInNeighbor(GraphNode n) {
    return inNeighbors.remove(n);
  }

  public final boolean hasInNeighbor(GraphNode n) {
    return inNeighbors.contains(n);
  }

  public final Vector getInNeighbors() {
    return inNeighbors;
  }

  public final Vector getInNeighborsCopy() {
    return inNeighbors.clone();
  }

  public final boolean addOutNeighbor(GraphNode n) {
    if (outNeighbors.contains(n)) {
      return false;
    } else {
      outNeighbors.addElement(n);
      return true;
    }
  }

  public final boolean removeOutNeighbor(GraphNode n) {
    return outNeighbors.remove(n);
  }
  public final boolean hasOutNeighbor(GraphNode n) {
    return outNeighbors.contains(n);
  }

  public final Vector getOutNeighbors() {
    return outNeighbors;
  }

  public final Vector getOutNeighborsCopy() {
    return outNeighbors.clone();
  }
}