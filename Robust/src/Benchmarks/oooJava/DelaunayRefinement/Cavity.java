import java.util.*;

public class Cavity {
	
    public Cavity(EdgeGraph mesh) {
        center = null;
        graph = mesh;
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
        frontier.clear();
        centerNode = node;
        for(centerElement = (Element)graph.getNodeData(centerNode); graph.containsNode(centerNode) && centerElement.isObtuse();) {
            Edge oppositeEdge = getOpposite(centerNode);
            if(graph.getSource(oppositeEdge) == centerNode)
                centerNode = graph.getDest(oppositeEdge);
            else
                centerNode = graph.getSource(oppositeEdge);
            centerElement = (Element)graph.getNodeData(centerNode);
            if(centerNode == null)
                System.exit(-1);
        }
		
        center = centerElement.center();
        dim = centerElement.getDim();
        pre.addNode(centerNode);
        frontier.add(centerNode);
    }
	
    private Edge getOpposite(Node node) {
        Element element = (Element)graph.getNodeData(node);
        Collection neighbors = graph.getOutNeighbors(node);
        if(neighbors.size() != 3)
            throw new Error(String.format("neighbors %d", new Object[] {
										  Integer.valueOf(neighbors.size())
										  }));
        for(Iterator iterator = neighbors.iterator(); iterator.hasNext();) {
            Node neighbor = (Node)iterator.next();
            Edge edge = graph.getEdge(node, neighbor);
            Element.Edge edge_data = (Element.Edge)graph.getEdgeData(edge);
            if(element.getObtuse().notEquals(edge_data.getPoint(0)) && element.getObtuse().notEquals(edge_data.getPoint(1)))
                return edge;
        }
		
        throw new Error("edge");
    }
	
    public boolean isMember(Node node) {
        Element element = (Element)graph.getNodeData(node);
        return element.inCircle(center);
    }
	
    public void build() {
        while(frontier.size() != 0)  {
            Node curr = (Node)frontier.poll();
            Collection neighbors = graph.getOutNeighbors(curr);
            for(Iterator iterator = neighbors.iterator(); iterator.hasNext();) {
                Node next = (Node)iterator.next();
                Element nextElement = (Element)graph.getNodeData(next);
                Edge edge = graph.getEdge(curr, next);
                if((dim != 2 || nextElement.getDim() != 2 || next == centerNode) && isMember(next)) {
                    if(nextElement.getDim() == 2 && dim != 2) {
                        initialize(next);
                        build();
                        return;
                    }
                    if(!pre.existsNode(next)) {
                        pre.addNode(next);
                        pre.addEdge(edge);
                        frontier.add(next);
                    }
                } else
					if(!connections.contains(edge)) {
						connections.add(edge);
						pre.addBorder(next);
					}
            }
			
        }
    }
	
    public void update() {
        if(centerElement.getDim() == 2) {
            Element ele1 = new Element(center, centerElement.getPoint(0));
            Node node1 = graph.createNode(ele1);
            post.addNode(node1);
            Element ele2 = new Element(center, centerElement.getPoint(1));
            Node node2 = graph.createNode(ele2);
            post.addNode(node2);
        }
        Node ne_node;
        for(Iterator iterator = connections.iterator(); iterator.hasNext(); post.addNode(ne_node)) {
            Edge conn = (Edge)iterator.next();
            Element.Edge edge = (Element.Edge)graph.getEdgeData(conn);
            Element new_element = new Element(center, edge.getPoint(0), edge.getPoint(1));
            ne_node = graph.createNode(new_element);
            Node ne_connection;
            if(pre.existsNode(graph.getDest(conn)))
                ne_connection = graph.getSource(conn);
            else
                ne_connection = graph.getDest(conn);
            Element.Edge new_edge = new_element.getRelatedEdge((Element)graph.getNodeData(ne_connection));
            post.addEdge(graph.createEdge(ne_node, ne_connection, new_edge));
            Collection postnodes = (Collection)post.getNodes().clone();
            for(Iterator iterator1 = postnodes.iterator(); iterator1.hasNext();) {
                Node node = (Node)iterator1.next();
                Element element = (Element)graph.getNodeData(node);
                if(element.isRelated(new_element)) {
                    Element.Edge ele_edge = new_element.getRelatedEdge(element);
                    post.addEdge(graph.createEdge(ne_node, node, ele_edge));
                }
            }
			
        }
		
    }
	
    protected Tuple center;
    protected Node centerNode;
    protected Element centerElement;
    protected int dim;
    protected final Queue frontier = new LinkedList();
    protected final Subgraph pre = new Subgraph();
    protected final Subgraph post = new Subgraph();
    private final EdgeGraph graph;
    protected final HashSet connections = new HashSet();
}
