package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import Util.Edge;

public class Predicate {
    public HashSet<VarDescriptor> vardescriptors;
    public Hashtable<VarDescriptor, HashSet<FlagExpressionNode>> flags;
    public Hashtable<VarDescriptor, TagExpressionList> tags; 
    //if there is a tag change, we stop the analysis
    
    public Predicate(){
	this.vardescriptors = new HashSet<VarDescriptor>();
	this.flags = new Hashtable<VarDescriptor, HashSet<FlagExpressionNode>>();
	this.tags = new Hashtable<VarDescriptor, TagExpressionList>();
    } 

    public boolean equals(Object o) {
	if (o instanceof Predicate) {
	    Predicate p=(Predicate)o;
	    if (vardescriptors.equals(p.vardescriptors)&&
		flags.equals(p.flags)&&
		tags.equals(p.tags))
		return true;
	}
	return false;
    }
    public int hashCode() {
	return vardescriptors.hashCode();
    }
}
