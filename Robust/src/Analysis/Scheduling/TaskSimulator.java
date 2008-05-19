package Analysis.Scheduling;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;

import Analysis.TaskStateAnalysis.FEdge;
import Analysis.TaskStateAnalysis.FlagState;
import Analysis.TaskStateAnalysis.FEdge.NewObjInfo;
import IR.ClassDescriptor;
import IR.TaskDescriptor;
import IR.VarDescriptor;
import IR.Tree.FlagExpressionNode;

public class TaskSimulator {
    TaskDescriptor td;
    Vector<Queue<ObjectSimulator>> paraQueues;
    Hashtable<ObjectSimulator, Integer> objVersionTbl;
    ExeResult currentRun;
    CoreSimulator cs;
    boolean finish;
    
    public class ExeResult {
	int finishTime;
	Vector<ObjectSimulator> newObjs;
	int exetype; // 0--normal executing
	             // 1--abort due to fail on grabbing locks
	             // 2--out of date task
	
	public ExeResult() {
	    finishTime = 0;
	    newObjs = null;
	}

	public int getFinishTime() {
	    return finishTime;
	}

	public void setFinishTime(int finishTime) {
	    this.finishTime = finishTime;
	}

	public Vector<ObjectSimulator> getNewObjs() {
	    return newObjs;
	}

	public void addNewObj(ObjectSimulator newObj) {
	    if(this.newObjs == null) {
		this.newObjs = new Vector<ObjectSimulator>();
	    }
	    
	    this.newObjs.add(newObj);
	}

	public int getExetype() {
	    return exetype;
	}

	public void setExetype(int exetype) {
	    this.exetype = exetype;
	}
    }
    
    public TaskSimulator(TaskDescriptor td, CoreSimulator cs) {
	super();
	this.td = td;
	this.paraQueues = null;
	this.objVersionTbl = null;
	this.currentRun = null;
	this.cs = cs;
	this.finish = true;
    }
    
    public CoreSimulator getCs() {
        return cs;
    }

    public TaskDescriptor getTd() {
        return td;
    }

    public ExeResult getCurrentRun() {
        return currentRun;
    }

    public Vector<Queue<ObjectSimulator>> getParaQueues() {
        return paraQueues;
    }
    
    public Hashtable<ObjectSimulator, Integer> getObjVersionTbl() {
        return objVersionTbl;
    }
    
    public int getObjVersion(ObjectSimulator os) {
	return this.objVersionTbl.get(os).intValue();
    }

    public void enquePara(ObjectSimulator obj, FlagState fs, int version, boolean inherent) {
	ClassDescriptor cd = obj.getCd();
	int paraNum = td.numParameters();
	for(int i = 0; i < paraNum; i++) {
	    VarDescriptor para = td.getParameter(i);
	    if(cd.equals(para.getType().getClassDesc())) {
		// check if the status is right
		FlagExpressionNode fen = td.getFlag(para);
		FlagState cfs = fs;
		if(inherent) {
		    cfs = obj.getCurrentFS();
		}
		if(SchedulingUtil.isTaskTrigger_flag(fen, cfs)) {
		    if(this.paraQueues == null) {
			this.paraQueues = new Vector<Queue<ObjectSimulator>>();
			for(int j = 0; j < paraNum; j++) {
			    this.paraQueues.add(null);
			}
		    }
		    if(this.paraQueues.elementAt(i) == null) {
			this.paraQueues.setElementAt(new LinkedList<ObjectSimulator>(), i);
		    }
		    if(this.objVersionTbl == null) {
			this.objVersionTbl = new Hashtable<ObjectSimulator, Integer>();
		    }
		    if(!this.paraQueues.elementAt(i).contains(obj)) {
			this.paraQueues.elementAt(i).add(obj);
			if(inherent) {
			    this.objVersionTbl.put(obj, obj.getVersion());
			} else {
			    this.objVersionTbl.put(obj, version);
			}
		    }
		}
	    }
	}
    }
    
    public void refreshPara(ObjectSimulator obj, boolean remove) {
	ClassDescriptor cd = obj.getCd();
	int paraNum = td.numParameters();
	for(int i = 0; i < paraNum; i++) {
	    VarDescriptor para = td.getParameter(i);
	    if(cd.equals(para.getType().getClassDesc())) {
		if(remove) {
		    if((this.paraQueues != null) &&
			    (this.paraQueues.elementAt(i) != null)  && 
			    (this.paraQueues.elementAt(i).contains(obj))) {
			this.paraQueues.elementAt(i).remove(obj);
			this.objVersionTbl.remove(obj);
		    }
		} else {
		    // check if the status is right
		    FlagExpressionNode fen = td.getFlag(para);
		    if(SchedulingUtil.isTaskTrigger_flag(fen, obj.getCurrentFS())) {
			if(this.paraQueues == null) {
			    this.paraQueues = new Vector<Queue<ObjectSimulator>>();
			    for(int j = 0; j < paraNum; j++) {
				this.paraQueues.add(null);
			    }
			}
			if(this.paraQueues.elementAt(i) == null) {
			    this.paraQueues.setElementAt(new LinkedList<ObjectSimulator>(), i);
			}
			this.paraQueues.elementAt(i).add(obj);
			if(this.objVersionTbl == null) {
			    this.objVersionTbl = new Hashtable<ObjectSimulator, Integer>();
			}
			this.objVersionTbl.put(obj, obj.getVersion());
		    } else {
			if((this.paraQueues != null) &&
				(this.paraQueues.elementAt(i) != null)  && 
				(this.paraQueues.elementAt(i).contains(obj))){
			    this.paraQueues.elementAt(i).remove(obj);
			    this.objVersionTbl.remove(obj);
			}
		    }
		}
	    }
	}
    }

