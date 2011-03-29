public class EdgeGraphNode implements Node {
    protected HashMap inEdges;
    protected HashMap outEdges;
    protected Object data;

    EdgeGraphNode() {
      super();
    }

    EdgeGraphNode(Object d) {
      super();
      inEdges = new HashMap();
      outEdges = new HashMap();
      data = d;
    }

    protected final boolean hasInNeighbor(EdgeGraphNode n) {
      return inEdges.containsKey(n);
    }

    protected boolean addInEdge(EdgeGraphNode n, GraphEdge e) {
      if (hasInNeighbor(n)) {
        return false;
      } else {
        inEdges.put(n, e);
        return true;
      }
    }

    protected boolean removeInEdge(EdgeGraphNode n) {
      if (!hasInNeighbor(n)) {
        return false;
      } else {
        inEdges.remove(n);
        return true;
      }
    }

    protected GraphEdge getInEdge(EdgeGraphNode n) {
      return (GraphEdge) inEdges.get(n);
    }

    protected Iterator getInEdges() {
      return inEdges.iterator(1);
    }

    protected final Iterator getInNeighbors() {
      return inEdges.iterator(0);
    }

    // TODO someone check this for performance.
    protected final Iterator getInNeighborsCopy() {
      LinkedList l = new LinkedList();
      Iterator o = inEdges.iterator(0);
      while (o.hasNext()) {
        l.addLast(o);
      }
      return l.iterator();
    }

    protected final boolean hasOutNeighbor(EdgeGraphNode n) {
      return outEdges.containsKey(n);
    }

    protected boolean addOutEdge(EdgeGraphNode n, GraphEdge e) {
      if (hasOutNeighbor(n)) {
        return false;
      } else {
        outEdges.put(n, e);
        return true;
      }
    }

    protected boolean removeOutEdge(EdgeGraphNode n) {
      if (!hasOutNeighbor(n)) {
        return false;
      } else {
        outEdges.remove(n);
        return true;
      }
    }

    protected GraphEdge getOutEdge(EdgeGraphNode n) {
      return (GraphEdge) outEdges.get(n);
    }

    protected Iterator getOutEdges() {
      return outEdges.iterator(1);
    }

    protected final Iterator getOutNeighbors() {
      return outEdges.iterator(0);
    }

    // TODO someone check this for performance.
    protected final Iterator getOutNeighborsCopy() {
      LinkedList l = new LinkedList();
      Iterator o = outEdges.iterator(0);
      while (o.hasNext()) {
        l.addLast(o);
      }
      return l.iterator();
    }
  }