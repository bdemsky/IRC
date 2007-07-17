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
    private TreeMap graphs;
    private Hashtable executiongraph;
    private SymbolTable tasks;
       
    public ExecutionGraph(State state, TaskAnalysis ta){
	this.taskanalysis=ta;
	this.state=state;
	this.tasks = this.state. getTaskSymbolTable();
	this.graphs=new TreeMap();
	this.executiongraph = new Hashtable();
    }

    public Hashtable getExecutionGraph(){
	return executiongraph;
    }
    
    public void createExecutionGraph() throws java.io.IOException {
	/** Explore the analysis structure "OPTIONAL ARGS" PROJECT**/
	
	Enumeration e=taskanalysis.flagstates.keys();
	
	while (e.hasMoreElements()) {
	    System.out.println("\nInto class :");
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
	graphs.clear();
	int l=0;
	
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
	    createLevel(l);
	    FlagState fs = (FlagState)sourceit.next();
	    
	    fs.doMarking();
	    fifo.addLast(fs);
	    
	    
	    int i=0;
	    while ( !fifo.isEmpty() ){
		
		fstemp = (FlagState)fifo.getFirst();
		fifo.removeFirst();
		
		System.out.println("IN FS : "+fstemp.getTextLabel());
		
		Iterator edges = fstemp.edges();
		if (edges.hasNext()){
		    
		    createNode(fstemp, l);
		    
		    while(edges.hasNext()){
			
			FEdge edge = (FEdge)edges.next();
			fstemp2 = (FlagState)edge.getTarget();
			
			if ( !fstemp2.isMarked() ) {
			    fstemp2.doMarking();
			    fifo.addLast(fstemp2);
			}
		    }
		    
		    
		    if (!isFinished(fstemp, l)){
			fifo.addLast(fstemp);
		    }
		}
		
	    }
		    
	    Hashtable temphash = new Hashtable();
	    temphash = clean((Hashtable)graphs.get(l));
	    graphs.put(l, temphash);
	    l++;
	}
    }
        
    private void createLevel(int level){
	if (!graphs.containsKey(level)){
	    Hashtable ht = new Hashtable();
	    graphs.put(level, ht);
	}	
    }
    
    private void createNode(FlagState fs, int level){
	Enumeration allocatingtasks;
	EGTaskNode tn;
	EGTaskNode target;
	FEdge edge;
	
	if (fs.isSourceNode()){

	    for (Iterator inedges = ((Vector)fs.getAllocatingTasks()).iterator(); inedges.hasNext();){
		String tname = new String(((TaskDescriptor)inedges.next()).getSymbol()); 
		String key1 = new String(fs.getTextLabel()+tname);
		if (((Hashtable)graphs.get(level)).containsKey(key1)){
		    tn = (EGTaskNode)((Hashtable)graphs.get(level)).get(key1);
		}
		else{
		    tn = new EGTaskNode(tname,(TaskDescriptor)tasks.get(tname));
		    tn.setSource();
		}			
		for (Iterator edges = fs.edges(); edges.hasNext();){
		    edge = (FEdge)edges.next();
		    // if(!edge.isProcessed()){
			target=new EGTaskNode(edge.getLabel(), fs, (TaskDescriptor)tasks.get(edge.getLabel()));
			String key2 = new String(((FlagState)edge.getTarget()).getTextLabel()+target.getName()+((FlagState)edge.getSource()).getTextLabel()); 
			if (((FlagState)edge.getTarget()).isMarked()){
			    target.doSelfLoopMarking();
			}
			if (((Hashtable)graphs.get(level)).containsKey(key2)){
			    target = (EGTaskNode)((Hashtable)graphs.get(level)).get(key2); 
			    TEdge newedge=new TEdge(target);
			    tn.addEdge(newedge);
			}
			else {			
			    TEdge newedge=new TEdge(target);
			    tn.addEdge(newedge);
			}
			((Hashtable)graphs.get(level)).put(key2, target);
			// }
		}
		((Hashtable)graphs.get(level)).put(key1, tn);
	    }
	}
	
	for (Iterator inedges = fs.inedges(); inedges.hasNext();){
	    
	    FEdge in=(FEdge)inedges.next();
	    String key1 = new String(fs.getTextLabel()+in.getLabel()+((FlagState)in.getSource()).getTextLabel());
	    if (!in.isProcessed()){
		tn = (EGTaskNode)((Hashtable)graphs.get(level)).get(key1);
		if (tn != null){
		    for (Iterator edges = fs.edges(); edges.hasNext();){
			edge = (FEdge)edges.next();
			target=new EGTaskNode(edge.getLabel(), fs, (TaskDescriptor)tasks.get(edge.getLabel()));
			String key2 = new String(((FlagState)edge.getTarget()).getTextLabel()+target.getName()+((FlagState)edge.getSource()).getTextLabel()); 
			if (((String)((FlagState)edge.getTarget()).getTextLabel()).compareTo(fs.getTextLabel())==0){
			    target.doSelfLoopMarking();
			}
			if (((Hashtable)graphs.get(level)).containsKey(key2)){
			    target = (EGTaskNode)((Hashtable)graphs.get(level)).get(key2); 
			    TEdge newedge=new TEdge(target);
			    tn.addEdge(newedge);
			}
			else {
			    TEdge newedge=new TEdge(target);
			    tn.addEdge(newedge);
			}
			((Hashtable)graphs.get(level)).put(key2, target);
		    }	
		    ((Hashtable)graphs.get(level)).put(key1, tn);
		    in.setProcessed();
		}
	    }
	}
	
    }
    
    private void adapt(ClassDescriptor cd) {
	Vector tasknodes = new Vector();
	
	Collection c1 = graphs.values();
	for (Iterator it1 = c1.iterator(); it1.hasNext();){
	    Collection tempc=((Hashtable)it1.next()).values();
	    for(Iterator it2 = tempc.iterator(); it2.hasNext();){
		EGTaskNode tn = (EGTaskNode)it2.next();
		if(tn.getName().compareTo("Runtime")!=0){
		    TaskDescriptor td = tn.getTD();
		    System.out.println("Trying to get : " + tn.getName());
		    if(td.numParameters()>1) tn.setMultipleParams();
		}
	    }
	    tasknodes.addAll(tempc);
	}
	executiongraph.put(cd,tasknodes);
    }
    
    private void test() {
	int i = 0;
	Collection c1 = graphs.values();
	for (Iterator it1 = c1.iterator(); it1.hasNext();){
	    Hashtable ht = ((Hashtable)it1.next());
	    System.out.println("\nLevel " + i++ + " contains :"); 
	    Collection c2 = ht.values();
	    for ( Iterator it2 = c2.iterator(); it2.hasNext();){
		EGTaskNode tn = (EGTaskNode)it2.next();
		System.out.println(tn.getTextLabel()+" ID "+tn.getLabel()+" FS "+tn.getFSName());
	    }
	}
    }
       
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
		output.println("\t"+tn.getLabel()+" -> "+((EGTaskNode)((TEdge)it2.next()).getTarget()).getLabel()+";");
	    }
	}
    }
    
    private Hashtable clean(Hashtable ht){
	Hashtable cleaned = new Hashtable();
	Collection c = ht.values();
	for ( Iterator it = c.iterator(); it.hasNext();){
	    EGTaskNode tn = (EGTaskNode)it.next();
	    Vector v = tn.getEdgeVector();
	    v = removeDouble(v);
	    tn.removeAllEdges();
	    tn.addEdge(v);
	    cleaned.put(tn.getuid(), tn);
	}
	return cleaned;
    }
    
    private Vector removeDouble(Vector v){
	
	Vector vcleaned = new Vector();
	for (Iterator it = v.iterator(); it.hasNext();){
	    
	    TEdge edge = (TEdge)it.next();
	    int contains = 0;
	    for (Iterator it2 = vcleaned.iterator(); it2.hasNext();){
		if (((EGTaskNode)edge.getTarget()).getuid()==((EGTaskNode)((TEdge)it2.next()).getTarget()).getuid()) contains = 1;
	    }
	    
	    if (contains == 0) vcleaned.add(edge); 
	}
	
	return vcleaned;
    }
    
    private boolean isFinished(FlagState fs, int level){
		
	boolean result = true;
	for (Iterator inedges = fs.inedges(); inedges.hasNext();){
	    
	    FEdge in=(FEdge)inedges.next();
	    
	    if (!in.isProcessed()){
		String key1 = new String(fs.getTextLabel()+in.getLabel()+((FlagState)in.getSource()).getTextLabel());
		
		if (((Hashtable)graphs.get(level)).get(key1)==null){
		    if (((String)((FlagState)in.getSource()).getTextLabel()).compareTo(fs.getTextLabel())!=0){
			result = false;
		    }
		}
		
	    }
	}
	return result;
    }
    
    
}













