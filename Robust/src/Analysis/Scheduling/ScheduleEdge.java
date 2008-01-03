package Analysis.Scheduling;
import IR.*;
import Analysis.TaskStateAnalysis.*;
import Util.Edge;

/* Edge *****************/

public class ScheduleEdge extends Edge {

    private String label;
    private final TaskDescriptor td;
    private final ClassDescriptor cd;
    private FEdge fedge;
    private FlagState targetFState;
    private ClassNode sourceCNode;
    private ClassNode targetCNode;
    private int newRate;
    private int probability;
    //private int parameterindex;
    
    /** Class Constructor
     * 
     */
    public ScheduleEdge(ScheduleNode target, String label, TaskDescriptor td, ClassDescriptor cd/*, int parameterindex*/) {
    	super(target);
    	this.fedge = null;
    	this.targetFState = null;
    	this.sourceCNode = null;
    	this.targetCNode = null;
    	this.label = label;
    	this.td = td;
    	this.cd = cd;
    	this.newRate = 1;
    	this.probability = 100;
    	//this.parameterindex = parameterindex;
    }
    
    public void setTarget(ScheduleNode sn) {
    	this.target = sn;
    }
    
    public String getLabel() {
    	String completeLabel = label + ":" + Integer.toString(this.newRate) + ":(" + Integer.toString(this.probability) + ")";
    	return completeLabel;
    }
    
    public int hashCode(){
	return target.hashCode()^label.hashCode();
    }

    public TaskDescriptor getTask() {
    	return td;
    }
    
    public ClassDescriptor getClassDescriptor() {
    	return cd;
    }
    
    public FEdge getFEdge() {
    	return this.fedge;
    }
    
    public void setFEdge(FEdge fEdge) {
    	this.fedge = fEdge;
    }
    
    public FlagState getSourceFState() {
    	if(this.fedge == null) {
	    return null;
    	}
    	return (FlagState)this.fedge.getTarget();
    }
    
    public void setTargetFState(FlagState targetFState) {
    	this.targetFState = targetFState;
    }
    
    public FlagState getTargetFState() {
    	return this.targetFState;
    }
    
    public int getProbability() {
    	return this.probability;
    }
    
    public int getNewRate() {
    	return this.newRate;
    }
    
    public ClassNode getSourceCNode() {
    	return this.sourceCNode;
    }
    
    public void setSourceCNode(ClassNode sourceCNode) {
    	this.sourceCNode = sourceCNode;
    }
    
    public ClassNode getTargetCNode() {
    	return this.targetCNode;
    }
    
    public void setTargetCNode(ClassNode targetCNode) {
    	this.targetCNode = targetCNode;
    }

   /* public int getIndex() {
	return parameterindex;
    }*/
	
    public boolean equals(Object o) {
        if (o instanceof ScheduleEdge) {
	    ScheduleEdge e=(ScheduleEdge)o;
	    if ((e.label.equals(label))&&
		(e.target.equals(target))&&
		(e.td.equals(td)) && 
		(e.cd.equals(cd)) && 
		(e.fedge.equals(fedge)) && 
		(e.targetFState.equals(targetFState)) && 
		(e.sourceCNode.equals(sourceCNode)) &&
		(e.targetCNode.equals(targetCNode)) &&
		(e.newRate == newRate) && 
		(e.probability == probability)/*&&
		e.getIndex()==parameterindex*/)
		return true;
        }
        return false;
    }
    
    public void setProbability(int prob) {
    	this.probability = prob;
    }
    
    public void setNewRate(int nr) {
    	this.newRate = nr;
    }
}
