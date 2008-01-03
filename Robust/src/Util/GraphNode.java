package Util;

import java.util.*;
import java.io.*;

public class GraphNode {
    /* NodeStatus enumeration pattern ***********/

    public static final NodeStatus UNVISITED = new NodeStatus("UNVISITED");
    public static final NodeStatus PROCESSING = new NodeStatus("PROCESSING");
    public static final NodeStatus FINISHED = new NodeStatus("FINISHED");
    
    public static class NodeStatus {
        private static String name;
        private NodeStatus(String name) { this.name = name; }
        public String toString() { return name; }
    }

    int discoverytime = -1;
    int finishingtime = -1; /* used for searches */

    protected Vector edges = new Vector();
    protected Vector inedges = new Vector();

    NodeStatus status = UNVISITED;
    String dotnodeparams = new String();
    public boolean merge=false;

    public void setMerge() {
	merge=true;
    }

    public static void computeclosure(Collection nodes, Collection removed) {
	Stack tovisit=new Stack();
	tovisit.addAll(nodes);
	while(!tovisit.isEmpty()) {
	    GraphNode gn=(GraphNode)tovisit.pop();
	    for(Iterator it=gn.edges();it.hasNext();) {
		Edge edge=(Edge)it.next();
		GraphNode target=edge.getTarget();
		if (!nodes.contains(target)) {
		    if ((removed==null)||
			(!removed.contains(target))) {
			nodes.add(target);
			tovisit.push(target);
		    }
		}
	    }
	}
    }

    public static void boundedcomputeclosure(Collection nodes, Collection removed,int depth) {
	Stack tovisit=new Stack();
	Stack newvisit=new Stack();
	tovisit.addAll(nodes);
	for(int i=0;i<depth&&!tovisit.isEmpty();i++) {
	    while(!tovisit.isEmpty()) {
		GraphNode gn=(GraphNode)tovisit.pop();
		for(Iterator it=gn.edges();it.hasNext();) {
		    Edge edge=(Edge)it.next();
		    GraphNode target=edge.getTarget();
		    if (!nodes.contains(target)) {
			if ((removed==null)||
			    (!removed.contains(target))) {
			    nodes.add(target);
			    newvisit.push(target);
			}
		    }
		}
	    }
	    tovisit=newvisit;
	    newvisit=new Stack();
	}
    }

    public void setDotNodeParameters(String param) {
        if (param == null) {
            throw new NullPointerException();
        }
        if (dotnodeparams.length() > 0) {
            dotnodeparams += "," + param;
        } else {
            dotnodeparams = param;
        }
    }

    public void setStatus(NodeStatus status) {
        if (status == null) {
            throw new NullPointerException();
        }
        this.status = status;
    }
       
    public String getLabel() {
	return "";
    }

    public String getTextLabel() {
	return "";
    }
    
   	public String getName(){
	   	return "";
   	}

    public NodeStatus getStatus() {
        return this.status;
    }

    public int numedges() {
	return edges.size();
    }

    public int numinedges() {
	return inedges.size();
    }

    public Edge getedge(int i) {
	return (Edge) edges.get(i);
    }

    public Edge getinedge(int i) {
	return (Edge) inedges.get(i);
    }

    public Vector getEdgeVector() {
	return edges;
    }

    public Vector getInedgeVector() {
	return inedges;
    }

    public Iterator edges() {
        return edges.iterator();
    }

    public Iterator inedges() {
        return inedges.iterator();
    }
    
    public void addEdge(Edge newedge) {
	newedge.setSource(this);
        edges.addElement(newedge);
	GraphNode tonode=newedge.getTarget();
	tonode.inedges.addElement(newedge);
    }

    public void addEdge(Vector v) {
	for (Iterator it = v.iterator(); it.hasNext();)
	    addEdge((Edge)it.next());
    }

    public void removeEdge(Edge edge) {
	edges.remove(edge);
    }

