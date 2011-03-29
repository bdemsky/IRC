public class UndirectedEdgeGraph extends DirectedEdgeGraph {
  public UndirectedEdgeGraph() {
  }

  public Edge_d createEdge(Node src, Node dest, Object e) {
    UndirectedEdgeGraphNode gsrc = (UndirectedEdgeGraphNode) src;
    UndirectedEdgeGraphNode gdest = (UndirectedEdgeGraphNode) dest;
    if (gsrc.compareTo(gdest) > 0)
      return new GraphEdge(gsrc, gdest, e);
    else
      return new GraphEdge(gdest, gsrc, e);
  }

  public UndirectedEdgeGraphNode createNode(Object n) {
    return new UndirectedEdgeGraphNode(n);
  }

  public boolean isDirected() {
    return false;
  }
}
