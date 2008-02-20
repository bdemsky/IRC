package Analysis.Scheduling;

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
    ExeResult currentRun;
    CoreSimulator cs;
    boolean finish;
    
    public class ExeResult {
	int finishTime;
	Vector<ObjectSimulator> newObjs;
	
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
	
    }
    
    public TaskSimulator(TaskDescriptor td, CoreSimulator cs) {
	super();
	this.td = td;
	this.paraQueues = null;
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

    public void enquePara(ObjectSimulator obj) {
	ClassDescriptor cd = obj.getCd();
	int paraNum = td.numParameters();
	for(int i = 0; i < paraNum; i++) {
	    VarDescriptor para = td.getParameter(i);
	    if(cd.equals(para.getType().getClassDesc())) {
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
			    (this.paraQueues.elementAt(i).contains(obj))){
			this.paraQueues.elementAt(i).remove(obj);
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
		    } else {
			if((this.paraQueues != null) &&
				(this.paraQueues.elementAt(i) != null)  && 
				(this.paraQueues.elementAt(i).contains(obj))){
			    this.paraQueues.elementAt(i).remove(obj);
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
	for(int i = 0; i < paraQueues.size(); i++) {
	    ObjectSimulator tpara = paraQueues.elementAt(i).peek();
	    // remove this object from the remaining parameter queues
	    for(int j = i + 1; j < paraQueues.size(); j++) {
		paraQueues.elementAt(j).remove(tpara);
	    }
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
	}
	finishTime /= paraQueues.size();
	this.currentRun.setFinishTime(finishTime);
    }
    
    public void updateFinishTime(int time) {
	this.currentRun.setFinishTime(this.currentRun.finishTime - time);
	finish = false;
    }
    
    public void finish() {
	this.finish = true;
    }
}