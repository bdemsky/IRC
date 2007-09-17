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
    private boolean tomention=true;
    private FlagState fs;
    private FlagState postfs;
    private TaskDescriptor td;
    private int index;

    public EGTaskNode(String name, TaskDescriptor td, FlagState postfs){
	this(name, null, td, -1, postfs);
    }

    public EGTaskNode(String name, FlagState fs, TaskDescriptor td, int index, FlagState postfs){
	super(name);
	this.fs = fs;
    	this.td = td;
	this.index=index;
	this.postfs=postfs;
    }
    
    public int getIndex() {
	return index;
    }

    public FlagState getPostFS() {
	return postfs;
    }
    
    public boolean isRuntime() {
	return td==null&&getName().equals("Runtime");
    }


    public boolean isOptional() {
	return (!isSource()&&td!=null&&td.isOptional(td.getParameter(index)));
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

    public boolean isMultipleParams(){
	return getTD()!=null&&getTD().numParameters()>1;
    }
    
    public String getFSName(){
	if(fs == null) 
	    return "no flag";
	else 
	    return fs.getTextLabel();
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
}
