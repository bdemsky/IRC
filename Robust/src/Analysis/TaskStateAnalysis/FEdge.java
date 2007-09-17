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
    /** Class Constructor
     * 
     */
    public FEdge(FlagState target, String label, TaskDescriptor td, int parameterindex) {
	super(target);
	this.label = label;
	this.td=td;
	this.parameterindex=parameterindex;
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
		e.td==td&&
		e.parameterindex==parameterindex)
		return true;
        }
        return false;
    }
}
