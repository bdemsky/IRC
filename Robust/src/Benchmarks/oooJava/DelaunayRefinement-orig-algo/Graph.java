public interface Graph {

  public abstract Node createNode(Object obj);

  public abstract boolean addNode(Node node);

  public abstract boolean removeNode(Node node);

  public abstract boolean containsNode(Node node);

  public abstract Node getRandom();

  public abstract boolean addNeighbor(Node node, Node node1);

  public abstract boolean removeNeighbor(Node node, Node node1);

  public abstract boolean hasNeighbor(Node node, Node node1);

  public abstract Iterator getInNeighbors(Node node);

  public abstract int getInNeighborsSize(Node node);

  public abstract Iterator getOutNeighbors(Node node);

  public abstract int getOutNeighborsSize(Node node);

  public abstract int getNumNodes();

  public abstract Object getNodeData(Node node);

  public abstract Object setNodeData(Node node, Object obj);

  public abstract boolean isDirected();

  public abstract Iterator iterator();
}
