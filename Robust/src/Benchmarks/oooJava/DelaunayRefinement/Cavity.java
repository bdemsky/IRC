public class Cavity {
  protected Tuple center;
  protected Node centerNode;
  protected Element centerElement;
  protected int dim;
  protected LinkedList frontier;
  protected Subgraph pre = new Subgraph();
  protected Subgraph post = new Subgraph();
  private final EdgeGraph graph;
  protected HashSet connections;

  public Cavity(EdgeGraph mesh) {
    center = null;
    graph = mesh;
    connections = new HashSet();
    frontier = new LinkedList();
  }

  public Subgraph getPre() {
    return pre;
  }

  public Subgraph getPost() {
    return post;
  }

  public void triggerAbort() {
  }

  public void triggerBorderConflict() {
  }

  public void initialize(Node node) {
    pre.reset();
    post.reset();
    connections = new HashSet();
    frontier = new LinkedList();
    centerNode = node;
    for (centerElement = (Element) graph.getNodeData(centerNode); graph.containsNode(centerNode)
        && centerElement.isObtuse();) {
      Edge_d oppositeEdge = getOpposite(centerNode);
      if (graph.getSource(oppositeEdge) == centerNode)
        centerNode = graph.getDest(oppositeEdge);
      else
        centerNode = graph.getSource(oppositeEdge);
      centerElement = (Element) graph.getNodeData(centerNode);
      if (centerNode == null)
        System.exit(-1);
    }

    center = centerElement.center();
    dim = centerElement.getDim();
    pre.addNode(centerNode);
    frontier.addLast(centerNode);
  }

  private Edge_d getOpposite(Node node) {
    Element element = (Element) graph.getNodeData(node);

    // Don't think we'd run into it but..
    // TODO check this.
    // if(neighbors.size() != 3)
    // throw new Error(String.format("neighbors %d", new Object[] {
    // Integer.valueOf(neighbors.size())
    // }));

    for (Iterator iterator = graph.getOutNeighbors(node); iterator.hasNext();) {
      Node neighbor = (Node) iterator.next();
      Edge_d edge = graph.getEdge(node, neighbor);
      ElementEdge edge_data = (ElementEdge) graph.getEdgeData(edge);
      if (element.getObtuse().notEquals(edge_data.getPoint(0))
          && element.getObtuse().notEquals(edge_data.getPoint(1)))
        return edge;
    }

    System.out.println("Error: \"Edge\" in Cavity.java getOpposite(Node)");
    System.exit(-1);
    return null; // it's here so the compiler doesn't complain.
  }

  public boolean isMember(Node node) {
    Element element = (Element) graph.getNodeData(node);
    return element.inCircle(center);
  }

  public void build() {
    while (frontier.size() != 0) {
      Node curr = (Node) frontier.removeFirst();
      for (Iterator iterator = graph.getOutNeighbors(curr); iterator.hasNext();) {
        Node next = (Node) iterator.next();
        Element nextElement = (Element) graph.getNodeData(next);
        Edge_d edge = graph.getEdge(curr, next);
        if ((dim != 2 || nextElement.getDim() != 2 || next == centerNode) && isMember(next)) {
          if (nextElement.getDim() == 2 && dim != 2) {
            initialize(next);
            build();
            return;
          }
          if (!pre.existsNode(next)) {
            pre.addNode(next);
            pre.addEdge(edge);
            frontier.addLast(next);
          }
        } else if (!connections.contains(edge)) {
          connections.add(edge);
          pre.addBorder(next);
        }
      }

    }
  }

  public void update() {
    if (centerElement.getDim() == 2) {
      Element ele1 = new Element(center, centerElement.getPoint(0));
      Node node1 = graph.createNode(ele1);
      post.addNode(node1);
      Element ele2 = new Element(center, centerElement.getPoint(1));
      Node node2 = graph.createNode(ele2);
      post.addNode(node2);
    }
    Node ne_node;
    for (HashMapIterator iterator = connections.iterator(); iterator.hasNext(); post.addNode(ne_node)) {
      Edge_d conn = (Edge_d) iterator.next();
      ElementEdge edge = (ElementEdge) graph.getEdgeData(conn);
      Element new_element = new Element(center, edge.getPoint(0), edge.getPoint(1));
      ne_node = graph.createNode(new_element);
      Node ne_connection;
      if (pre.existsNode(graph.getDest(conn)))
        ne_connection = graph.getSource(conn);
      else
        ne_connection = graph.getDest(conn);
      ElementEdge new_edge =
          new_element.getRelatedEdge((Element) graph.getNodeData(ne_connection));
      post.addEdge(graph.createEdge(ne_node, ne_connection, new_edge));

      // Collection postnodes = (Collection)post.getNodes().clone();
      LinkedList postnodes = new LinkedList();
      for (Iterator it = post.getNodes().iterator(); it.hasNext();) {
        postnodes.addLast(it.next());
      }

      for (Iterator iterator1 = postnodes.iterator(); iterator1.hasNext();) {
        Node node = (Node) iterator1.next();
        Element element = (Element) graph.getNodeData(node);
        if (element.isRelated(new_element)) {
          ElementEdge ele_edge = new_element.getRelatedEdge(element);
          post.addEdge(graph.createEdge(ne_node, node, ele_edge));
        }
      }
    }
  }
}