    public void removeInedge(Edge edge) {
	inedges.remove(edge);
    }

    public void removeAllEdges() {
	edges.removeAllElements();
    }

    public void removeAllInedges() {
	inedges.removeAllElements();
    }
    void reset() {
	    discoverytime = -1;
	    finishingtime = -1;
	    status = UNVISITED;
    }

    void resetscc() {
	status = UNVISITED;
    }

    void discover(int time) {
	discoverytime = time;
	status = PROCESSING;
    }

    void finish(int time) {
        assert status == PROCESSING;
	finishingtime = time;
        status = FINISHED;
    }

    /** Returns finishing time for dfs */

    public int getFinishingTime() {
	return finishingtime;
    }


    public static class DOTVisitor {

        java.io.PrintWriter output;
        int tokennumber;
        int color;
	Vector namers;

        private DOTVisitor(java.io.OutputStream output, Vector namers) {
            tokennumber = 0;
            color = 0;
            this.output = new java.io.PrintWriter(output, true);
	    this.namers=namers;
        }

        private String getNewID(String name) {
            tokennumber = tokennumber + 1;
            return new String (name+tokennumber);
        }

        Collection nodes;


        public static void visit(java.io.OutputStream output, Collection nodes, Vector namers) {
            DOTVisitor visitor = new DOTVisitor(output, namers);
            visitor.nodes = nodes;
            visitor.make();
        }

        public static void visit(java.io.OutputStream output, Collection nodes) {
	    Vector v=new Vector();
	    v.add(new Namer());
            DOTVisitor visitor = new DOTVisitor(output, v);
            visitor.nodes = nodes;
            visitor.make();
        }

        private void make() {
            output.println("digraph dotvisitor {");
	    /*            output.println("\tpage=\"8.5,11\";");
			  output.println("\tnslimit=1000.0;");
			  output.println("\tnslimit1=1000.0;");
			  output.println("\tmclimit=1000.0;");
			  output.println("\tremincross=true;");*/
            output.println("\tnode [fontsize=10,height=\"0.1\", width=\"0.1\"];");
            output.println("\tedge [fontsize=6];");
	    traverse();
            output.println("}\n");
        }



        private void traverse() {
	    Set cycleset=GraphNode.findcycles(nodes);

            Iterator it = nodes.iterator();
            while (it.hasNext()) {
                GraphNode gn = (GraphNode) it.next();
                Iterator edges = gn.edges();
                String label = "";
		String dotnodeparams="";

		for(int i=0;i<namers.size();i++) {
		    Namer name=(Namer) namers.get(i);
		    String newlabel=name.nodeLabel(gn);
		    String newparams=name.nodeOption(gn);

		    if (!newlabel.equals("") && !label.equals("")) {
			label+=", ";
		    }
		    if (!newparams.equals("")) {
			dotnodeparams+=", " + name.nodeOption(gn);
		    }
		    label+=name.nodeLabel(gn);
		}

		if (!gn.merge)
                    output.println("\t" + gn.getLabel() + " [label=\"" + label + "\"" + dotnodeparams + "];");

		if (!gn.merge)
                while (edges.hasNext()) {
                    Edge edge = (Edge) edges.next();
                    GraphNode node = edge.getTarget();
		    if (nodes.contains(node)) {
			for(Iterator nodeit=nonmerge(node).iterator();nodeit.hasNext();) {
			    GraphNode node2=(GraphNode)nodeit.next();
			    String edgelabel = "";
			    String edgedotnodeparams="";

			    for(int i=0;i<namers.size();i++) {
				Namer name=(Namer) namers.get(i);
				String newlabel=name.edgeLabel(edge);
				String newoption=name.edgeOption(edge);
				if (!newlabel.equals("")&& ! edgelabel.equals(""))
				    edgelabel+=", ";
				edgelabel+=newlabel;
				if (!newoption.equals(""))
				    edgedotnodeparams+=", "+newoption;
			    }
			    
			    output.println("\t" + gn.getLabel() + " -> " + node2.getLabel() + " [" + "label=\"" + edgelabel + "\"" + edgedotnodeparams + "];");
			}
		    }
                }
            }
        }

