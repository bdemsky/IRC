package Analysis.Scheduling;

import Analysis.TaskStateAnalysis.*;
import IR.*;
import java.util.*;

import Util.GraphNode;

/** This class holds flag transition diagram(s) can be put on one core.
 */
public class ScheduleNode extends GraphNode implements Cloneable{
    
    private int uid;
    private int gid;
    private static int nodeID=0;

    private Vector<ClassNode> classNodes;
    Vector<ScheduleEdge> scheduleEdges;
    
    private int executionTime;
    
    private int coreNum;
    private Vector tasks;
    private Hashtable<ClassDescriptor, Vector<ScheduleNode>> targetSNodes; 
    private boolean sorted = false;

    /** Class constructor
     *	@param cd ClassDescriptor
     *  @param fStates
     */
    public ScheduleNode(int gid) {
    	this.uid = ScheduleNode.nodeID++;
    	this.gid = gid;
    	this.coreNum = -1;
    	this.executionTime = -1;
    }
    
    public ScheduleNode(ClassNode cn, int gid) {
    	this.uid = ScheduleNode.nodeID++;
    	this.gid = gid;
    	this.coreNum = -1;
    	this.classNodes = new Vector<ClassNode>();
    	this.scheduleEdges = new Vector<ScheduleEdge>();
    	this.classNodes.add(cn);
    	this.addEdge(cn.getEdgeVector());
    	this.executionTime = -1;
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
    
    public String toString() {
    	String temp = new String("");
    	for(int i = 0; i < classNodes.size(); i++) {
	    temp += classNodes.elementAt(i).getClassDescriptor().toString() + ", ";
    	}
    	temp += getTextLabel();
    	return temp;
    }
    
    public Vector getClassNodes() {
    	return classNodes;
    }
    
    public Iterator getClassNodesIterator() {
	if(classNodes == null) {
	    return null;
    	}
    	return classNodes.iterator();
    }
    
    public void resetClassNodes() {
    	classNodes = null;
    }
    
    public Vector getScheduleEdges() {
    	return scheduleEdges;
    }
    
    public Iterator getScheduleEdgesIterator() {
	if(scheduleEdges == null) {
	    return null;
    	}
    	return scheduleEdges.iterator();
    }
    
    public void resetScheduleEdges() {
    	scheduleEdges = null;
    }
    
    public int getExeTime() {
	if(this.executionTime == -1) {
	    try {
		calExeTime();
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    	return this.executionTime;
    }
    
    public void calExeTime() throws Exception {
    	if(this.classNodes.size() != 1) {
	    throw new Exception("Error: there are multiple ClassNodes inside the ScheduleNode when calculating executionTime");
    	}
    	ClassNode cn = this.classNodes.elementAt(0);
	if(!cn.isSorted()) {
	    throw new Error("Error: Non-sorted ClassNode!");
	}
	this.executionTime = cn.getFlagStates().elementAt(0).getExeTime();
    }
    
    /** Tests for equality of two flagstate objects.
    */
    
    public boolean equals(Object o) {
        if (o instanceof ScheduleNode) {
	    ScheduleNode fs=(ScheduleNode)o;
	    if(fs.gid == this.gid) {
		if(fs.uid != this.uid) {
		    return false;
		}
	    }
            if ((fs.sorted != this.sorted) ||
		(fs.executionTime != this.executionTime)){ 
                return false;
            }
            if(fs.classNodes != null) {
		if(!fs.classNodes.equals(classNodes)) {
		    return false;
		}
	    } else if(classNodes != null) {
		return false;
	    }
            return true;
        }
        return false;
    }

    public int hashCode() {
	int hashcode = gid^uid^Boolean.toString(sorted).hashCode()^executionTime;//^scheduleEdges.hashCode();
	if(this.classNodes != null) {
	    hashcode ^= classNodes.hashCode();
	}
        return hashcode;
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
    
    public Object clone(Hashtable<ClassNode, ClassNode> cn2cn, int gid) {
    	ScheduleNode o = null;
    	try {
	    o = (ScheduleNode)super.clone();
    	} catch(CloneNotSupportedException e){
	    e.printStackTrace();
    	}
    	o.uid = ScheduleNode.nodeID++;
    	o.gid = gid;
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
	    ScheduleEdge se = null;
	    if(!temp.getIsNew()) {
		se = new ScheduleEdge(o, "transmit",temp.getClassDescriptor(), false, gid);
	    } else {
		se = new ScheduleEdge(o, "new",temp.getClassDescriptor(), gid);
	    }
	    se.setSourceCNode(cn2cn.get(temp.getSourceCNode()));
	    se.setTargetCNode(cn2cn.get(temp.getTargetCNode()));
	    se.setProbability(temp.getProbability());
	    se.setNewRate(temp.getNewRate());
	    se.setTransTime(temp.getTransTime());
	    tses.add(se);
    	}
    	o.classNodes = tcns;
    	o.scheduleEdges = tses;
    	tcns = null;
    	tses = null;
    	o.inedges = new Vector();
    	o.edges = new Vector();

    	return o;
    }
    
    public void mergeSEdge(ScheduleEdge se) {
	assert(se.getIsNew());
	
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
    	se.resetListExeTime();
    	se.getTarget().removeInedge(se);
    	this.removeEdge(se);
    	Iterator it_edges = se.getTarget().edges();
    	while(it_edges.hasNext()) {
	    ScheduleEdge tse = (ScheduleEdge)it_edges.next();
	    tse.setSource(this);
	    this.edges.addElement(tse);
	}
    	
    	// As all tasks inside one ScheduleNode are executed sequentially,
    	// simply add the execution time of all the ClassNodes inside one ScheduleNode. 
    	if(this.executionTime == -1) {
	    this.executionTime = 0;
    	}
    	this.executionTime += ((ScheduleNode)se.getTarget()).getExeTime();
    }
    
    public void mergeSNode(ScheduleNode sn) throws Exception {
    	Vector<ClassNode> targetCNodes = (Vector<ClassNode>)sn.getClassNodes();
    	Vector<ScheduleEdge> targetSEdges = (Vector<ScheduleEdge>)sn.getScheduleEdges();
    	
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

    	Iterator it_edges = sn.edges();
    	while(it_edges.hasNext()) {
	    ScheduleEdge tse = (ScheduleEdge)it_edges.next();
	    tse.setSource(this);
	    this.edges.addElement(tse);
    	}
    	
    	// As all tasks inside one ScheduleNode are executed sequentially,
    	// simply add the execution time of all the ClassNodes inside one ScheduleNode. 
    	if(this.executionTime == -1) {
	    throw new Exception("Error: ScheduleNode without initiate execution time when analysising.");
    	}
    	if(this.executionTime < sn.getExeTime()) {
	    this.executionTime = sn.getExeTime();
    	}
    }
}
