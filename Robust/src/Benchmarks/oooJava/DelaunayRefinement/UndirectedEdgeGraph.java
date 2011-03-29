import java.util.HashMap;


public class UndirectedEdgeGraph 
extends DirectedEdgeGraph 
{
    protected class UndirectedEdgeGraphNode 
    extends DirectedEdgeGraph.EdgeGraphNode
        implements Comparable {
			
			public int compareTo(UndirectedEdgeGraphNode n) {
				return n.hashCode() - hashCode();
			}
			
			public int compareTo(Object obj) {
				return compareTo((UndirectedEdgeGraphNode)obj);
			}
			
			final UndirectedEdgeGraph this$0;
			
			UndirectedEdgeGraphNode(Object d) {
        super(UndirectedEdgeGraph.this);
				this$0 = UndirectedEdgeGraph.this;
				data = d;
				inEdges = new HashMap();
				outEdges = inEdges;
			}
		}
	
	
    public UndirectedEdgeGraph() {
    }
	
    public Edge createEdge(Node src, Node dest, Object e) {
        UndirectedEdgeGraphNode gsrc = (UndirectedEdgeGraphNode)src;
        UndirectedEdgeGraphNode gdest = (UndirectedEdgeGraphNode)dest;
        if(gsrc.compareTo(gdest) > 0)
            return new DirectedEdgeGraph.GraphEdge(gsrc, gdest, e);
        else
            return new DirectedEdgeGraph.GraphEdge(gdest, gsrc, e);
    }
	
    public UndirectedEdgeGraphNode createNode(Object n) {
        return new UndirectedEdgeGraphNode(n);
    }
	
    public boolean isDirected() {
        return false;
    }
}
