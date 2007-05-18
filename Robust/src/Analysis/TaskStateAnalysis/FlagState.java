package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

/** This class is used to hold the flag states that a class in the Bristlecone 
 *  program can exist in, during runtime.
 */
public class FlagState {
    /* NodeStatus enumeration pattern ***********/

    public static final NodeStatus UNVISITED = new NodeStatus("UNVISITED");
    public static final NodeStatus PROCESSING = new NodeStatus("PROCESSING");
    public static final NodeStatus FINISHED = new NodeStatus("FINISHED");
    public static final int ONETAG=1;
    public static final int NOTAGS=0;
    public static final int MULTITAGS=-1;
    
    

    
    public static class NodeStatus {
        private static String name;
        private NodeStatus(String name) { this.name = name; }
        public String toString() { return name; }
    }


    int discoverytime = -1;
    int finishingtime = -1; /* used for searches */

    //Hashtable<String,Integer> tags=new Hashtable<String,Integer>();
    Vector edges = new Vector();
    Vector inedges = new Vector();
    NodeStatus status = UNVISITED;
    protected String dotnodeparams = new String();
    boolean merge=false;
    String nodeoption="";
    private int uid;
    private static int nodeid=0;

    private final HashSet flagstate;
    private final ClassDescriptor cd;
    private final Hashtable<String,Integer> tags;

    public void setOption(String option) {
	this.nodeoption=","+option;
    }

    public void setMerge() {
	merge=true;
    }
    /** Class constructor
     *  Creates a new flagstate with all flags set to false.
     *	@param cd ClassDescriptor
     */
    public FlagState(ClassDescriptor cd) {
	this.flagstate=new HashSet();
	this.cd=cd;
	this.tags=new Hashtable<String,Integer>();
	this.uid=FlagState.nodeid++;
    }

    /** Class constructor
     *  Creates a new flagstate with flags set according to the HashSet.
     *  If the flag exists in the hashset, it's set to true else set to false.
     *	@param cd ClassDescriptor
     *  @param flagstate a <CODE>HashSet</CODE> containing FlagDescriptors
     */
    private FlagState(HashSet flagstate, ClassDescriptor cd,Hashtable<String,Integer> tags) {
	this.flagstate=flagstate;
	this.cd=cd;
	this.tags=tags;
	this.uid=FlagState.nodeid++;
	
    }
    
    /** Accessor method
      *  @param fd FlagDescriptor
      *  @return true if the flagstate contains fd else false.
      */
    public boolean get(FlagDescriptor fd) {
	return flagstate.contains(fd);
    }

    
    public String toString() {
	return cd.toString()+getTextLabel();
    }

    /** @return Iterator over the flags in the flagstate.
     */
     
    public Iterator getFlags() {
	return flagstate.iterator();
    }
    
    public FlagState setTag(TagDescriptor tag){
	   
	    HashSet newset=flagstate.clone();
	    Hashtable<String,Integer> newtags=tags.clone();
	    
	    if (newtags.containsKey(tag)){
		   switch (newtags.get(tag).intValue()){
			   case ONETAG:
			   		newtags.put(tag,new Integer(MULTITAG));
			   		break;
			   case MULTITAG:
			   		newtags.put(tag,new Integer(MULTITAG));
			   		break;
			}
		}
		else{
			newtags.put(tag,new Integer(ONETAG));
		}
		
		return new FlagState(newset,cd,newtags);
			   	
    }
    public int getTagCount(String tagtype){
	    	for (Enumeration en=getTags();en.hasMoreElements();){
		    	TagDescriptor td=(TagDescriptor)en.nextElement();
		    	if (tagtype.equals(td.getSymbol()))
		    		return tags.get(td).intValue();   //returns either ONETAG or MULTITAG
	    	}
	    	return NOTAG;
	    	
    }
    
   
    
