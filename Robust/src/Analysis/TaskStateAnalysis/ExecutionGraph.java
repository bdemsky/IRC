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
    private Hashtable graph;
    private Hashtable executiongraph;
    private SymbolTable tasks;
       
    public ExecutionGraph(State state, TaskAnalysis ta){
	this.taskanalysis=ta;
	this.state=state;
	this.tasks = this.state. getTaskSymbolTable();
	this.graph=new Hashtable();
	this.executiongraph = new Hashtable();
    }

    public Hashtable getExecutionGraph(){
	return executiongraph;
    }
    
    public void createExecutionGraph() throws java.io.IOException {
	/*Explore the taskanalysis structure*/
	System.out.println("------- BUILDING THE EXECUTION GRAPH -------");
	Enumeration e=taskanalysis.flagstates.keys();
	
	while (e.hasMoreElements()) {
	    System.out.println("\nBuilding class :");
	    ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
	    System.out.println("\t"+(cdtemp.getSymbol())+ "\n");
	    exploreGraph(cdtemp);
	    test();
	    adapt(cdtemp);
	}
	printDOTFile();
	
    }
    
    private void exploreGraph(ClassDescriptor cd) {
	
	LinkedList fifo = new LinkedList();
	Vector sourceNodeList = new Vector();
	Enumeration e;
	graph.clear();
		
	/* Search for starting nodes */
	Collection nodes = ((Hashtable)taskanalysis.flagstates.get(cd)).values();
	Iterator it = nodes.iterator();
	while (it.hasNext()) {
	    FlagState fs = (FlagState)it.next();
	    if(fs.isSourceNode()){
		sourceNodeList.addElement(fs);
	    }
    	}
	
	/* Perform the Breadth first search algorithm and build ExecutionGraph */
	FlagState fstemp, fstemp2;
	Iterator sourceit = sourceNodeList.iterator();
	while( sourceit.hasNext() ){
	    FlagState fs = (FlagState)sourceit.next();
	    
	    fs.doMarking();
	    fifo.addLast(fs);
	    
	    while ( !fifo.isEmpty() ){
		
		fstemp = (FlagState)fifo.getFirst();
		fifo.removeFirst();
		
		System.out.println("IN FS : "+fstemp.getTextLabel());
		
		Iterator edges = fstemp.edges();
		if (edges.hasNext()){
		    
		    //build corresponding nodes of the ExecutionGraph
		    createNode(fstemp);
		    
		    //add the other non marked (prevent looping) fses to the fifo
		    while(edges.hasNext()){
			
			FEdge edge = (FEdge)edges.next();
			fstemp2 = (FlagState)edge.getTarget();
			
			if ( !fstemp2.isMarked() ) {
			    fstemp2.doMarking();
			    fifo.addLast(fstemp2);
			}
		    }
		    
		    //if the flagstate is not entirely processed, back into fifo
		    if (!isFinished(fstemp)){
			fifo.addLast(fstemp);
		    }
		}
		
	    }
	}
    }
        
    private void createNode(FlagState fs){
	Enumeration allocatingtasks;
	EGTaskNode tn;
	EGTaskNode target;
	FEdge edge;
	//the idea is to look at the inedges to find the "parents" nodes. Then create the "children" and link them to the "parents".
	if (fs.isSourceNode()){
	    //in the case of sourcenode, "parents" are the allocating tasks
	    for (Iterator inedges = ((Vector)fs.getAllocatingTasks()).iterator(); inedges.hasNext();){
		String tname = new String(((TaskDescriptor)inedges.next()).getSymbol()); 
		//the hashkey for source EGTaskNodes is : nextfs+taskname.
		String key1 = new String(fs.getTextLabel()+tname);
		//get the parent
		if (graph.containsKey(key1)){
		    tn = (EGTaskNode)graph.get(key1);
		}
		else{//if not existing, create it
		    tn = new EGTaskNode(tname,(TaskDescriptor)tasks.get(tname));
		    tn.setSource();
		}			
		//create the children. the key is : nextfs+taskname+previousfs (that ensures that only one node can have that key).
		for (Iterator edges = fs.edges(); edges.hasNext();){
		    edge = (FEdge)edges.next();
		    target=new EGTaskNode(edge.getLabel(), fs, (TaskDescriptor)tasks.get(edge.getLabel()));
		    String key2 = new String(((FlagState)edge.getTarget()).getTextLabel()+target.getName()+((FlagState)edge.getSource()).getTextLabel()); 
		    //mark if is self loop
		    if (((FlagState)edge.getTarget()).isMarked()){
			target.doSelfLoopMarking();
		    }
		    //check if child already exists. if not, create it.
		    //link to the parent.
		    if (graph.containsKey(key2)){
			target = (EGTaskNode)graph.get(key2); 
			EGEdge newedge=new EGEdge(target);
			tn.addEdge(newedge);
		    }
		    else {			
			EGEdge newedge=new EGEdge(target);
			tn.addEdge(newedge);
		    }
		    //put child in graph
		    graph.put(key2, target);
		}
		//put parent in graph
		graph.put(key1, tn);
	    }
	}
	
	for (Iterator inedges = fs.inedges(); inedges.hasNext();){
	    //regular case, "parents" are the inedges.
	    FEdge in=(FEdge)inedges.next();
	    if (!in.isProcessed()){
		//the key to search is : nextfs+taskname+previousfs.
		String key1 = new String(fs.getTextLabel()+in.getLabel()+((FlagState)in.getSource()).getTextLabel());
		tn = (EGTaskNode)graph.get(key1);
		//if the TaskNode does not exist, that means that we are in the case of a loop.
		//The fs will not be entirely processed, will be put back in the fifo until the TaskNode has finaly been created.
		if (tn != null){
		    //same process than with the sourcenode.
		    for (Iterator edges = fs.edges(); edges.hasNext();){
			edge = (FEdge)edges.next();
			target=new EGTaskNode(edge.getLabel(), fs, (TaskDescriptor)tasks.get(edge.getLabel()));
			String key2 = new String(((FlagState)edge.getTarget()).getTextLabel()+target.getName()+((FlagState)edge.getSource()).getTextLabel()); 
			if (((String)((FlagState)edge.getTarget()).getTextLabel()).compareTo(fs.getTextLabel())==0){
			    target.doSelfLoopMarking();
			}
			if (graph.containsKey(key2)){
			    target = (EGTaskNode)graph.get(key2); 
			    EGEdge newedge=new EGEdge(target);
			    tn.addEdge(newedge);
			}
			else {
			    EGEdge newedge=new EGEdge(target);
			    tn.addEdge(newedge);
			}
			graph.put(key2, target);
		    }	
		    graph.put(key1, tn);
		    in.setProcessed();
		}
	    }
	}
    }
    
    //put the graph into executiongraph
    private void adapt(ClassDescriptor cd) {
	Vector tasknodes = new Vector();
	tasknodes.addAll(graph.values());
	executiongraph.put(cd,tasknodes);
    }
    //print the contain of graph
    private void test() {
	System.out.println("\nGraph contains :"); 
	Collection c = graph.values();
	for ( Iterator it = c.iterator(); it.hasNext();){
	    EGTaskNode tn = (EGTaskNode)it.next();
	    System.out.println(tn.getTextLabel()+" ID "+tn.getLabel()+" FS "+tn.getFSName());
	}
    }
    
    //test if a flagstate has been entirely processed
    private boolean isFinished(FlagState fs){
		
	for (Iterator inedges = fs.inedges(); inedges.hasNext();){
	    
	    FEdge in=(FEdge)inedges.next();
	    
	    if (!in.isProcessed()){
		String key1 = new String(fs.getTextLabel()+in.getLabel()+((FlagState)in.getSource()).getTextLabel());
		
		if (graph.get(key1)==null){
		    //except for the case of self loop, if the pointed tn is not present, fs is not totally processed
		    if (((String)((FlagState)in.getSource()).getTextLabel()).compareTo(fs.getTextLabel())!=0){
			return false;
		    }
		}
		
	    }
	}
	return true;
    }

    
    //********DEBUG
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
    //*********************
    
}













