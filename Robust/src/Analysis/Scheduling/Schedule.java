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
    
    public Schedule(int coreNum) {
	super();
	this.coreNum = coreNum;
	this.tasks = null;
	this.targetCores = null;
	this.targetFState = null;
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

    public void addTargetCore(FlagState fstate, Integer targetCore/*, Integer num*/) {
	if(this.targetCores == null) {
	    this.targetCores = new Hashtable<FlagState, Queue<Integer>>();
	}
	if(!this.targetCores.containsKey(fstate)) {
	    this.targetCores.put(fstate, new LinkedList<Integer>());
	}
	this.targetCores.get(fstate).add(targetCore);
    }
    
    public void addTargetCore(FlagState fstate, Integer targetCore, FlagState tfstate) {
	if(this.targetCores == null) {
	    this.targetCores = new Hashtable<FlagState, Queue<Integer>>();
	}
	if(!this.targetCores.containsKey(fstate)) {
	    this.targetCores.put(fstate, new LinkedList<Integer>());
	}
	this.targetCores.get(fstate).add(targetCore);
	if(this.targetFState == null) {
	    this.targetFState = new Hashtable<FlagState, FlagState>();
	}
	this.targetFState.put(fstate, tfstate);
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
}