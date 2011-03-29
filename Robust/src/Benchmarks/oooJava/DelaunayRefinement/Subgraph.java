import java.util.*;

public class Subgraph {
	
    public Subgraph() {
    }
	
    public boolean existsNode(Node n) {
        return nodes.contains(n);
    }
	
    public boolean existsBorder(Node b) {
        return border.contains(b);
    }
	
    public boolean existsEdge(Edge e) {
        return edges.contains(e);
    }
	
    public boolean addNode(Node n) {
        return nodes.add(n);
    }
	
    public boolean addBorder(Node b) {
        return border.add(b);
    }
	
    public void addEdge(Edge e) {
        edges.add(e);
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
	
    public HashSet newBad(EdgeGraph mesh) {
        HashSet ret = new HashSet();
        for(Iterator iter = nodes.iterator(); iter.hasNext();) {
            Node node = (Node)iter.next();
            Element element = (Element)mesh.getNodeData(node);
            if(element.isBad())
                ret.add(node);
        }
		
        return ret;
    }
	
    private final LinkedList nodes = new LinkedList();
    private final LinkedList border = new LinkedList();
    private final LinkedList edges = new LinkedList();
}
