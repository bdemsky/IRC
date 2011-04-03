public interface EdgeGraph extends Graph {

  public abstract Edge_d createEdge(Node node, Node node1, Object obj);

  public abstract Edge_d getEdge(Node node, Node node1);

  public abstract boolean removeEdge(Edge_d edge);

  public abstract boolean addEdge(Edge_d edge);

  public abstract boolean hasEdge(Edge_d edge);

  public abstract Node getSource(Edge_d edge);

  public abstract Node getDest(Edge_d edge);

  public abstract Iterator getOutEdges(Node node);

  public abstract Iterator getInEdges(Node node);

  public abstract Object getEdgeData(Edge_d edge);

  public abstract Object setEdgeData(Edge_d edge, Object obj);

  public abstract void addNodeToAllNodesSet( Node n );
  public abstract void removeNodeFromAllNodesSet( Node n );
}
