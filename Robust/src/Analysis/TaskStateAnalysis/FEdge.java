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
    /** Class Constructor
     * 
     */
    public FEdge(FlagState target, String label) {
	super(target);
	this.label = label;
    }
    
    public String getLabel() {
	return label;
    }
    
    public int hashCode(){
	return target.hashCode()^label.hashCode();
    }
	
    public boolean equals(Object o) {
        if (o instanceof FEdge) {
            FEdge e=(FEdge)o;
	    return e.label.equals(label)&&
		e.target.equals(target);
        }
        return false;
    }

    
}
