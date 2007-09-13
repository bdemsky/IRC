package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.State;
import IR.SymbolTable;
import IR.ClassDescriptor;
import IR.TaskDescriptor;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import Util.Edge;

public class ExecutionGraph {
    private TaskAnalysis taskanalysis;
    private State state;
    private Hashtable executiongraph;
    private HashSet marked;
    private HashSet processed;

    public ExecutionGraph(State state, TaskAnalysis ta){
	this.taskanalysis=ta;
	this.state=state;
	this.executiongraph = new Hashtable();
	this.marked=new HashSet();
	this.processed=new HashSet();
    }

    public Hashtable getExecutionGraph(){
	return executiongraph;
    }
    
    public void createExecutionGraph() throws java.io.IOException {
	//Cycle through classes
	Enumeration e=taskanalysis.flagstates.keys();
	
	while (e.hasMoreElements()) {
	    ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
	    HashSet<EGTaskNode> graph=exploreGraph(cdtemp);
	    adapt(cdtemp,graph);
	}
	printDOTFile();
    }
    
    private HashSet<EGTaskNode> exploreGraph(ClassDescriptor cd) {
	LinkedList<FlagState> fifo = new LinkedList<FlagState>();
	HashSet<EGTaskNode> nodes=new HashSet<EGTaskNode>();
	Hashtable<FEdge, EGTaskNode> map=new Hashtable<FEdge, EGTaskNode>();

	// Go through nodes
	Iterator<FlagState> it = taskanalysis.getFlagStates(cd).iterator();
	while (it.hasNext()) {
	    FlagState fs = it.next();
	    if(fs.isSourceNode()) {
		for (Iterator allocit = ((Vector)fs.getAllocatingTasks()).iterator(); allocit.hasNext();) {
		    TaskDescriptor alloctask=(TaskDescriptor)allocit.next();
		    EGTaskNode srcnode=new EGTaskNode(alloctask.getSymbol(),alloctask);
		    nodes.add(srcnode);
		    srcnode.setSource();
		    for (Iterator edges = fs.edges(); edges.hasNext();){
			FEdge edge = (FEdge)edges.next();
			EGTaskNode targetnode=getNode(edge, map, nodes);
			EGEdge newedge=new EGEdge(targetnode);
			srcnode.addEdge(newedge);
		    }
		}
	    }
	    for(Iterator init=fs.inedges();init.hasNext();) {
		FEdge inedge=(FEdge)init.next();
		EGTaskNode srcnode=getNode(inedge, map, nodes);
		for(Iterator outit=fs.edges();outit.hasNext();) {
		    FEdge outedge=(FEdge)outit.next();
		    EGTaskNode dstnode=getNode(outedge, map, nodes);
		    EGEdge newedge=new EGEdge(dstnode);
		    srcnode.addEdge(newedge);
		}
	    }

    	}
	return nodes;
    }	
    
    private EGTaskNode getNode(FEdge fedge, Hashtable<FEdge, EGTaskNode> map, HashSet<EGTaskNode> nodes) {
	if (map.containsKey(fedge))
	    return map.get(fedge);
	EGTaskNode egnode=new EGTaskNode(fedge.getLabel(), (FlagState) fedge.getSource(), fedge.getTask());
	if (fedge.getTarget()==fedge.getSource())
	    egnode.doSelfLoopMarking();
	map.put(fedge, egnode);
	nodes.add(egnode);
	return egnode;
    }

    //put the graph into executiongraph
    private void adapt(ClassDescriptor cd, HashSet<EGTaskNode> nodes) {
	Vector tasknodes = new Vector();
	tasknodes.addAll(nodes);
	executiongraph.put(cd,tasknodes);
    }

    //print the contain of graph
    private void test(Hashtable graph) {
	System.out.println("\nGraph contains :"); 
	Collection c = graph.values();
	for ( Iterator it = c.iterator(); it.hasNext();){
	    EGTaskNode tn = (EGTaskNode)it.next();
	    System.out.println(tn.getTextLabel()+" ID "+tn.getLabel()+" FS "+tn.getFSName());
	}
    }
    
    //create dot files execution_classname_.dot
    private void printDOTFile()throws java.io.IOException {
	Enumeration e = executiongraph.keys();
	while (e.hasMoreElements()){
	    createDOTFile((ClassDescriptor)e.nextElement());
	}
    }	
    
    private void createDOTFile(ClassDescriptor cd) throws java.io.IOException {
	Vector v = (Vector)executiongraph.get(cd);
	java.io.PrintWriter output;
	File dotfile_flagstates= new File("execution"+cd.getSymbol()+".dot");
	FileOutputStream dotstream=new FileOutputStream(dotfile_flagstates,true);
	output = new java.io.PrintWriter(dotstream, true);
	output.println("digraph dotvisitor {");
	output.println("\tnode [fontsize=10,height=\"0.1\", width=\"0.1\"];");
	output.println("\tedge [fontsize=6];");
	traverse(output, v);
	output.println("}\n");
    }
    
    private void traverse(java.io.PrintWriter output, Vector v) {
	EGTaskNode tn;
	
	for(Iterator it1 = v.iterator(); it1.hasNext();){
	    tn = (EGTaskNode)it1.next();
	    output.println("\t"+tn.getLabel()+" [label=\""+tn.getTextLabel()+"\"");
	    if (tn.isSelfLoop()) output.println(", shape=box");
	    if (tn.isMultipleParams()) output.println(", color=blue");
	    output.println("];");
	    
	    for(Iterator it2 = tn.edges();it2.hasNext();){
		output.println("\t"+tn.getLabel()+" -> "+((EGTaskNode)((EGEdge)it2.next()).getTarget()).getLabel()+";");
	    }
	}
    }
}
