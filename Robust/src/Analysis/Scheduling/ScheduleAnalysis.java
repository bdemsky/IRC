package Analysis.Scheduling;

import Analysis.TaskStateAnalysis.*;
import IR.*;
import java.util.*;
import java.io.*;

import Util.Edge;
import Util.GraphNode;
import Util.Namer;

/** This class holds flag transition diagram(s) can be put on one core.
 */
public class ScheduleAnalysis {
    
    TaskAnalysis taskanalysis;
    State state;
    Vector<ScheduleNode> scheduleNodes;
    Vector<ClassNode> classNodes;
    boolean sorted = false;
    Vector<ScheduleEdge> scheduleEdges;
    Hashtable<TaskDescriptor, Hashtable<ClassDescriptor, Vector<ScheduleEdge>>> taskToSEdges;
    
    int probabilityThreshold;

    public ScheduleAnalysis(State state, TaskAnalysis taskanalysis) {
	this.state = state;
	this.taskanalysis = taskanalysis;
	this.scheduleNodes = new Vector<ScheduleNode>();
	this.classNodes = new Vector<ClassNode>();
	this.scheduleEdges = new Vector<ScheduleEdge>();
	this.taskToSEdges = new Hashtable<TaskDescriptor, Hashtable<ClassDescriptor, Vector<ScheduleEdge>>>();
	this.probabilityThreshold = 45;
    } 
    
    public void setProbabilityThreshold(int pt) {
    	this.probabilityThreshold = pt;
    }
    
    public Vector<ScheduleEdge> getSEdges(TaskDescriptor td, ClassDescriptor cd) {
    	return taskToSEdges.get(td).get(cd);
    }
    
    public Vector<ScheduleEdge> getSEdges4Test() {
    	return scheduleEdges;
    }
    