	Set nonmerge(GraphNode gn) {
	    HashSet newset=new HashSet();
	    HashSet toprocess=new HashSet();
	    toprocess.add(gn);
	    while(!toprocess.isEmpty()) {
		GraphNode gn2=(GraphNode)toprocess.iterator().next();
		toprocess.remove(gn2);
		if (!gn2.merge)
		    newset.add(gn2);
		else {
		    Iterator edges = gn2.edges();
		    while (edges.hasNext()) {
			Edge edge = (Edge) edges.next();
			GraphNode node = edge.getTarget();
			if (!newset.contains(node)&&nodes.contains(node))
			    toprocess.add(node);
		    }
		}
	    }
	    return newset;
	}

    }

    /** This function returns the set of nodes involved in cycles.
     *	It only considers cycles containing nodes in the set 'nodes'.
    */
    public static Set findcycles(Collection nodes) {
	HashSet cycleset=new HashSet();
	SCC scc=DFS.computeSCC(nodes);
	if (!scc.hasCycles())
	    return cycleset;
	for(int i=0;i<scc.numSCC();i++) {
	    if (scc.hasCycle(i))
		cycleset.addAll(scc.getSCC(i));
	}
	return cycleset;
    }

    public static class SCC {
	boolean acyclic;
	HashMap map,revmap;
	int numscc;
	public SCC(boolean acyclic, HashMap map,HashMap revmap,int numscc) {
	    this.acyclic=acyclic;
	    this.map=map;
	    this.revmap=revmap;
	    this.numscc=numscc;
	}

	/** Returns whether the graph has any cycles */
	public boolean hasCycles() {
	    return !acyclic;
	}

	/** Returns the number of Strongly Connected Components */
	public int numSCC() {
	    return numscc;
	}

	/** Returns the strongly connected component number for the GraphNode gn*/
	public int getComponent(GraphNode gn) {
	    return ((Integer)revmap.get(gn)).intValue();
	}

	/** Returns the set of nodes in the strongly connected component i*/
	public Set getSCC(int i) {
	    Integer scc=new Integer(i);
	    return (Set)map.get(scc);
	}

	/** Returns whether the strongly connected component i contains a cycle */
	boolean hasCycle(int i) {
	    Integer scc=new Integer(i);
	    Set s=(Set)map.get(scc);
	    if (s.size()>1)
		return true;
	    Object [] array=s.toArray();
	    GraphNode gn=(GraphNode)array[0];
	    for(Iterator it=gn.edges();it.hasNext();) {
		Edge e=(Edge)it.next();
		if (e.getTarget()==gn)
		    return true; /* Self Cycle */
	    }
	    return false;
	}
    }

    /**
     * DFS encapsulates the depth first search algorithm
     */
    public static class DFS {

        int time = 0;
	int sccindex = 0;
        Collection nodes;
	Vector finishingorder=null;
	Vector finishingorder_edge = null; 
	int edgetime = 0; 
	HashMap sccmap;
	HashMap sccmaprev;

        private DFS(Collection nodes) {
            this.nodes = nodes;
        }
	/** Calculates the strong connected components for the graph composed
	 *  of the set of nodes 'nodes'*/
	public static SCC computeSCC(Collection nodes) {
	    if (nodes==null) {
		throw new NullPointerException();
	    }
	    DFS dfs=new DFS(nodes);
	    dfs.sccmap=new HashMap();
	    dfs.sccmaprev=new HashMap();
	    dfs.finishingorder=new Vector();
	    boolean acyclic=dfs.go();
            for (Iterator it = nodes.iterator();it.hasNext();) {
                GraphNode gn = (GraphNode) it.next();
                gn.resetscc();
            }
	    for(int i=dfs.finishingorder.size()-1;i>=0;i--) {
		GraphNode gn=(GraphNode)dfs.finishingorder.get(i);
		if (gn.getStatus() == UNVISITED) {
		    dfs.dfsprev(gn);
		    dfs.sccindex++; /* Increment scc index */
		}
	    }
	    return new SCC(acyclic,dfs.sccmap,dfs.sccmaprev,dfs.sccindex);
	}

