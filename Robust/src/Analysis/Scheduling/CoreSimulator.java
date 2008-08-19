package Analysis.Scheduling;

import java.util.Hashtable;
import java.util.Queue;
import java.util.Vector;

import Analysis.TaskStateAnalysis.FlagState;
import IR.TaskDescriptor;

public class CoreSimulator {
  Vector<TaskSimulator> tasks;
  RuntimeSchedule rSchedule;
  TaskSimulator rtask;
  Hashtable<FlagState, Queue<Integer>> targetCSimulator;
  Hashtable<FlagState, Vector<Integer>> allyCSimulator;
  Hashtable<FlagState, FlagState> targetFState;
  int coreNum;
  int activeTime;

  public CoreSimulator(RuntimeSchedule schedule, int coreNum) {
    super();
    reset();
    this.rSchedule = schedule;
    this.coreNum = coreNum;
  }

  public CoreSimulator(int coreNum) {
    super();
    reset();
    this.coreNum = coreNum;
  }

  public void reset() {
    this.activeTime = 0;
    this.tasks = null;
    this.targetCSimulator = null;
    this.targetFState = null;
    this.rSchedule = null;
    this.rtask = null;
  }

  public void deployTasks(Vector<TaskDescriptor> tasks) {
    if(tasks == null) {
      return;
    }

    if(this.tasks == null) {
      this.tasks = new Vector<TaskSimulator>();
    } else {
      this.tasks.clear();
    }

    for(int i = 0; i < tasks.size(); i++) {
      TaskDescriptor td = tasks.elementAt(i);
      this.tasks.add(new TaskSimulator(td, this));
    }
  }

  public Queue<Integer> getTargetCores(FlagState fstate) {
    if(targetCSimulator == null) {
      return null;
    }
    return targetCSimulator.get(fstate);
  }

  public void setTargetCSimulator(Hashtable<FlagState, Queue<Integer>> targetCSimulator) {
    this.targetCSimulator = targetCSimulator;
  }

  public Vector<Integer> getAllyCores(FlagState fstate) {
    if(allyCSimulator == null) {
      return null;
    }
    return allyCSimulator.get(fstate);
  }

  public void setAllyCSimulator(Hashtable<FlagState, Vector<Integer>> allyCSimulator) {
    this.allyCSimulator = allyCSimulator;
  }

  public FlagState getTargetFState(FlagState fstate) {
    if(targetFState == null) {
      return fstate;
    }
    return targetFState.get(fstate);
  }

  public void setTargetFState(Hashtable<FlagState, FlagState> targetFState) {
    this.targetFState = targetFState;
  }

  public int getActiveTime() {
    return activeTime;
  }

  public int getCoreNum() {
    return coreNum;
  }

  public Vector<TaskSimulator> getTasks() {
    return tasks;
  }

  public RuntimeSchedule getRSchedule() {
    return rSchedule;
  }

  public void setRSchedule(RuntimeSchedule schedule) {
    rSchedule = schedule;
  }

  public TaskSimulator getRtask() {
    return rtask;
  }

  public void addObject(ObjectSimulator newObj) {
    if(this.tasks == null) {
      return;
    }
    for(int i = 0; i < this.tasks.size(); i++) {
      this.tasks.elementAt(i).enquePara(newObj, null, 0, true);
    }
  }

  public void addObject(ObjectSimulator newObj, FlagState fs, int version) {
    if(this.tasks == null) {
      return;
    }
    for(int i = 0; i < this.tasks.size(); i++) {
      this.tasks.elementAt(i).enquePara(newObj, fs, version, false);
    }
  }

  public Vector<ObjectSimulator> finishTask() {
    assert(this.rtask != null);

    Vector<ObjectSimulator> transObjs = null;
    Vector<Queue<ObjectSimulator>> paraQueues = this.rtask.getParaQueues();
    for(int i = 0; i < paraQueues.size(); i++) {
      ObjectSimulator obj = paraQueues.elementAt(i).poll();
      obj.setHold(false);
      boolean remove = false;
      if((this.targetFState != null) && (this.targetFState.containsKey(obj.getCurrentFS()))) {
	if(transObjs == null) {
	  transObjs = new Vector<ObjectSimulator>();
	}
	if(!transObjs.contains(obj)) {
	  transObjs.add(obj);
	}
	remove = true;
      }
      // check if this object becoming shared or not
      Vector<Integer> allycores = this.getAllyCores(obj.getCurrentFS());
      if(allycores != null) {
	obj.setShared(true);
	for(int k = 0; k < allycores.size(); ++k) {
	  Integer allyCore = allycores.elementAt(k);
	  if(transObjs == null) {
	    transObjs = new Vector<ObjectSimulator>();
	  }
	  if(!transObjs.contains(obj)) {
	    transObjs.add(obj);
	  }
	  remove = false;
	}
      }
      for(int j = 0; j < this.tasks.size(); j++) {
	this.tasks.elementAt(j).refreshPara(obj, remove);
      }
    }
    this.activeTime += this.rtask.getCurrentRun().getFinishTime();
    this.rtask.finish();
    this.rtask = null;
    return transObjs;
  }

  public void updateTask(int time) {
    this.activeTime += time;
    this.rtask.updateFinishTime(time);
  }

  public TaskSimulator process() {
    TaskSimulator next = rSchedule.schedule(tasks);
    this.rtask = next;
    return next;
  }

}