    public void preSchedule() {
    	Hashtable<ClassDescriptor, ClassNode> cdToCNodes = new Hashtable<ClassDescriptor, ClassNode>();
    	// Build the combined flag transition diagram
    	// First, for each class create a ClassNode
    	for(Iterator it_classes = state.getClassSymbolTable().getDescriptorsIterator(); it_classes.hasNext(); ) {
    	    ClassDescriptor cd = (ClassDescriptor) it_classes.next();
    	    Set<FlagState> fStates = taskanalysis.getFlagStates(cd);
    	    
    	    //Sort flagState nodes inside this ClassNode
    	    Vector<FlagState> sFStates = FlagState.DFS.topology(fStates, null);
    	    
    	    Vector rootnodes  = taskanalysis.getRootNodes(cd);
    	    if(((rootnodes != null) && (rootnodes.size() > 0)) || (cd.getSymbol().equals("StartupObject"))) {
    	    	ClassNode cNode = new ClassNode(cd, sFStates);
    	    	cNode.setSorted(true);
    	    	classNodes.add(cNode);
    	    	cdToCNodes.put(cd, cNode);
	   }    
    	}

    	// For each ClassNode create a ScheduleNode containing it
    	int i = 0;
    	for(i = 0; i < classNodes.size(); i++) {
	    ScheduleNode sn = new ScheduleNode(classNodes.elementAt(i));
	    classNodes.elementAt(i).setScheduleNode(sn);
	    scheduleNodes.add(sn);
    	}
    	
    	// Create 'new' edges between the ScheduleNodes.
    	for(i = 0; i < classNodes.size(); i++) {
	    ClassNode cNode = classNodes.elementAt(i);
	    ClassDescriptor cd = cNode.getClassDescriptor();
	    Vector rootnodes  = taskanalysis.getRootNodes(cd);   	    
	    if(rootnodes != null) {
    	    	for(Iterator it_rootnodes=rootnodes.iterator();it_rootnodes.hasNext();){
		    FlagState root=(FlagState)it_rootnodes.next();
		    Vector allocatingTasks = root.getAllocatingTasks();
		    if(allocatingTasks != null) {
    	    		for(Iterator it_atnodes=allocatingTasks.iterator();it_atnodes.hasNext();){
			    TaskDescriptor td = (TaskDescriptor)it_atnodes.next();
			    Vector<FEdge> fev = (Vector<FEdge>)taskanalysis.getFEdgesFromTD(td);
			    int numEdges = fev.size();
			    ScheduleNode sNode = cNode.getScheduleNode();
			    for(int j = 0; j < numEdges; j++) {
    	    			FEdge pfe = fev.elementAt(j);
    	    			FlagState pfs = (FlagState)pfe.getTarget();
    	    			ClassDescriptor pcd = pfs.getClassDescriptor();
    	    			ClassNode pcNode = cdToCNodes.get(pcd);
    	    					
        		    	ScheduleEdge sEdge = new ScheduleEdge(sNode, "new", td, cd);
        		    	sEdge.setFEdge(pfe);
        		    	sEdge.setSourceCNode(pcNode);
        		    	sEdge.setTargetCNode(cNode); 
        		   	sEdge.setTargetFState(root);
        		    	pcNode.getScheduleNode().addEdge(sEdge);
        		    	scheduleEdges.add(sEdge);
        		    	if(taskToSEdges.get(td) == null) {
        		    		taskToSEdges.put(td, new Hashtable<ClassDescriptor, Vector<ScheduleEdge>>());
        		    	}
        		    	if(taskToSEdges.get(td).get(cd) == null)  {
        		    		taskToSEdges.get(td).put(cd, new Vector<ScheduleEdge>());
        		    	}
        		    	taskToSEdges.get(td).get(cd).add(sEdge);
			    }
			    fev = null;
    	    		}
    	    		allocatingTasks = null;
		    }
    	    	}
    	    	rootnodes = null;
	    }
    	}
    	
    	// Do topology sort of the ClassNodes and ScheduleEdges.
    	Vector<ScheduleEdge> ssev = new Vector<ScheduleEdge>();
    	Vector<ScheduleNode> tempSNodes = ClassNode.DFS.topology(scheduleNodes, ssev);
    	scheduleNodes.removeAllElements();
    	scheduleNodes = tempSNodes;
    	tempSNodes = null;
    	scheduleEdges.removeAllElements();
    	scheduleEdges = ssev;
    	ssev = null;
    	sorted = true;
    }
    
    public void scheduleAnalysis() {
    	// First iteration
    	int i = 0; 	
    	Vector<ScheduleEdge> rsev = new Vector<ScheduleEdge>();
    	for(i = scheduleEdges.size(); i > 0; i--) {
	    ScheduleEdge se = scheduleEdges.elementAt(i-1);
	    if(!(se.getProbability() > this.probabilityThreshold)){
    		// Merge the target ScheduleNode into the source ScheduleNode
    		((ScheduleNode)se.getSource()).merge(se);
    		scheduleNodes.remove(se.getTarget());
    		se.setTarget((ScheduleNode)se.getSource());
    		rsev.add(se);
	    }
    	}
    	scheduleEdges.removeAll(rsev);
    	rsev = null;
    	
    	//Second iteration
    	//Access the ScheduleEdges in reverse topology order
    	for(i = scheduleEdges.size(); i > 0; i--) {
	    ScheduleEdge se = scheduleEdges.elementAt(i-1);
	    FEdge fe = se.getFEdge();
	    if(fe.getSource() == fe.getTarget()) {
    		// back edge
    		if(se.getNewRate() > 1){
		    for(int j = 1; j< se.getNewRate(); j++ ) {
	    		cloneSNodeList(se);
		    }
		    se.setNewRate(1);
    		}
	    } else {
    		if(se.getNewRate() > 1){
		    // clone the whole ScheduleNode lists starting with se's target
		    for(int j = 1; j < se.getNewRate(); j++ ) {
			cloneSNodeList(se);
		    }
		    se.setNewRate(1);
		} else if (se.getNewRate() == 1) {
		    //merge the target ScheduleNode to the source ScheduleNode
		    ((ScheduleNode)se.getSource()).merge(se);
		    scheduleNodes.remove(se.getTarget());
		    scheduleEdges.removeElement(se);
		    se.setTarget((ScheduleNode)se.getSource());
    		}
	    }
    	}
    }
    