    public FlagState[] clearTag(TagDescriptor tag){
	    
	    if (tags.containsKey(tag)){
	    switch(tags.get(tag).intValue()){
		    case ONETAG:
		    	HashSet newset=flagstate.clone();
	    		Hashtable<String,Integer> newtags=tags.clone();
		    	newtags.remove(tag);
		    	return new FlagState(newset,cd,newtags);
		    	break;
		    case MULTITAG:
		        //when tagcount is more than 2, COUNT stays at MULTITAG
		    	FlagState[] retstates=new FlagState[2];
		    	HashSet newset1=flagstate.clone();
	    		Hashtable<String,Integer> newtags1=tags.clone();
	    		retstates[1]=new FlagState(newset1,cd,newtags1);
	    		//when tagcount is 2, COUNT changes to ONETAG
	    		HashSet newset2=flagstate.clone();
	    		Hashtable<String,Integer> newtags2=tags.clone();
	    		newtags1.put(tag,new Integer(ONETAG));
	    		retstates[1]=new FlagState(newset2,cd,newtags2);
	    		return retstates;
	    		break;
    	}
		}else{
			throw new Error("Invalid Operation: Can not clear a tag that doesn't exist.");
		}
		
    }
    
    /** Creates a string description of the flagstate
     *  e.g.  a flagstate with five flags could look like 01001
     *  @param flags an array of flagdescriptors.
     *  @return string representation of the flagstate.
     */
	public String toString(FlagDescriptor[] flags)
	{
		StringBuffer sb = new StringBuffer(flagstate.size());
		for(int i=0;i < flags.length; i++)
		{
			if (get(flags[i]))
				sb.append(1);
			else
				sb.append(0);
		}
			
		return new String(sb);
	}

	/** Accessor method
	 *  @return returns the classdescriptor of the flagstate.
	 */
	 
	public ClassDescriptor getClassDescriptor(){
	return cd;
	}

	/** Sets the status of a specific flag in a flagstate after cloning it.
	 *  @param	fd FlagDescriptor of the flag whose status is being set.
	 *  @param  status boolean value
	 *  @return the new flagstate with <CODE>fd</CODE> set to <CODE>status</CODE>.
	 */
	 
    public FlagState setFlag(FlagDescriptor fd, boolean status) {
	HashSet newset=(HashSet) flagstate.clone();
	if (status)
	    newset.add(fd);
	else if (newset.contains(fd)){
	    newset.remove(fd);
	}
	return new FlagState(newset, cd);
    }
    
    /** Tests for equality of two flagstate objects.
    */
    
    public boolean equals(Object o) {
        if (o instanceof FlagState) {
            FlagState fs=(FlagState)o;
            if (fs.cd!=cd)
                return false;
	    return (fs.flagstate.equals(flagstate) & fs.tags.equals(tags));
        }
        return false;
    }

    public int hashCode() {
        return cd.hashCode()^flagstate.hashCode()^tags.hashCode();
    }

