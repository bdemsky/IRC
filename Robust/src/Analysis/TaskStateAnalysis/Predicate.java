package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import Util.Edge;

public class Predicate{
    public Hashtable<String, VarDescriptor> vardescriptors;
    public Hashtable<String, HashSet<FlagExpressionNode>> flags;
    public Hashtable<String, TagExpressionList> tags; //if there is a tag change, we stop the analysis
    
    public Predicate(){
	this.vardescriptors = new Hashtable();
	this.flags = new Hashtable();
	this.tags = new Hashtable();
    } 

    public boolean equals(Object o){
	if(o instanceof Predicate){
	    Predicate p = (Predicate) o;
	    if(this.vardescriptors.equals(p.vardescriptors))
		return true;
	    return false;
	}
	else return false;
    }

    public int hashCode(){
	return vardescriptors.hashCode();
    }


}
