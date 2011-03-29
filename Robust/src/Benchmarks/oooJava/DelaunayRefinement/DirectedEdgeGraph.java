import java.util.*;

public class DirectedEdgeGraph
    implements EdgeGraph {
		protected class EdgeGraphNode
			implements Node {
				
				protected final boolean hasInNeighbor(EdgeGraphNode n) {
					return inEdges.containsKey(n);
				}
				
				protected boolean addInEdge(EdgeGraphNode n, GraphEdge e) {
					if(hasInNeighbor(n)) {
						return false;
					} else {
						inEdges.put(n, e);
						return true;
					}
				}
				
				protected boolean removeInEdge(EdgeGraphNode n) {
					if(!hasInNeighbor(n)) {
						return false;
					} else {
						inEdges.remove(n);
						return true;
					}
				}
				
				protected GraphEdge getInEdge(EdgeGraphNode n) {
					return (GraphEdge)inEdges.get(n);
				}
				
				protected Collection getInEdges() {
					return inEdges.values();
				}
				
				protected final Collection getInNeighbors() {
					return inEdges.keySet();
				}
				
				protected final Collection getInNeighborsCopy() {
					return new ArrayList(inEdges.keySet());
				}
				
				protected final boolean hasOutNeighbor(EdgeGraphNode n) {
					return outEdges.containsKey(n);
				}
				
				protected boolean addOutEdge(EdgeGraphNode n, GraphEdge e) {
					if(hasOutNeighbor(n)) {
						return false;
					} else {
						outEdges.put(n, e);
						return true;
					}
				}
				
				protected boolean removeOutEdge(EdgeGraphNode n) {
					if(!hasOutNeighbor(n)) {
						return false;
					} else {
						outEdges.remove(n);
						return true;
					}
				}
				
				protected GraphEdge getOutEdge(EdgeGraphNode n) {
					return (GraphEdge)outEdges.get(n);
				}
				
				protected Collection getOutEdges() {
					return outEdges.values();
				}
				
				protected final Collection getOutNeighbors() {
					return outEdges.keySet();
				}
				
				protected final Collection getOutNeighborsCopy() {
					return new ArrayList(outEdges.keySet());
				}
				
				public Object getData() {
					return getNodeData(this);
				}
				
				public Object setData(Object n) {
					return setNodeData(this, n);
				}
				
				protected Map inEdges;
				protected Map outEdges;
				protected Object data;
				final DirectedEdgeGraph this$0;
				
				EdgeGraphNode() {
				  super();
					this$0 = DirectedEdgeGraph.this;
				}
				
				EdgeGraphNode(Object d) {
          super();
					this$0 = DirectedEdgeGraph.this;
					inEdges = new HashMap();
					outEdges = new HashMap();
					data = d;
				}
			}
		
		protected class GraphEdge
			implements Edge {
				
				protected final EdgeGraphNode getOpposite(EdgeGraphNode n) {
					return n != src ? src : dest;
				}
				
				protected final EdgeGraphNode getSrc() {
					return src;
				}
				
				protected final EdgeGraphNode getDest() {
					return dest;
				}
				
				public Object getData() {
					return getEdgeData(this);
				}
				
				public Object setData(Object e) {
					return setEdgeData(this, e);
				}
				
				protected EdgeGraphNode src;
				protected EdgeGraphNode dest;
				protected Object d;
				final DirectedEdgeGraph this$0;
				
				public GraphEdge(Object d) {
				  super();
					this$0 = DirectedEdgeGraph.this;
					this.d = d;
				}
				
				public GraphEdge(EdgeGraphNode src, EdgeGraphNode dest, Object d) {
					this(d);
					this.src = src;
					this.dest = dest;
				}
			}
		
		
		public DirectedEdgeGraph() {
			nodes = Collections.synchronizedSet(new HashSet());
		}
		
		public boolean addEdge(Edge e) {
			GraphEdge ge = (GraphEdge)e;
			EdgeGraphNode src = ge.getSrc();
			EdgeGraphNode dest = ge.getDest();
			return src.addOutEdge(dest, ge) ? dest.addInEdge(src, ge) : false;
		}
		
		public Edge createEdge(Node src, Node dest, Object e) {
			return new GraphEdge((EdgeGraphNode)src, (EdgeGraphNode)dest, e);
		}
		
		public Node getDest(Edge e) {
			return ((GraphEdge)e).getDest();
		}
		
		public Edge getEdge(Node src, Node dest) {
			return ((EdgeGraphNode)src).getOutEdge((EdgeGraphNode)dest);
		}
		
		public Collection getInEdges(Node n) {
			return ((EdgeGraphNode)n).getInEdges();
		}
		
		public Collection getOutEdges(Node n) {
			return ((EdgeGraphNode)n).getOutEdges();
		}
		
		public Node getSource(Edge e) {
			return ((GraphEdge)e).src;
		}
		
		public boolean hasEdge(Edge e) {
			GraphEdge ge = (GraphEdge)e;
			return ge.getSrc().hasOutNeighbor(ge.getDest());
		}
		
		public boolean removeEdge(Edge e) {
			GraphEdge ge = (GraphEdge)e;
			EdgeGraphNode src = ge.getSrc();
			EdgeGraphNode dest = ge.getDest();
			return src.removeOutEdge(dest) ? dest.removeInEdge(src) : false;
		}
		
		public boolean addNeighbor(Node src, Node dest) {
			throw new UnsupportedOperationException("addNeighbor not supported in EdgeGraphs. Use createEdge/addEdge instead");
		}
		
		public Node createNode(Object n) {
			return new EdgeGraphNode(n);
		}
		
		public Collection getInNeighbors(Node src) {
			return ((EdgeGraphNode)src).getInNeighbors();
		}
		
		public Collection getOutNeighbors(Node src) {
			return ((EdgeGraphNode)src).getOutNeighbors();
		}
		
		public boolean removeNeighbor(Node src, Node dest) {
			EdgeGraphNode gsrc = (EdgeGraphNode)src;
			EdgeGraphNode gdest = (EdgeGraphNode)dest;
			return gsrc.removeOutEdge(gdest) ? gdest.removeInEdge(gsrc) : false;
		}
		
		public Object getEdgeData(Edge e) {
			return ((GraphEdge)e).d;
		}
		
		public Object setEdgeData(Edge e, Object d) {
			GraphEdge ge = (GraphEdge)e;
			Object retval = ge.d;
			ge.d = d;
			return retval;
		}
		
		public Iterator iterator() {
			return nodes.iterator();
		}
		
		public boolean addNode(Node n) {
			return nodes.add((EdgeGraphNode)n);
		}
		
		public boolean containsNode(Node n) {
			return nodes.contains(n);
		}
		
		public Object getNodeData(Node n) {
			EdgeGraphNode egn = (EdgeGraphNode)n;
			return egn.data;
		}
		
		public int getNumNodes() {
			return nodes.size();
		}
		
		public Node getRandom() {
			return (Node)Sets.getAny(nodes);
		}
		
		public boolean hasNeighbor(Node src, Node dest) {
			EdgeGraphNode esrc = (EdgeGraphNode)src;
			EdgeGraphNode edest = (EdgeGraphNode)dest;
			return esrc.hasOutNeighbor(edest);
		}
		
		public boolean removeNode(Node n) {
			removeConnectingEdges((EdgeGraphNode)n);
			return nodes.remove(n);
		}
		
		protected void removeConnectingEdges(EdgeGraphNode n) {
			Collection outNeighbors = n.getOutNeighborsCopy();
			EdgeGraphNode g;
			for(Iterator iterator1 = outNeighbors.iterator(); iterator1.hasNext(); removeNeighbor(n, g))
				g = (EdgeGraphNode)iterator1.next();
			
			Collection inNeighbors = n.getInNeighborsCopy();
			for(Iterator iterator2 = inNeighbors.iterator(); iterator2.hasNext(); removeNeighbor(g, n))
				g = (EdgeGraphNode)iterator2.next();
			
		}
		
		public Object setNodeData(Node n, Object d) {
			EdgeGraphNode egn = (EdgeGraphNode)n;
			Object retval = egn.data;
			egn.data = d;
			return retval;
		}
		
		public boolean isDirected() {
			return true;
		}
		
		Set nodes;
	}
