import java.util.Collection;
import java.util.Iterator;

public interface Graph
    extends Iterable {
		
		public abstract Node createNode(Object obj);
		
		public abstract boolean addNode(Node node);
		
		public abstract boolean removeNode(Node node);
		
		public abstract boolean containsNode(Node node);
		
		public abstract Node getRandom();
		
		public abstract boolean addNeighbor(Node node, Node node1);
		
		public abstract boolean removeNeighbor(Node node, Node node1);
		
		public abstract boolean hasNeighbor(Node node, Node node1);
		
		public abstract Collection getInNeighbors(Node node);
		
		public abstract Collection getOutNeighbors(Node node);
		
		public abstract int getNumNodes();
		
		public abstract Object getNodeData(Node node);
		
		public abstract Object setNodeData(Node node, Object obj);
		
		public abstract boolean isDirected();
		
		public abstract Iterator iterator();
	}
