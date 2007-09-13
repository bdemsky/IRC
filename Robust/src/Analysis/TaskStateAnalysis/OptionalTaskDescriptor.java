package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.io.*;
import Util.Edge;

public class  OptionalTaskDescriptor {
    public TaskDescriptor td;
    public HashSet flagstates;
    public int depth;
    public HashSet<HashSet> exitfses;
    public Predicate predicate;
    private static int nodeid=0;
    private int uid;
    
    protected OptionalTaskDescriptor(TaskDescriptor td, HashSet flagstates, int depth, Predicate predicate) {
	this.td = td;
	this.flagstates = flagstates;
	this.depth = depth;
	this.exitfses = new HashSet();
	this.predicate = predicate;
	this.uid = OptionalTaskDescriptor.nodeid++;
    }
    
    public boolean equals(Object o){
	if (o instanceof OptionalTaskDescriptor) {
	    OptionalTaskDescriptor otd = (OptionalTaskDescriptor) o;
	    if (td==otd.td&&
		flagstates.equals(otd.flagstates)&&
		predicate.equals(otd.predicate))
		return true;
	}
	return false;
    }
    
    public int hashCode() {
	return td.getSymbol().hashCode()+flagstates.hashCode()+predicate.hashCode();
    }
    
    public String tostring() {
	return "Optional task "+td.getSymbol();
    }

    public int getuid() {
	return uid;
    }
}