    private void cloneSNodeList(ScheduleEdge sEdge) {
    	Hashtable<ClassNode, ClassNode> cn2cn = new Hashtable<ClassNode, ClassNode>();
    	ScheduleNode csNode = (ScheduleNode)((ScheduleNode)sEdge.getTarget()).clone(cn2cn);
	scheduleNodes.add(csNode);
		
	// Clone all the external in ScheduleEdges
	int i;  	
	Vector inedges = sEdge.getTarget().getInedgeVector();
    	for(i = 0; i < inedges.size(); i++) {
	    ScheduleEdge tse = (ScheduleEdge)inedges.elementAt(i);
	    ScheduleEdge se = new ScheduleEdge(csNode, "new", tse.getTask(), tse.getClassDescriptor());
	    se.setProbability(tse.getProbability());
	    se.setNewRate(1);
	    se.setSourceCNode(tse.getSourceCNode());
	    se.setTargetCNode(cn2cn.get(tse.getTargetCNode()));
	    tse.getSource().addEdge(se);
	    scheduleEdges.add(se);
    	}
    	
    	Queue<ScheduleNode> toClone = new LinkedList<ScheduleNode>();
    	Queue<ScheduleNode> clone = new LinkedList<ScheduleNode>();
    	Queue<Hashtable> qcn2cn = new LinkedList<Hashtable>();
    	clone.add(csNode);
    	toClone.add((ScheduleNode)sEdge.getTarget());
    	qcn2cn.add(cn2cn);
    	while(!toClone.isEmpty()) {
	    Hashtable<ClassNode, ClassNode> tocn2cn = new Hashtable<ClassNode, ClassNode>();
	    csNode = clone.poll();
	    ScheduleNode osNode = toClone.poll();
	    cn2cn = qcn2cn.poll();
	    // Clone all the external ScheduleEdges and the following ScheduleNodes
	    Vector edges = osNode.getEdgeVector();
	    for(i = 0; i < edges.size(); i++) {
	    	ScheduleEdge tse = (ScheduleEdge)edges.elementAt(i);
	    	ScheduleNode tSNode = (ScheduleNode)((ScheduleNode)tse.getTarget()).clone(tocn2cn);
	    	scheduleNodes.add(tSNode);
	    	clone.add(tSNode);
	    	toClone.add((ScheduleNode)tse.getTarget());
	    	qcn2cn.add(tocn2cn);
	    	ScheduleEdge se = new ScheduleEdge(tSNode, "new", tse.getTask(), tse.getClassDescriptor());
	    	se.setProbability(tse.getProbability());
	    	se.setNewRate(tse.getNewRate());
	    	se.setSourceCNode(cn2cn.get(tse.getSourceCNode()));
	    	se.setTargetCNode(tocn2cn.get(tse.getTargetCNode()));
	    	csNode.addEdge(se);
	    	scheduleEdges.add(se);
	    }
    	}
    }
    
    public void schedule() {
    	// Assign a core to each ScheduleNode
    	int i = 0;
    	int coreNum = 1;
    	for(i = 0; i < scheduleNodes.size(); i++) {
	    ScheduleNode sn = scheduleNodes.elementAt(i);
	    sn.setCoreNum(coreNum++);
	    sn.listTasks();
	    // For each of the ScheduleEdge out of this ScheduleNode, add the target ScheduleNode into the queue inside sn
	    Iterator it_edges = sn.edges();
	    while(it_edges.hasNext()) {
    		ScheduleEdge se = (ScheduleEdge)it_edges.next();
    		ScheduleNode target = (ScheduleNode)se.getTarget();
    		sn.addTargetSNode(se.getTargetFState().getClassDescriptor(), target);
	    }
    	}
    }
    
