package Analysis.Scheduling;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;

import Analysis.TaskStateAnalysis.FlagState;
import IR.TaskDescriptor;

/** This class holds flag transition diagram(s) can be put on one core.
 */
public class Schedule {
    private int coreNum;
    private Vector<TaskDescriptor> tasks;
    private Hashtable<FlagState, Queue<Integer>> targetCores;
    private Hashtable<FlagState, FlagState> targetFState;
    private Vector<Integer> ancestorCores;
    private Vector<Integer> childCores;
    private Hashtable<FlagState, Vector<Integer>> allyCores;
    private Hashtable<TaskDescriptor, Vector<FlagState>> td2fs;
    
    public Schedule(int coreNum) {
	super();
	this.coreNum = coreNum;
	this.tasks = null;
	this.targetCores = null;
	this.targetFState = null;
	this.ancestorCores = null;
	this.allyCores = null;
	this.td2fs = null;
    }

    public int getCoreNum() {
        return coreNum;
    }
    
    public Hashtable<FlagState, Queue<Integer>> getTargetCoreTable() {
        return targetCores;
    }
    
    public Queue<Integer> getTargetCores(FlagState fstate) {
	if(targetCores == null) {
	    return null;
	}
	return targetCores.get(fstate);
    }
    
    public Hashtable<FlagState, FlagState> getTargetFStateTable() {
        return targetFState;
    }
    
    public FlagState getTargetFState(FlagState fstate) {
	if(targetFState == null) {
	    return null;
	}
	return targetFState.get(fstate);
    }
    
    public Hashtable<FlagState, Vector<Integer>> getAllyCoreTable() {
        return this.allyCores;
    }
    
    public Vector<Integer> getAllyCores(FlagState fstate) {
	if(this.allyCores == null) {
	    return null;
	}
	return this.allyCores.get(fstate);
    }
    
    public Hashtable<TaskDescriptor, Vector<FlagState>> getTd2FsTable() {
        return this.td2fs;
    }
    
    public Vector<FlagState> getFStates4TD(TaskDescriptor td) {
	if(this.td2fs == null) {
	    return null;
	}
	return this.td2fs.get(td);
    }

    public void addTargetCore(FlagState fstate, Integer targetCore/*, Integer num*/) {
	if(this.targetCores == null) {
	    this.targetCores = new Hashtable<FlagState, Queue<Integer>>();
	}
	if(!this.targetCores.containsKey(fstate)) {
	    this.targetCores.put(fstate, new LinkedList<Integer>());
	}
	//if(!this.targetCores.get(fstate).contains(targetCore)) {
	    this.targetCores.get(fstate).add(targetCore); // there may have some duplicate items,
	                                                  // which reflects probabilities.
	//}
    }
    
    public void addTargetCore(FlagState fstate, Integer targetCore, FlagState tfstate) {
	if(this.targetCores == null) {
	    this.targetCores = new Hashtable<FlagState, Queue<Integer>>();
	}
	if(!this.targetCores.containsKey(fstate)) {
	    this.targetCores.put(fstate, new LinkedList<Integer>());
	}
	//if(!this.targetCores.get(fstate).contains(targetCore)) {
	    this.targetCores.get(fstate).add(targetCore);
	//}
	if(this.targetFState == null) {
	    this.targetFState = new Hashtable<FlagState, FlagState>();
	}
	//if(!this.targetFState.containsKey(fstate)) {
	    this.targetFState.put(fstate, tfstate);
	//}
    }
    
    public void addAllyCore(FlagState fstate, Integer targetCore/*, Integer num*/) {
	if(this.allyCores == null) {
	    this.allyCores = new Hashtable<FlagState, Vector<Integer>>();
	}
	if(!this.allyCores.containsKey(fstate)) {
	    this.allyCores.put(fstate, new Vector<Integer>());
	}
	if((this.coreNum != targetCore.intValue()) && (!this.allyCores.get(fstate).contains(targetCore))) {
	    this.allyCores.get(fstate).add(targetCore); // there may have some duplicate items,
	                                                  // which reflects probabilities.
	}
    }
    
    public void addFState4TD(TaskDescriptor td, FlagState fstate) {
	if(this.td2fs == null) {
	    this.td2fs = new Hashtable<TaskDescriptor, Vector<FlagState>>();
	}
	if(!this.td2fs.containsKey(td)) {
	    this.td2fs.put(td, new Vector<FlagState>());
	}
	if(!this.td2fs.get(td).contains(fstate)) {
	    this.td2fs.get(td).add(fstate);
	}
    }

    public Vector<TaskDescriptor> getTasks() {
        return tasks;
    }

    public void addTask(TaskDescriptor task) {
	if(this.tasks == null) {
	    this.tasks = new Vector<TaskDescriptor>();
	}
	if(!this.tasks.contains(task)) {
	    this.tasks.add(task);
	}
    }

    public Vector<Integer> getAncestorCores() {
        return ancestorCores;
    }

    public void setAncestorCores(Vector<Integer> ancestorCores) {
        this.ancestorCores = ancestorCores;
    }

    public void addAncestorCores(Integer ancestorCore) {
	if(this.ancestorCores == null) {
	    this.ancestorCores = new Vector<Integer>();
	}
	if((ancestorCore.intValue() != this.coreNum) && (!this.ancestorCores.contains(ancestorCore))) {
	    this.ancestorCores.addElement(ancestorCore);
	}
    }
    
    public int ancestorCoresNum() {
	if(this.ancestorCores == null) {
	    return 0;
	}
	return this.ancestorCores.size();
    }

    public Vector<Integer> getChildCores() {
        return childCores;
    }

    public void setChildCores(Vector<Integer> childCores) {
        this.childCores = childCores;
    }
    
    public void addChildCores(Integer childCore) {
	if(this.childCores == null) {
	    this.childCores = new Vector<Integer>();
	}
	if((childCore.intValue() != this.coreNum) && (!this.childCores.contains(childCore))) {
	    this.childCores.addElement(childCore);
	}
    }
    
    public int childCoresNum() {
	if(this.childCores == null) {
	    return 0;
	}
	return this.childCores.size();
    }
    
}