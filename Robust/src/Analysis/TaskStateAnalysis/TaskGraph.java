package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.State;
import IR.SymbolTable;
import IR.ClassDescriptor;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;

public class TaskGraph {
    TaskAnalysis taskanalysis;
    State state;
    Hashtable cdtonodes;

    public TaskGraph(State state, TaskAnalysis taskanalysis) {
	this.state=state;
	this.taskanalysis=taskanalysis;
	this.cdtonodes=new Hashtable();

	for(Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();classit.hasNext();) {
	    ClassDescriptor cd=(ClassDescriptor) classit.next();
	    if (cd.hasFlags())
	    	produceTaskNodes(cd);
	}
    }
    
    
    public void createDOTfiles() {
	for(Iterator it_classes=(Iterator)cdtonodes.keys();it_classes.hasNext();) {
	    ClassDescriptor cd=(ClassDescriptor) it_classes.next();
	    Set tasknodes=getTaskNodes(cd);
	    if (tasknodes!=null) {
		try {
		    File dotfile_tasknodes=new File("graph"+cd.getSymbol()+"_task.dot");
		    FileOutputStream dotstream=new FileOutputStream(dotfile_tasknodes,true);
		    TaskNode.DOTVisitor.visit(dotstream,tasknodes);
		} catch(Exception e) {
		    e.printStackTrace();
		    throw new Error();
		}
	    }
	}
    }

    /** Returns the set of TaskNodes for the class descriptor cd */

    public Set getTaskNodes(ClassDescriptor cd) {
	if (cdtonodes.containsKey(cd))
	    return ((Hashtable)cdtonodes.get(cd)).keySet();
	else
	    return null;
    }

    private TaskNode canonicalizeTaskNode(Hashtable nodes, TaskNode node){
	if (nodes.containsKey(node))
	    return (TaskNode)nodes.get(node);
	else{
	    nodes.put(node,node);
	    return (TaskNode)node;
	}
    }
    
    private void produceTaskNodes(ClassDescriptor cd) {
	Set fsnodes=taskanalysis.getFlagStates(cd);
	if (fsnodes==null)
	    return;
	    
	Hashtable<TaskNode,TaskNode> tasknodes=new Hashtable<TaskNode,TaskNode>();
	cdtonodes.put(cd, tasknodes);

	for(Iterator it=fsnodes.iterator();it.hasNext();) {
	    FlagState fs=(FlagState)it.next();
	    Iterator it_inedges=fs.inedges();	
	    TaskNode tn,sn;
	   
		if (fs.isSourceNode()) {
			sn=new TaskNode("Start Node");
			if(fs.edges().hasNext()){
				 addEdges(fs,sn);
			}	
		}
						
		while(it_inedges.hasNext()){   
			
		    FEdge inedge=(FEdge)it_inedges.next();
		    tn=new TaskNode(inedge.getLabel());
		    if(fs.edges().hasNext()){
		    	addEdges(fs,tn);
			}
	    }  
	}
    }
    
    private void addEdges(FlagState fs, TaskNode tn){
	    
	    Hashtable<TaskNode,TaskNode> tasknodes=(Hashtable<TaskNode,TaskNode>)cdtonodes.get(fs.getClassDescriptor());
	    tn=(TaskNode)canonicalizeTaskNode(tasknodes, tn);
		for (Iterator it_edges=fs.edges();it_edges.hasNext();){
			TaskNode target=new TaskNode(((FEdge)it_edges.next()).getLabel());
			target=(TaskNode)canonicalizeTaskNode(tasknodes,target);

			TEdge newedge=new TEdge(target);
			if (! tn.edgeExists(newedge))
				tn.addEdge(new TEdge(target));
	    }

	}
}
