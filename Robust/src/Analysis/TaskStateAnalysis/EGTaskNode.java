package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import Util.GraphNode;

public class EGTaskNode extends GraphNode {
    private boolean source=false;
    private FlagState fs;
    private FlagState postfs;
    private TaskDescriptor td;
    private int index;
    private String name;
    private int uid;
    private static int nodeid;

    public EGTaskNode(String name, TaskDescriptor td, FlagState postfs){
	this(name, null, td, -1, postfs);
    }

    public EGTaskNode(String name, FlagState fs, TaskDescriptor td, int index, FlagState postfs){
	this.name=name;
	this.uid=nodeid++;
	this.fs = fs;
    	this.td = td;
	this.index=index;
	this.postfs=postfs;
    }

    public String getTextLabel() {
	return "Task "+getName()+"["+fs+"]->["+postfs+"]";
    }

    public String getName() {
	return name;
    }

    public String getLabel() {
	return "N"+uid;
    }
    
    public int getIndex() {
	return index;
    }

    public String toString() {
	return getTextLabel();
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
}
