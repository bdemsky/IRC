import java.util.*;

public class DirectedGraph
    implements Graph {
		protected class GraphNode
			implements Node {
				
				public Object getData() {
					return getNodeData(this);
				}
				
				public Object setData(Object n) {
					return setNodeData(this, n);
				}
				
				public final boolean addInNeighbor(GraphNode n) {
					if(inNeighbors.contains(n)) {
						return false;
					} else {
						inNeighbors.add(n);
						return true;
					}
				}
				
				public final boolean removeInNeighbor(GraphNode n) {
					return inNeighbors.remove(n);
				}
				
				public final boolean hasInNeighbor(GraphNode n) {
					return inNeighbors.contains(n);
				}
				
				public final Collection getInNeighbors() {
					return inNeighbors;
				}
				
				public final Collection getInNeighborsCopy() {
					return new ArrayList(inNeighbors);
				}
				
				public final boolean addOutNeighbor(GraphNode n) {
					if(outNeighbors.contains(n)) {
						return false;
					} else {
						outNeighbors.add(n);
						return true;
					}
				}
				
				public final boolean removeOutNeighbor(GraphNode n) {
					return outNeighbors.remove(n);
				}
				
				public final boolean hasOutNeighbor(GraphNode n) {
					return outNeighbors.contains(n);
				}
				
				public final Collection getOutNeighbors() {
					return outNeighbors;
				}
				
				public final Collection getOutNeighborsCopy() {
					return new ArrayList(outNeighbors);
				}
				
				protected Object data;
				protected List inNeighbors;
				protected List outNeighbors;
				final DirectedGraph this$0;
				
				protected GraphNode() {
				  super();
					this$0 = DirectedGraph.this;
				}
				
				public GraphNode(Object n) {
          super();
					this$0 = DirectedGraph.this;
					data = n;
					inNeighbors = new ArrayList();
					outNeighbors = new ArrayList();
				}
			}
		
		
		public DirectedGraph() {
			nodes = Collections.synchronizedSet(new HashSet());
		}
		
		public boolean addNeighbor(Node src, Node dest) {
			GraphNode src_c = (GraphNode)src;
			GraphNode dest_c = (GraphNode)dest;
			return src_c.addOutNeighbor(dest_c) ? dest_c.addInNeighbor(src_c) : false;
		}
		
		public boolean addNode(Node n) {
			return nodes.add((GraphNode)n);
		}
		
		public boolean containsNode(Node n) {
			return nodes.contains(n);
		}
		
		public Node createNode(Object n) {
			return new GraphNode(n);
		}
		
		public Collection getInNeighbors(Node src) {
			GraphNode src_c = (GraphNode)src;
			return Collections.unmodifiableCollection(src_c.getInNeighbors());
		}
		
		public int getNumNodes() {
			return nodes.size();
		}
		
		public Collection getOutNeighbors(Node src) {
			GraphNode src_c = (GraphNode)src;
			return Collections.unmodifiableCollection(src_c.getOutNeighbors());
		}
		
		public Node getRandom() {
			return (Node)Sets.getAny(nodes);
		}
		
		public boolean hasNeighbor(Node src, Node dest) {
			GraphNode src_c = (GraphNode)src;
			GraphNode dest_c = (GraphNode)dest;
			return src_c.hasOutNeighbor(dest_c);
		}
		
		public boolean removeNeighbor(Node src, Node dest) {
			GraphNode src_c = (GraphNode)src;
			GraphNode dest_c = (GraphNode)dest;
			return src_c.removeOutNeighbor(dest_c) ? dest_c.removeInNeighbor(src_c) : false;
		}
		
		public boolean removeNode(Node n) {
			removeConnectingEdges((GraphNode)n);
			return nodes.remove(n);
		}
		
		protected void removeConnectingEdges(GraphNode n) {
			Collection outNeighbors = n.getOutNeighborsCopy();
			GraphNode g;
			for(Iterator iterator1 = outNeighbors.iterator(); iterator1.hasNext(); removeNeighbor(n, g)) {
				g = (GraphNode)iterator1.next();
			}
			
			Collection inNeighbors = n.getInNeighborsCopy();
			for(Iterator iterator2 = inNeighbors.iterator(); iterator2.hasNext(); removeNeighbor(g, n)) {
				g = (GraphNode)iterator2.next();
			}
			
		}
		
		public Object getNodeData(Node n) {
			return ((GraphNode)n).data;
		}
		
		public Object setNodeData(Node n, Object d) {
			GraphNode gn = (GraphNode)n;
			Object retval = gn.data;
			gn.data = d;
			return retval;
		}
		
		public Iterator iterator() {
			return nodes.iterator();
		}
		
		public boolean isDirected() {
			return true;
		}
		
		protected Set nodes;
	}
