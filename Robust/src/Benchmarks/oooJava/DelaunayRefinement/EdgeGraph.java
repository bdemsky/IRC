import java.util.Collection;

public interface EdgeGraph
    extends Graph {
		
		public abstract Edge createEdge(Node node, Node node1, Object obj);
		
		public abstract Edge getEdge(Node node, Node node1);
		
		public abstract boolean removeEdge(Edge edge);
		
		public abstract boolean addEdge(Edge edge);
		
		public abstract boolean hasEdge(Edge edge);
		
		public abstract Node getSource(Edge edge);
		
		public abstract Node getDest(Edge edge);
		
		public abstract Collection getOutEdges(Node node);
		
		public abstract Collection getInEdges(Node node);
		
		public abstract Object getEdgeData(Edge edge);
		
		public abstract Object setEdgeData(Edge edge, Object obj);
	}