    public static void computeclosure(Collection nodes, Collection removed) {
	Stack tovisit=new Stack();
	tovisit.addAll(nodes);
	while(!tovisit.isEmpty()) {
	    FlagState gn=(FlagState)tovisit.pop();
	    for(Iterator it=gn.edges();it.hasNext();) {
		Edge edge=(Edge)it.next();
		FlagState target=edge.getTarget();
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
		FlagState gn=(FlagState)tovisit.pop();
		for(Iterator it=gn.edges();it.hasNext();) {
		    Edge edge=(Edge)it.next();
		    FlagState target=edge.getTarget();
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
        if (param.length() > 0) {
            dotnodeparams = "," + param;
        } else {
            dotnodeparams = new String();
        }
    }

    public void setStatus(NodeStatus status) {
        if (status == null) {
            throw new NullPointerException();
        }
        this.status = status;
    }

    public String getLabel() {
	return "N"+uid;
    }

    public String getTextLabel() {
	String label=null;
	for(Iterator it=getFlags();it.hasNext();) {
	    FlagDescriptor fd=(FlagDescriptor) it.next();
	    if (label==null)
		label=fd.toString();
	    else
		label+=", "+fd.toString();
	}
	for (Enumeration en_tags=getTags();en_tags.hasMoreElements();){
		TagDescriptor td=(TagDescriptor)en_tags.nextElement();
		switch (tags.get(td).intValue()){
			case ONETAG:
				label+=", "+td.toString()+"(1)";
				break;
			case MULTITAG:
				label+=", "+td.toString()+"(n)";
				break;
			default:
				break;
		}
	}
	return label;
    }
    
    public Enumeration getTags(){
	    return tags.keys();
   	}

    public NodeStatus getStatus() {
        return this.status;
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
	FlagState tonode=newedge.getTarget();
	tonode.inedges.addElement(newedge);
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

        private DOTVisitor(java.io.OutputStream output) {
            tokennumber = 0;
            color = 0;
            this.output = new java.io.PrintWriter(output, true);
        }

        private String getNewID(String name) {
            tokennumber = tokennumber + 1;
            return new String (name+tokennumber);
        }

        Collection nodes;
	Collection special;

        public static void visit(java.io.OutputStream output, Collection nodes) {
	    visit(output,nodes,null);
	}

        public static void visit(java.io.OutputStream output, Collection nodes, Collection special) {
            DOTVisitor visitor = new DOTVisitor(output);
	    visitor.special=special;
            visitor.nodes = nodes;
            visitor.make();
        }

        private void make() {
            output.println("digraph dotvisitor {");
            output.println("\trotate=90;");
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
            Iterator i = nodes.iterator();
            while (i.hasNext()) {
                FlagState gn = (FlagState) i.next();
                Iterator edges = gn.edges();
                String label = gn.getTextLabel(); // + " [" + gn.discoverytime + "," + gn.finishingtime + "];";
				String option=gn.nodeoption;
				if (special!=null&&special.contains(gn))
		    		option+=",shape=box";
				if (!gn.merge)
                    output.println("\t" + gn.getLabel() + " [label=\"" + label + "\"" + gn.dotnodeparams + option+"];");
				if (!gn.merge)
        	        while (edges.hasNext()) {
            	        Edge edge = (Edge) edges.next();
                	    FlagState node = edge.getTarget();
		    			if (nodes.contains(node)) {
							for(Iterator nodeit=nonmerge(node).iterator();nodeit.hasNext();) {
			    				FlagState node2=(FlagState)nodeit.next();
			    				String edgelabel = "label=\"" + edge.getLabel() + "\"";
			   					output.println("\t" + gn.getLabel() + " -> " + node2.getLabel() + " [" + edgelabel + edge.dotnodeparams + "];");
							}
		    			}
                	}
            }
        }
        

	Set nonmerge(FlagState gn) {
	    HashSet newset=new HashSet();
	    HashSet toprocess=new HashSet();
	    toprocess.add(gn);
	    while(!toprocess.isEmpty()) {
		FlagState gn2=(FlagState)toprocess.iterator().next();
		toprocess.remove(gn2);
		if (!gn2.merge)
		    newset.add(gn2);
		else {
		    Iterator edges = gn2.edges();
		    while (edges.hasNext()) {
			Edge edge = (Edge) edges.next();
			FlagState node = edge.getTarget();
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

	/** Returns the strongly connected component number for the FlagState gn*/
	public int getComponent(FlagState gn) {
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
	    FlagState gn=(FlagState)array[0];
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
                FlagState gn = (FlagState) it.next();
                gn.resetscc();
            }
	    for(int i=dfs.finishingorder.size()-1;i>=0;i--) {
		FlagState gn=(FlagState)dfs.finishingorder.get(i);
		if (gn.getStatus() == UNVISITED) {
		    dfs.dfsprev(gn);
		    dfs.sccindex++; /* Increment scc index */
		}
	    }
	    return new SCC(acyclic,dfs.sccmap,dfs.sccmaprev,dfs.sccindex);
	}

	void dfsprev(FlagState gn) {
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
		FlagState gn2=e.getSource();
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
            boolean acyclic=true;
            i = nodes.iterator();
            while (i.hasNext()) {
                FlagState gn = (FlagState) i.next();
                gn.reset();
            }

            i = nodes.iterator();
            while (i.hasNext()) {
                FlagState gn = (FlagState) i.next();
		assert gn.getStatus() != PROCESSING;
                if (gn.getStatus() == UNVISITED) {
                    if (!dfs(gn))
			acyclic=false;
                }
            }
	    return acyclic;
        }

        private boolean dfs(FlagState gn) {
	    boolean acyclic=true;
            gn.discover(time++);
            Iterator edges = gn.edges();

            while (edges.hasNext()) {
                Edge edge = (Edge) edges.next();
                FlagState node = edge.getTarget();
		if (!nodes.contains(node)) /* Skip nodes which aren't in the set */
		    continue;
                if (node.getStatus() == UNVISITED) {
                    if (!dfs(node))
			acyclic=false;
                } else if (node.getStatus()==PROCESSING) {
		    acyclic=false;
		}
            }
	    if (finishingorder!=null)
		finishingorder.add(gn);
            gn.finish(time++);
	    return acyclic;
        }
    } /* end DFS */
}