    public void printScheduleGraph(String path) {
    	try {
	    File file=new File(path);
	    FileOutputStream dotstream=new FileOutputStream(file,false);
	    PrintWriter output = new java.io.PrintWriter(dotstream, true);
	    //ScheduleNode.DOTVisitor.visit(dotstream, scheduleNodes);
	    output.println("digraph G {");
	    //output.println("\tnode [fontsize=10,height=\"0.1\", width=\"0.1\"];");
	    //output.println("\tedge [fontsize=6];");
	    output.println("\tcompound=true;\n");
	    traverseSNodes(output);
	    output.println("}\n");
	            
    	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
    	}
    }
    
    private void traverseSNodes(PrintWriter output){
    	//Draw clusters representing ScheduleNodes
        Iterator it = scheduleNodes.iterator();
        while (it.hasNext()) {
	    ScheduleNode gn = (ScheduleNode) it.next();
            Iterator edges = gn.edges();
            output.println("\tsubgraph " + gn.getLabel() + "{");
            //output.println("\t\tstyle=dashed;");
            //output.println("\t\tlabel=\"" + gn.getTextLabel() + "\";");
            Iterator it_cnodes = gn.getClassNodesIterator();
            traverseCNodes(output, it_cnodes);
            //Draw the internal 'new' edges
            Iterator it_edges =gn.getScheduleEdgesIterator();
            while(it_edges.hasNext()) {
            	ScheduleEdge se = (ScheduleEdge)it_edges.next();
            	//output.println("\t" + se.getSourceFState().getLabel() + " -> " + se.getTargetFState().getLabel() + " [label=\"" + se.getLabel() + "\", color=red, ltail=" + se.getSourceCNode().getLabel() + "];");
            	output.println("\t" + se.getSourceCNode().getLabel() + " -> " + se.getTargetCNode().getLabel() + " [label=\"" + se.getLabel() + "\", color=red];");
            }
            output.println("\t}\n");
            //Draw 'new' edges of this ScheduleNode
            while(edges.hasNext()) {
            	ScheduleEdge se = (ScheduleEdge)edges.next();
            	//output.println("\t" + se.getSourceFState().getLabel() + " -> " + se.getTargetFState().getLabel() + " [label=\"" + se.getLabel() + "\", color=red, style=dashed, ltail=" + gn.getLabel() + "];");
            	output.println("\t" + se.getSourceCNode().getLabel() + " -> " + se.getTargetCNode().getLabel() + " [label=\"" + se.getLabel() + "\", color=red, style=dashed];");
            }
        }
    }
    
    private void traverseCNodes(PrintWriter output, Iterator it){
    	//Draw clusters representing ClassNodes
        while (it.hasNext()) {
	    ClassNode gn = (ClassNode) it.next();
            /*output.println("\tsubgraph " + gn.getLabel() + "{");
            output.println("\t\tstyle=dashed;");
            output.println("\t\tlabel=\"" + gn.getTextLabel() + "\";");
            traverseFlagStates(output, gn.getFlagStates());
            output.println("\t}\n");*/
	    output.println("\t\t" + gn.getLabel() + " [style=dashed, label=\"" + gn.getTextLabel() + "\", shape=box];");
        }
    }
    
    private void traverseFlagStates(PrintWriter output, Collection nodes) {
	Set cycleset=GraphNode.findcycles(nodes);
	Vector namers=new Vector();
	namers.add(new Namer());
	namers.add(new Allocations());
	//namers.add(new TaskEdges());
	    
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
                    	for(Iterator nodeit=nonmerge(node, nodes).iterator();nodeit.hasNext();) {
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

    private Set nonmerge(GraphNode gn, Collection nodes) {
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
