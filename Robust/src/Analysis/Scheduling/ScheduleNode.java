package Analysis.Scheduling;

import Analysis.TaskStateAnalysis.*;
import IR.*;
import java.util.*;

import Util.GraphNode;

/** This class holds flag transition diagram(s) can be put on one core.
 */
public class ScheduleNode extends GraphNode implements Cloneable{
    
    private int uid;
    private static int nodeID=0;

    private int coreNum;
    private Vector tasks;
    private Hashtable<ClassDescriptor, Vector<ScheduleNode>> targetSNodes; 
    private boolean sorted = false;
    
    private boolean clone = false;

    private Vector<ClassNode> classNodes;
    Vector<ScheduleEdge> scheduleEdges;

    /** Class constructor
     *	@param cd ClassDescriptor
     *  @param fStates
     */
    public ScheduleNode() {
    	this.uid=ScheduleNode.nodeID++;
    	this.coreNum = -1;
    }
    
    public ScheduleNode(ClassNode cn) {
    	this.uid=ScheduleNode.nodeID++;
    	this.coreNum = -1;
    	this.classNodes = new Vector<ClassNode>();
    	this.scheduleEdges = new Vector<ScheduleEdge>();
    	this.classNodes.add(cn);
    	this.addEdge(cn.getEdgeVector());
    }
   
    public int getuid() {
    	return uid;
    }
    
    public int getCoreNum() {
    	return this.coreNum;
    }
    
    public void setCoreNum(int coreNum) {
    	this.coreNum = coreNum;
    }
    
    public void addTargetSNode(ClassDescriptor cd, ScheduleNode sn) {
    	if(this.targetSNodes == null) {
	    this.targetSNodes = new Hashtable<ClassDescriptor, Vector<ScheduleNode>>();
    	}
    	
    	if(!this.targetSNodes.containsKey(cd)) {
	    this.targetSNodes.put(cd, new Vector<ScheduleNode>());
    	}
    	this.targetSNodes.get(cd).add(sn);
    }
    
    public void listTasks() {
    	if(this.tasks == null) {
	    this.tasks = new Vector();
    	}
    	
    	int i = 0;
    	for(i = 0; i < classNodes.size(); i++) {
	    Iterator it_flags = classNodes.elementAt(i).getFlags();
	    while(it_flags.hasNext()) {
		FlagState fs = (FlagState)it_flags.next();
		Iterator it_edges = fs.edges();
		while(it_edges.hasNext()) {
		    TaskDescriptor td = ((FEdge)it_edges.next()).getTask();
		    if(!this.tasks.contains(td)) {
			this.tasks.add(td);
		    }
		}
	    }
    	}
    }
    
    public void addTask(TaskDescriptor task){
    	tasks.add(task);
    }
    
    public Vector getTasks(){
    	return tasks;
    }
    
    public boolean isSorted() {
    	return sorted;
    }
    
    public void setSorted(boolean sorted) {
    	this.sorted = sorted;
    }
    
    public boolean isclone() {
    	return clone;
    }
    
    public String toString() {
    	String temp = new String("");
    	for(int i = 0; i < classNodes.size(); i++) {
	    temp += classNodes.elementAt(i).getClassDescriptor().toString() + ", ";
    	}
    	temp += getTextLabel();
    	return temp;
    }

    public Vector getClassNodes() {
    	if(classNodes == null) {
	    classNodes = new Vector<ClassNode>();
    	}
    	return classNodes;
    }
    
    public Iterator getClassNodesIterator() {
    	return classNodes.iterator();
    }
    
    public void resetClassNodes() {
    	classNodes = null;
    }
    
    public Vector getScheduleEdges() {
    	if(scheduleEdges == null) {
	    scheduleEdges = new Vector<ScheduleEdge>();
    	}
    	return scheduleEdges;
    }
    
    public Iterator getScheduleEdgesIterator() {
    	return scheduleEdges.iterator();
    }
    
    public void resetScheduleEdges() {
    	scheduleEdges = null;
    }
    
    /** Tests for equality of two flagstate objects.
    */
    
