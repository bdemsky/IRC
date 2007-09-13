package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import Util.GraphNode;

public class EGTaskNode extends TaskNode {
    private boolean source=false;
    private int loopmarker=0;
    private boolean multipleparams=false;
    private boolean optional = false;
    private boolean marked=false;
    private boolean tomention=true;
    private int type = 0;
    private FlagState fs;
    private TaskDescriptor td;
    protected HashSet edges = new HashSet();
    public EGTaskNode(){
	this("default", null, null);
    }
    
    public EGTaskNode(String name){
	this(name, null, null);
    }

    public EGTaskNode(String name, FlagState fs){
	this(name, fs, null);
    }

    public EGTaskNode(String name, TaskDescriptor td){
	this(name, null, td);
    }

    public EGTaskNode(String name, FlagState fs, TaskDescriptor td){
	super(name);
	this.fs = fs;
    	this.td = td;
    }
    
    public int hashCode(){
	return getLabel().hashCode();
    }
    
    public boolean equals(Object o){
	if(o instanceof EGTaskNode){
	    EGTaskNode tn=(EGTaskNode) o;
	    return tn.getLabel().equals(getLabel());
	}
	return false;
    }

    public HashSet getEdgeSet(){
	return edges;
    }

    public void addEdge(EGEdge newedge) {
	newedge.setSource(this);
        edges.add(newedge);
	EGTaskNode tonode=newedge.getTarget();
	tonode.inedges.addElement(newedge);
    }

    public Iterator edges(){
	return edges.iterator();
    }
    
    public TaskDescriptor getTD(){
	return td;
    }
        
    public void setSource(){
	source = true;
    }

    public boolean isSource(){
	return source;
    }

    public int getuid(){
	return uid;
    }

    public void doSelfLoopMarking(){
	loopmarker=1;
    }

    public void doLoopMarking(){
	loopmarker=2;
    }
	    
    public boolean isSelfLoop(){
	if (loopmarker==1) return true;
	else return false;
    }

    public boolean isLoop(){
	if (loopmarker==2) return true;
	else return false;
    }

    public void setMultipleParams(){
	multipleparams=true;
    }

    public boolean isMultipleParams(){
	return multipleparams;
    }
    
    public void setOptional(){
	optional = true;
    }

    public boolean isOptional(){
	return optional;
    }

    public void mark(){
	marked = true;
    }

    public void unMark(){
	marked = false;
    }
    
    public boolean isMarked(){
	return marked;
    }

    public String getFSName(){
	if(fs == null) return "no flag";
	else return fs.getTextLabel();
    }
    
    public FlagState getFS(){
	return fs;
    }

    public void dontMention(){
	tomention = false;
    }

    public boolean toMention(){
	return tomention;
    }
    
    public void setAND(){
	type = 1;
    }
    
    public void setOR(){
	type = 0;
    }
    
    public int type(){
	return type;
    }
}
