package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.io.*;
import Util.Edge;

public class  MyOptional{
    public TaskDescriptor td;
    public HashSet flagstates;
    public int depth;
    public HashSet<Hashtable> exitfses;
    public Predicate predicate;
    
    protected MyOptional(TaskDescriptor td, HashSet flagstates, int depth, Predicate predicate){
	this.td = td;
	this.flagstates = flagstates;
	this.depth = depth;
	this.exitfses = new HashSet();
	this.predicate = predicate;
	
    }
    
    public boolean equals(Object o){
	if (o instanceof MyOptional) {
	    MyOptional myo = (MyOptional) o;
	    if (this.td.getSymbol().compareTo(myo.td.getSymbol())==0)
		if(this.flagstates.equals(myo.flagstates))
		    return true;
	    return false;
	}
	else return false;
	
    }
    
    public int hashCode() {
	return td.hashCode()+flagstates.hashCode()+exitfses.hashCode();
    }
    
    public String tostring() {
	return "Optional task "+td.getSymbol();
    }
    
}