    public boolean equals(Object o) {
        if (o instanceof ScheduleNode) {
	    ScheduleNode fs=(ScheduleNode)o;
            if ((fs.getCoreNum() != this.coreNum) ||
		(fs.sorted != this.sorted) ||
		(fs.clone != this.clone)){ 
                return false;
            }
            if(fs.tasks != null) {
            	if(!fs.tasks.equals(this.tasks)) {
		    return false;
            	}
            } else if (tasks != null) {
            	return false;
            }
            if (fs.targetSNodes != null) {
            	if(!fs.targetSNodes.equals(this.targetSNodes)) {
		    return false;
            	}
            } else if(this.targetSNodes != null) {
            	return false;
            }
	    if(fs.classNodes != null) {
		if(!fs.classNodes.equals(classNodes)) {
		    return false;
		}
	    } else if(classNodes != null) {
		return false;
	    }
	    return (fs.scheduleEdges.equals(scheduleEdges));
        }
        return false;
    }

    public int hashCode() {
        return classNodes.hashCode()^scheduleEdges.hashCode();
    }

    public String getLabel() {
    	return "cluster" + uid;
    }	

    public String getTextLabel() {
	String label=null;
	if(this.coreNum != -1) {
	    label = "Core " + this.coreNum;
	}
	
	if (label==null)
	    return " ";
	return label;
    }
    
    public Object clone(Hashtable<ClassNode, ClassNode> cn2cn) {
    	ScheduleNode o = null;
    	try {
	    o = (ScheduleNode)super.clone();
    	} catch(CloneNotSupportedException e){
	    e.printStackTrace();
    	}
    	o.uid = ScheduleNode.nodeID++;
    	// Clone all the internal ClassNodes and ScheduleEdges
    	Vector<ClassNode> tcns = new Vector<ClassNode>();
    	Vector<ScheduleEdge> tses = new Vector<ScheduleEdge>();
    	int i = 0;
    	for(i = 0; i < this.classNodes.size(); i++) {
	    ClassNode tcn = this.classNodes.elementAt(i);
	    ClassNode cn = (ClassNode)tcn.clone();
	    cn.setScheduleNode(o);
	    tcns.add(cn);
	    cn2cn.put(tcn, cn);
    	}
    	for(i = 0; i < this.scheduleEdges.size(); i++) {
	    ScheduleEdge temp = this.scheduleEdges.elementAt(i);
	    ScheduleEdge se = new ScheduleEdge(o, "new", temp.getTask(), temp.getClassDescriptor());
	    se.setSourceCNode(cn2cn.get(temp.getSourceCNode()));
	    se.setTargetCNode(cn2cn.get(temp.getTargetCNode()));
	    tses.add(se);
    	}
    	o.classNodes = tcns;
    	o.scheduleEdges = tses;
    	tcns = null;
    	tses = null;
    	o.inedges = new Vector();
    	o.edges = new Vector();
    	
    	o.clone = true;
    	return o;
    }
    
    public void merge(ScheduleEdge se) {
    	Vector<ClassNode> targetCNodes = (Vector<ClassNode>)((ScheduleNode)se.getTarget()).getClassNodes();
    	Vector<ScheduleEdge> targetSEdges = (Vector<ScheduleEdge>)((ScheduleNode)se.getTarget()).getScheduleEdges();
    	
    	for(int i = 0; i <  targetCNodes.size(); i++) {
	    targetCNodes.elementAt(i).setScheduleNode(this);
    	}
    	
    	if(classNodes == null) {
	    classNodes = targetCNodes;
	    scheduleEdges = targetSEdges;
    	} else {
	    if(targetCNodes.size() != 0) {
		classNodes.addAll(targetCNodes);
	    }
	    if(targetSEdges.size() != 0) {
		scheduleEdges.addAll(targetSEdges);
	    }
    	}
    	targetCNodes = null;
	targetSEdges = null;
    	
    	scheduleEdges.add(se);
    	se.getTarget().removeInedge(se);
    	this.removeEdge(se);
    	//this.addEdge(se.getTarget().getEdgeVector());
    	Iterator it_edges = se.getTarget().edges();
    	while(it_edges.hasNext()) {
	    ScheduleEdge tse = (ScheduleEdge)it_edges.next();
	    tse.setSource(this);
	    this.edges.addElement(tse);
    	}
    }
}