    public void process() {
	if(!finish) {
	    return;
	} else {
	    finish = false;
	}
	
	if(this.currentRun == null) {
	    this.currentRun = new ExeResult();
	}
	
	int finishTime = 0;
	// According to runtime statistic information, decide the execution path of this task this time.
	// Mainly following things:
	//    1.the result, i.e. the result FlagState reached by each parameter.
	//    2.the finish time
	//    3.any new objects
	
	// First check if all the parameters are still available.
	// For shared objects, need to first grab the lock and also check if the version is right
	for(int i = 0; i < paraQueues.size(); i++) {
	    ObjectSimulator tpara = paraQueues.elementAt(i).peek();
	    if(tpara.isShared()) {
		if(tpara.isHold()) {
		    // shared object held by other tasks
		    finishTime = 1; // TODO currenly assume the effort on requesting locks are only 1
		    this.currentRun.setFinishTime(finishTime);
		    this.currentRun.setExetype(1);
		    paraQueues.elementAt(i).poll();
		    paraQueues.elementAt(i).add(tpara);
		    for(int j = 0; j < i; ++j) {
			tpara = this.paraQueues.elementAt(j).poll();
			if(tpara.isShared() && tpara.isHold()) {
			    tpara.setHold(false);
			}
			this.paraQueues.elementAt(j).add(tpara);
		    }
		    return;
		} else if (tpara.getVersion() != this.objVersionTbl.get(tpara)) {
		    // shared object has been updated and no longer fitted to this task
		    finishTime = 1; // TODO currenly assume the effort on requesting locks are only 1
		    this.currentRun.setFinishTime(finishTime);
		    this.currentRun.setExetype(2);
		    paraQueues.elementAt(i).poll();
		    // remove this object from the remaining parameter queues
		    for(int j = i + 1; j < paraQueues.size(); j++) {
			paraQueues.elementAt(j).remove(tpara);
		    }
		    for(int j = 0; j < i; ++j) {
			tpara = this.paraQueues.elementAt(j).poll();
			if(tpara.isShared() && tpara.isHold()) {
			    tpara.setHold(false);
			}
			this.paraQueues.elementAt(j).add(tpara);
		    }
		    return;
		} else {
		    tpara.setHold(true);
		}
	    }
	    // remove this object from the remaining parameter queues
	    for(int j = i + 1; j < paraQueues.size(); j++) {
		paraQueues.elementAt(j).remove(tpara);
	    }
	}
	for(int i = 0; i < paraQueues.size(); i++) {
	    ObjectSimulator tpara = paraQueues.elementAt(i).peek();
	    
	    FlagState tfstate = tpara.getCurrentFS();
	    FEdge toexecute = tfstate.process(td);
	    finishTime += toexecute.getExeTime();
	    if((toexecute.getNewObjInfoHashtable() != null) && (toexecute.getNewObjInfoHashtable().size() > 0)) {
		// have new objects
		Iterator it = toexecute.getNewObjInfoHashtable().keySet().iterator();
		int invokeNum = toexecute.getInvokeNum();
		while(it.hasNext()) {
		    ClassDescriptor cd = (ClassDescriptor)it.next();
		    NewObjInfo noi = toexecute.getNewObjInfo(cd);
		    if(noi.getInvokeNum() < ((int)Math.round(((noi.getProbability() / 100) * noi.getNewRate() * invokeNum)))) {
			for(int j = 0; j < noi.getNewRate(); j++) { 
			    ObjectSimulator tmpObj = new ObjectSimulator(cd, noi.getRoot());
			    this.currentRun.addNewObj(tmpObj);
			    noi.incInvokeNum();
			}
		    }
		}
	    }
	    //FlagState tFState = (FlagState)toexecute.getTarget();
	    //tpara.setCurrentFS(tFState);
	    tpara.applyEdge(toexecute);
	    tpara.increaseVersion();
	}
	finishTime /= paraQueues.size();
	this.currentRun.setFinishTime(finishTime);
	this.currentRun.setExetype(0);
    }
    
    public void updateFinishTime(int time) {
	this.currentRun.setFinishTime(this.currentRun.finishTime - time);
	finish = false;
    }
    
    public void finish() {
	this.finish = true;
    }
}