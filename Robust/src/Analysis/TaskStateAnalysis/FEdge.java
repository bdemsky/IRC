package Analysis.TaskStateAnalysis;
import IR.*;
import Analysis.TaskStateAnalysis.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import Util.Edge;

/* Edge *****************/

public class FEdge extends Edge {

    private String label;
    private TaskDescriptor td;
    private int parameterindex;
    
    // jzhou
    private int executeTime;
    private Hashtable<ClassDescriptor, NewObjInfo> newObjInfos;
    
    public class NewObjInfo {
    	int newRate;
    	int probability;
    	
    	public NewObjInfo() {
	    newRate = 0;
	    probability = 0;
    	}
    	
    	public NewObjInfo(int newRate, int probability) {
	    this.newRate = newRate;
	    this.probability = probability;
    	}
    	
    	public int getNewRate() {
	    return this.newRate;
    	}
    	
    	public void setNewRate(int newRate) {
	    this.newRate = newRate;
    	}
    	
    	public int getProbability() {
	    return this.probability;
    	}
    	
    	public void setProbability(int probability) {
	    this.probability = probability;
    	}
    	
    	public boolean equals(Object o) {
            if (o instanceof NewObjInfo) {
            	NewObjInfo e=(NewObjInfo)o;
		if (e.newRate == this.newRate &&
		    e.probability == this.probability) {
		    return true;
		}
            }
            return false;
        }
    }
    
    /** Class Constructor
     * 
     */
    public FEdge(FlagState target, String label, TaskDescriptor td, int parameterindex) {
	super(target);
	this.label = label;
	this.td=td;
	this.parameterindex=parameterindex;
	this.executeTime = -1;
	this.newObjInfos = null;
    }
    
    public String getLabel() {
	return label;
    }
    
    public int hashCode(){
	return target.hashCode()^label.hashCode();
    }

    public TaskDescriptor getTask() {
	return td;
    }

    public int getIndex() {
	return parameterindex;
    }
	
    public boolean equals(Object o) {
        if (o instanceof FEdge) {
            FEdge e=(FEdge)o;
	    if (e.label.equals(label)&&
		e.target.equals(target)&&
		e.source.equals(source) &&
		e.td==td&&
		e.parameterindex==parameterindex &&
		e.executeTime == executeTime) {
	    	if(this.newObjInfos != null) {
		    if(e.newObjInfos == null) {
			return false;
		    } else if(e.newObjInfos.equals(this.newObjInfos)) {
			return true;
		    }
	    	}
	    }
        }
        return false;
    }
    
    public int getExeTime() {
    	return this.executeTime;
    }
    
    public void setExeTime(int eTime) {
    	this.executeTime = eTime;
    }
    
    public Hashtable<ClassDescriptor, NewObjInfo> getNewObjInfoHashtable() {
    	return this.newObjInfos;
    }
    
    public NewObjInfo getNewObjInfo(ClassDescriptor cd) {
    	if(this.newObjInfos == null) {
	    return null;
    	}
    	return this.newObjInfos.get(cd);
    }
    
    public void addNewObjInfo(ClassDescriptor cd, int newRate, int probability) {
    	if(this.newObjInfos == null) {
	    this.newObjInfos = new Hashtable<ClassDescriptor, NewObjInfo>();
    	}
    	this.newObjInfos.put(cd, new NewObjInfo(newRate, probability));
    }
}
