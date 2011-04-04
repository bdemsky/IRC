public class Subgraph {

  private final LinkedList nodes = new LinkedList();
  private final LinkedList border = new LinkedList();
  private final LinkedList edges = new LinkedList();


  public Subgraph() {
  }

  public boolean existsNode(Node n) {
    return nodes.contains(n);
  }

  public boolean existsBorder(Node b) {
    return border.contains(b);
  }

  public boolean existsEdge(Edge_d e) {
    return edges.contains(e);
  }

  public void addNode(Node n) {
    nodes.addLast(n);
  }

  public void addBorder(Node b) {
    border.addLast(b);
  }

  public void addEdge(Edge_d e) {
    edges.addLast(e);
  }

  public LinkedList getNodes() {
    return nodes;
  }

  public LinkedList getBorder() {
    return border;
  }

  public LinkedList getEdges() {
    return edges;
  }

  public void reset() {
    nodes.clear();
    border.clear();
    edges.clear();
  }


  public boolean allNodesAndBorderStillInCompleteGraph() {
    for( Iterator i = nodes.iterator(); i.hasNext(); ) {
      Node node = (Node) i.next();
      if( !node.inGraph ) {
        return false;
      }
    }
    for( Iterator i = border.iterator(); i.hasNext(); ) {
      Node node = (Node) i.next();
      if( !node.inGraph ) {
        return false;
      }
    }
    return true;
  }

  public HashSet newBad(EdgeGraph mesh) {
    HashSet ret = new HashSet();
    for (Iterator iter = nodes.iterator(); iter.hasNext();) {
      Node node = (Node) iter.next();
      Element element = (Element) mesh.getNodeData(node);
      if (element.isBad())
        ret.add(node);
    }

    return ret;
  }
}
