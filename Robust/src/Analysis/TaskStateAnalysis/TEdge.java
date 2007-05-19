package Analysis.TaskStateAnalysis;
import IR.*;
import Analysis.TaskStateAnalysis.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import Util.Edge;


public class TEdge extends Edge{
	
	private String target;
	
	public TEdge(TaskNode target){
		super(target);
	}
	
	   
	 
    public int hashCode(){
	return target.hashCode()^target.toString().hashCode();
    }
    
	
    public boolean equals(Object o) {
        if (o instanceof TEdge) {
            TEdge e=(TEdge)o;
	    return e.target.equals(target);
        }
        return false;
    }

    
}
