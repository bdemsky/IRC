package Analysis.TaskStateAnalysis;
import IR.*;
import Analysis.TaskStateAnalysis.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import Util.Edge;


public class EGEdge extends Edge{
    EGTaskNode target;

    
    public EGEdge(EGTaskNode target){
	super(target);
	this.target = target;
    }

    public EGTaskNode getTarget(){
	return target;
    }
		 
    public int hashCode(){
	return target.hashCode();
    }
    	
    public boolean equals(Object o) {
        if (o instanceof EGEdge) {
            EGEdge e=(EGEdge)o;
	    return e.target.equals(target);
        }
        return false;
    }

    
}
