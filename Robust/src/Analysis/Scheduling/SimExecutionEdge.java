package Analysis.Scheduling;

import java.util.Vector;

import IR.TaskDescriptor;
import Util.Edge;

public class SimExecutionEdge extends Edge {
    
    private int eid;
    private static int nodeID=0;
    
    private int coreNum;
    private TaskDescriptor td;
    private Vector<Integer> taskparams;
    private int weight;
    
    private int bestStartPoint;
    private SimExecutionNode lastpredicatenode;
    private SimExecutionEdge lastpredicateedge;
    private Vector<SimExecutionEdge> predicates;
    private boolean isFixedTime;

    public SimExecutionEdge(SimExecutionNode target,
	                    int corenum,
	                    TaskDescriptor td,
	                    int weight,
                            Vector<Integer> taskparams) {
	super(target);
	this.eid = SimExecutionEdge.nodeID++;
	this.coreNum = corenum;
	this.td = td;
	this.taskparams = taskparams;
	this.weight = weight;
	this.bestStartPoint = -1;
	this.lastpredicatenode = null;
	this.lastpredicateedge = null;
	this.predicates = null;
	this.isFixedTime = true;
    }
    
    public int getBestStartPoint() {
	if(this.bestStartPoint == -1) {
	    if(this.predicates.size() > 0) {
		// have predicates
		int starttime = 0;
		// check the latest finish time of all the predicates
		for(int j = 0; j < this.predicates.size(); j++) {
		    SimExecutionEdge predicate = this.predicates.elementAt(j);
		    int tmptime = predicate.getBestStartPoint() + predicate.getWeight();
		    if(tmptime > starttime) {
			starttime = tmptime;
			this.lastpredicateedge = predicate;
			if(predicate.getTd() != null) {
			    this.lastpredicatenode = (SimExecutionNode)predicate.getTarget();
			} else {
			    // transfer edge
			    this.lastpredicatenode = (SimExecutionNode)predicate.getSource();
			}
		    }
		}
		this.bestStartPoint = starttime;
	    } else {
		// no predicates
		this.bestStartPoint = 0;
	    }
	}
        return bestStartPoint;
    }

    public void setBestStartPoint(int bestStartPoint) {
        this.bestStartPoint = bestStartPoint;
    }
    
    public Vector<SimExecutionEdge> getPredicates() {
        return predicates;
    }
    
    public void addPredicate(SimExecutionEdge predicate) {
	if(this.predicates == null) {
	    this.predicates = new Vector<SimExecutionEdge>();
	}
	if(!this.predicates.contains(predicate)) {
	    this.predicates.add(predicate);
	}
    }

    public Vector<Integer> getTaskparams() {
        return taskparams;
    }

    public TaskDescriptor getTd() {
        return td;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getCoreNum() {
        return coreNum;
    }

    public SimExecutionNode getLastpredicateNode() {
        return lastpredicatenode;
    }

    public void setLastpredicateNode(SimExecutionNode lastpredicatenode) {
        this.lastpredicatenode = lastpredicatenode;
    }

    public SimExecutionEdge getLastpredicateEdge() {
        return lastpredicateedge;
    }

    public void setLastpredicateEdge(SimExecutionEdge lastpredicateedge) {
        this.lastpredicateedge = lastpredicateedge;
    }

    public boolean isFixedTime() {
        return isFixedTime;
    }

    public void setFixedTime(boolean isFixedTime) {
        this.isFixedTime = isFixedTime;
    }

    public String getLabel() {
	String completeLabel = (this.td != null? this.td.getSymbol():"")
	                       + "(" + this.weight + " | " + this.bestStartPoint + ")";
	return completeLabel;
    }
    
    public void destroy() {
	this.td = null;
	if(this.taskparams != null) {
	    this.taskparams.clear();
	    this.taskparams = null;
	}
	this.lastpredicatenode = null;
	this.lastpredicateedge = null;
	if(this.predicates != null) {
	    this.predicates.clear();
	    this.predicates = null;
	}
	this.source.getEdgeVector().clear();
	this.source.getInedgeVector().clear();
	this.source = null;
	this.target.getEdgeVector().clear();
	this.target.getInedgeVector().clear();
	this.target = null;
    }
}
