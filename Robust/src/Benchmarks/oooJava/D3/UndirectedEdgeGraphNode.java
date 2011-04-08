public class UndirectedEdgeGraphNode extends EdgeGraphNode {

    public int compareTo(UndirectedEdgeGraphNode n) {
      return n.hashCode() - hashCode();
    }

    public int compareTo(Object obj) {
      return compareTo((UndirectedEdgeGraphNode) obj);
    }

    UndirectedEdgeGraphNode(Object d) {
      data = d;
      inEdges = new HashMap();
      outEdges = inEdges;
    }
  }