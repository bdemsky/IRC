package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import Util.Edge;

public class Predicate{
    public HashSet vardescriptors;
    public Hashtable<VarDescriptor, HashSet<FlagExpressionNode>> flags;
    public Hashtable<VarDescriptor, TagExpressionList> tags; //if there is a tag change, we stop the analysis
    
    public Predicate(){
	this.vardescriptors = new HashSet();
	    this.flags = new Hashtable();
	    this.tags = new Hashtable();
    } 
}