	void dfsprev(GraphNode gn) {
	    if (gn.getStatus()==FINISHED||!nodes.contains(gn))
		return;
	    gn.setStatus(FINISHED);
	    Integer i=new Integer(sccindex);
	    if (!sccmap.containsKey(i))
		sccmap.put(i,new HashSet());
	    ((Set)sccmap.get(i)).add(gn);
	    sccmaprev.put(gn,i);
	    for(Iterator edgeit=gn.inedges();edgeit.hasNext();) {
		Edge e=(Edge)edgeit.next();
		GraphNode gn2=e.getSource();
		dfsprev(gn2);
	    }
	}

        public static boolean depthFirstSearch(Collection nodes) {
            if (nodes == null) {
                throw new NullPointerException();
            }

            DFS dfs = new DFS(nodes);
            return dfs.go();
        }

        private boolean go() {
            Iterator i;
            time = 0;
            edgetime = 0; 
            boolean acyclic=true;
            i = nodes.iterator();
            while (i.hasNext()) {
                GraphNode gn = (GraphNode) i.next();
                gn.reset();
            }

            i = nodes.iterator();
            while (i.hasNext()) {
                GraphNode gn = (GraphNode) i.next();
		assert gn.getStatus() != PROCESSING;
                if (gn.getStatus() == UNVISITED) {
                    if (!dfs(gn))
			acyclic=false;
                }
            }
	    return acyclic;
        }

        private boolean dfs(GraphNode gn) {
        	boolean acyclic=true;
            gn.discover(time++);
            Iterator edges = gn.edges();

            while (edges.hasNext()) {
                Edge edge = (Edge) edges.next();
                edge.discover(edgetime++);
                GraphNode node = edge.getTarget();
				if (!nodes.contains(node)) /* Skip nodes which aren't in the set */ {
					if(finishingorder_edge != null)
				    	finishingorder_edge.add(edge);
					edge.finish(edgetime++); 
				    continue;
				}
                if (node.getStatus() == UNVISITED) {
                    if (!dfs(node))
                    	acyclic=false;
                } else if (node.getStatus()==PROCESSING) {
                		acyclic=false;
                }
                if(finishingorder_edge != null)
    		    	finishingorder_edge.add(edge);
    			edge.finish(edgetime++); 
            }
		    if (finishingorder!=null)
			finishingorder.add(gn);  
	        gn.finish(time++);
		    return acyclic;
        }

        public static Vector topology(Collection nodes, Vector finishingorder_edge) {
    	    if (nodes==null) {
    		throw new NullPointerException();
    	    }
    	    DFS dfs=new DFS(nodes);
    	    dfs.finishingorder=new Vector();
    	    if(finishingorder_edge != null) {
    	    	dfs.finishingorder_edge = new Vector();
    	    }
    	    boolean acyclic=dfs.go();
    	    Vector topology = new Vector();
    	    for(int i=dfs.finishingorder.size()-1;i>=0;i--) {
    		GraphNode gn=(GraphNode)dfs.finishingorder.get(i);
    		topology.add(gn);
    	    }
    	    if(finishingorder_edge != null) {
	    	    for(int i=dfs.finishingorder_edge.size()-1;i>=0;i--) {
	        		Edge gn=(Edge)dfs.finishingorder_edge.get(i);
	        		finishingorder_edge.add(gn);
	        	}
    	    }
    	    return topology;
    	}
    } /* end DFS */

}
