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
    connections.clear();
    frontier = new LinkedList();
    centerNode = node;
    for (centerElement = (Element) getNodeData(centerNode); graph.containsNode(centerNode)
        && centerElement.isObtuse();) {
      Edge_d oppositeEdge = getOpposite(centerNode);
      if (getSource(oppositeEdge) == centerNode)
        centerNode = getDest(oppositeEdge);
      else
        centerNode = getSource(oppositeEdge);
      centerElement = (Element) getNodeData(centerNode);
      if (centerNode == null)
        System.exit(-1);
    }

    center = centerElement.center();
    dim = centerElement.getDim();
    pre.addNode(centerNode);
    frontier.addLast(centerNode);
  }

  private Edge_d getOpposite(Node node) {
    Element element = (Element) getNodeData(node);

    System.out.println( "\n  element="+element+" with obtuse="+element.getObtuse() );

    int outNeiCnt = 0;

    for (Iterator iterator = getOutNeighbors(node); iterator.hasNext();) {
      ++outNeiCnt;
      Node neighbor = (Node) iterator.next();
      Edge_d edge = getEdge(node, neighbor);
      ElementEdge edge_data = (ElementEdge) getEdgeData(edge);

      System.out.println( "    is "+edge_data.getPoint(0)+" and "+edge_data.getPoint(1)+
                          " opposite the obtuse?");

      if (element.getObtuse().notEquals(edge_data.getPoint(0)) &&
          element.getObtuse().notEquals(edge_data.getPoint(1))
          )
        return edge;
    }

    int inNeiCnt = 0;

    for (Iterator iterator = ((EdgeGraphNode)node).getInNeighbors(); iterator.hasNext();) {
      ++inNeiCnt;
      Node neighbor = (Node) iterator.next();
    }

    if( outNeiCnt != 3 ) {
      System.out.println( "Get opposite when "+outNeiCnt+" out-neighbors? in-nei="+inNeiCnt );
    }
    
    System.out.println("Error: \"Edge\" in Cavity.java getOpposite(Node)");
    System.exit(-1);
    return null; // it's here so the compiler doesn't complain.
  }

  public boolean isMember(Node node) {
    Element element = (Element) getNodeData(node);
    return element.inCircle(center);
  }

  public void build() {
    while (frontier.size() != 0) {
      Node curr = (Node) frontier.removeFirst();
      for (Iterator iterator = getOutNeighbors(curr); iterator.hasNext();) {
        Node next = (Node) iterator.next();
        Element nextElement = (Element) getNodeData(next);
        Edge_d edge = getEdge(curr, next);
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
      ElementEdge edge = (ElementEdge) getEdgeData(conn);
      Element new_element = new Element(center, edge.getPoint(0), edge.getPoint(1));
      ne_node = graph.createNode(new_element);
      Node ne_connection;
      if (pre.existsNode(getDest(conn)))
        ne_connection = getSource(conn);
      else
        ne_connection = getDest(conn);
      ElementEdge new_edge =
          new_element.getRelatedEdge((Element) getNodeData(ne_connection));
      post.addEdge(createEdge(ne_node, ne_connection, new_edge));

      // Collection postnodes = (Collection)post.getNodes().clone();
      LinkedList postnodes = new LinkedList();
      for (Iterator it = post.getNodes().iterator(); it.hasNext();) {
        postnodes.addLast(it.next());
      }

      for (Iterator iterator1 = postnodes.iterator(); iterator1.hasNext();) {
        Node node = (Node) iterator1.next();
        Element element = (Element) getNodeData(node);
        if (element.isRelated(new_element)) {
          ElementEdge ele_edge = new_element.getRelatedEdge(element);
          post.addEdge(createEdge(ne_node, node, ele_edge));
        }
      }
    }
  }
  
  private Object getNodeData(Node n) {
    return ((EdgeGraphNode) n).data;
  }
  
  public Node getSource(Edge_d e) {
    return ((GraphEdge) e).getSrc();
  }
  
  public Node getDest(Edge_d e) {
    return ((GraphEdge) e).getDest();
  }
  
  public Edge_d getEdge(Node src, Node dest) {
    return ((EdgeGraphNode) src).getOutEdge((EdgeGraphNode) dest);
  }
  
  
  public Edge_d createEdge(Node src, Node dest, Object e) {
    return new GraphEdge((EdgeGraphNode) src, (EdgeGraphNode) dest, e);
  }
  
  public Iterator getOutNeighbors(Node src) {
    return ((EdgeGraphNode) src).getOutNeighbors();
  }
  
  public Object getEdgeData(Edge_d e) {
    return ((GraphEdge) e).d;
  }
}
