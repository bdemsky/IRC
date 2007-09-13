package Analysis.TaskStateAnalysis;
import IR.*;
import Analysis.TaskStateAnalysis.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import Util.Edge;


public class EGEdge extends Edge{
    public EGEdge(EGTaskNode target){
	super(target);
    }

    public EGTaskNode getTarget(){
	return (EGTaskNode) target;
    }
}
