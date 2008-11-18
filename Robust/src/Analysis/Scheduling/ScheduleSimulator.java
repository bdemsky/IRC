package Analysis.Scheduling;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;
import java.util.Map.Entry;

import Analysis.TaskStateAnalysis.FlagState;
import Analysis.TaskStateAnalysis.TaskAnalysis;
import IR.ClassDescriptor;
import IR.State;
import IR.TaskDescriptor;
import IR.TypeUtil;

public class ScheduleSimulator {
  private int coreNum;
  private Vector<Schedule> scheduling;
  private Vector<CoreSimulator> cores;
  private Vector<TaskSimulator> tasks;
  private Vector<CheckPoint> checkpoints;
  private int processTime;
  private int invoketime;

  State state;
  TaskAnalysis taskanalysis;

  public ScheduleSimulator(int coreNum, State state, TaskAnalysis taskanalysis) {
    super();
    this.coreNum = coreNum;
    this.scheduling = null;
    this.cores = null;
    this.tasks = null;
    this.checkpoints = null;
    this.processTime = 0;
    this.invoketime = 0;
    this.state = state;
    this.taskanalysis = taskanalysis;
  }

  public ScheduleSimulator(int coreNum, Vector<Schedule> scheduling, State state, TaskAnalysis taskanalysis) {
    super();
    this.coreNum = coreNum;
    this.scheduling = scheduling;
    this.cores = new Vector<CoreSimulator>(this.coreNum);
    for(int i = 0; i < this.coreNum; i++) {
      this.cores.add(new CoreSimulator(FIFORSchedule.getFIFORSchedule(), i));
    }
    this.tasks = new Vector<TaskSimulator>();
    this.checkpoints = null;
    this.processTime = 0;
    this.invoketime = 0;
    this.state = state;
    this.taskanalysis = taskanalysis;
    applyScheduling();
  }

  public Vector<CheckPoint> getCheckpoints() {
    return checkpoints;
  }

  public int getCoreNum() {
    return coreNum;
  }

  public void setCoreNum(int coreNum) {
    this.coreNum = coreNum;
    if(this.cores != null) {
      this.cores.clear();
    }
    this.cores = new Vector<CoreSimulator>(this.coreNum);
    for(int i = 0; i < this.coreNum; i++) {
      this.cores.add(new CoreSimulator(FIFORSchedule.getFIFORSchedule(), i));
    }
    if(this.scheduling != null) {
      applyScheduling();
    }
  }

  public int getUtility(int index) {
    return (this.cores.elementAt(index).getActiveTime() * 100) / this.processTime;
  }

  public Vector<Schedule> getScheduling() {
    return scheduling;
  }

  public void setScheduling(Vector<Schedule> scheduling) {
    this.scheduling = scheduling;
    if(this.tasks == null) {
      this.tasks = new Vector<TaskSimulator>();
    } else {
      this.tasks.clear();
    }
    if(this.cores != null) {
      for(int i = 0; i < this.coreNum; i++) {
	CoreSimulator core = this.cores.elementAt(i);
	core.reset();
	core.setRSchedule(FIFORSchedule.getFIFORSchedule());
      }
    } else {
      this.cores = new Vector<CoreSimulator>(this.coreNum);
      for(int i = 0; i < this.coreNum; i++) {
	this.cores.add(new CoreSimulator(FIFORSchedule.getFIFORSchedule(), i));
      }
    }
    if(this.checkpoints != null) {
	this.checkpoints.clear();
    }

    applyScheduling();
  }

  public void applyScheduling() {
    assert(this.state != null);

    for(int i = 0; i < this.scheduling.size(); i++) {
      Schedule temp = this.scheduling.elementAt(i);
      CoreSimulator cs = this.cores.elementAt(temp.getCoreNum());
      cs.deployTasks(temp.getTasks());
      cs.setTargetCSimulator(temp.getTargetCoreTable());
      cs.setAllyCSimulator(temp.getAllyCoreTable());
      cs.setTargetFState(temp.getTargetFStateTable());
    }
    // inject a Startup Object to each core
    for(int i = 0; i < this.coreNum; i++) {
      ClassDescriptor startupobject=(ClassDescriptor)state.getClassSymbolTable().get(TypeUtil.StartupClass);
      FlagState fsstartup = (FlagState)taskanalysis.getRootNodes(startupobject).elementAt(0);
      ObjectSimulator newObj = new ObjectSimulator(startupobject, fsstartup);
      this.cores.elementAt(i).addObject(newObj);
    }
  }

  public Vector<TaskSimulator> getTasks() {
    return tasks;
  }

  public int process() {
    assert(this.scheduling != null);

    this.invoketime++;

    if(this.checkpoints == null) {
      this.checkpoints = new Vector<CheckPoint>();
    } /*else {
      this.checkpoints.clear();
    }*/

    this.processTime = 0;

    // first decide next task to execute on each core
    int i = 0;
    for(i = 0; i < this.cores.size(); i++) {
      CoreSimulator cs = this.cores.elementAt(i);
      TaskSimulator task = cs.process();
      if(task != null) {
	this.tasks.add(task);
      }
    }

    // add STARTTASK checkpoint for all the initial tasks
    CheckPoint cp = new CheckPoint(this.processTime);
    for(i = 0; i < this.tasks.size(); i++) {
      TaskSimulator task = this.tasks.elementAt(i);
      Action action = new Action(task.getCs().getCoreNum(), Action.TASKSTART);
      action.setTd(task.getTd());
      cp.addAction(action);
    }
    this.checkpoints.add(cp);

    while(true) {
      // if no more tasks on each core, simulation finish
      if(this.tasks.size() == 0) {
	break;
      }

      // for each task in todo queue, decide the execution path of this time
      // according to statistic information
      //int index = 0;  // indicate the task to finish first
      int finishTime = Integer.MAX_VALUE;
      Vector<TaskSimulator> finishTasks = new Vector<TaskSimulator>();
      for(i = 0; i < this.tasks.size(); i++) {
	TaskSimulator task = this.tasks.elementAt(i);
	task.process();
	int tempTime = task.getCurrentRun().getFinishTime();
	if(tempTime < finishTime) {
	  finishTime = tempTime;
	  finishTasks.clear();
	  finishTasks.add(task);
	} else if (tempTime == finishTime) {
	  finishTasks.add(task);
	}
      }
      for(i = 0; i < this.tasks.size(); i++) {
	TaskSimulator task = this.tasks.elementAt(i);
	if(!finishTasks.contains(task)) {
	  task.getCs().updateTask(finishTime);
	}
      }
      this.processTime += finishTime;
      cp = new CheckPoint(this.processTime);
      Action action = null;
      for(i = 0; i < finishTasks.size(); i++) {
	TaskSimulator task = finishTasks.elementAt(i);
	this.tasks.removeElement(task);
	if(task instanceof TransTaskSimulator) {
	  TransTaskSimulator tmptask = (TransTaskSimulator)task;
	  // add ADDOBJ task to targetCore
	  int targetCoreNum = tmptask.getTargetCoreNum();
	  ObjectInfo objinfo = tmptask.refreshTask();
	  ObjectSimulator nobj = objinfo.obj;
	  FlagState fs = objinfo.fs;
	  int version = objinfo.version;
	  this.cores.elementAt(targetCoreNum).addObject(nobj, fs, version);
	  action = new Action(targetCoreNum, Action.ADDOBJ, 1, nobj.getCd());
	  cp.addAction(action);
	  if(!tmptask.isFinished()) {
	    // still have some objects to be transpotted
	    this.tasks.add(task);
	  }
	  if(this.cores.elementAt(targetCoreNum).getRtask() == null) {
	    TaskSimulator newTask = this.cores.elementAt(targetCoreNum).process();
	    if(newTask != null) {
	      this.tasks.add(newTask);
	      // add a TASKSTART action into this checkpoint
	      action = new Action(targetCoreNum, Action.TASKSTART);
	      action.setTd(newTask.getTd());
	      cp.addAction(action);
	    }
	  }
	} else {
	  CoreSimulator cs = task.getCs();
	  int coreNum = cs.getCoreNum();
	  if(task.getCurrentRun().getExetype() == 0) {
	    Hashtable<Integer, Queue<ObjectInfo>> transObjQueues = new Hashtable<Integer, Queue<ObjectInfo>>();
	    if(task.getCurrentRun().getNewObjs() == null) {
	      action = new Action(coreNum, Action.TASKFINISH);
	      action.setTd(cs.getRtask().getTd());
	    } else {
	      action = new Action(coreNum, Action.TFWITHOBJ);
	      action.setTd(cs.getRtask().getTd());
	      Vector<ObjectSimulator> nobjs = task.getCurrentRun().getNewObjs();
	      for(int j = 0; j < nobjs.size(); j++) {
		ObjectSimulator nobj = nobjs.elementAt(j);
		action.addNewObj(nobj.getCd(), Integer.valueOf(1));
		// send the new object to target core according to pre-decide scheduling
		Queue<Integer> cores = cs.getTargetCores(nobj.getCurrentFS());
		if(cores == null) {
		  // this obj will reside on this core
		  cs.addObject(nobj);
		} else {
		  Integer targetCore = cores.poll();
		  if(targetCore == coreNum) {
		    // this obj will reside on this core
		    cs.addObject(nobj);
		  } else {
		    if(!transObjQueues.containsKey(targetCore)) {
		      transObjQueues.put(targetCore, new LinkedList<ObjectInfo>());
		    }
		    Queue<ObjectInfo> tmpqueue = transObjQueues.get(targetCore);
		    tmpqueue.add(new ObjectInfo(nobj));
		    tmpqueue = null;
		  }
		  // enqueue this core again
		  cores.add(targetCore);
		}
		cores = null;
		// check if this object becoming shared or not
		Vector<Integer> allycores = cs.getAllyCores(nobj.getCurrentFS());
		if(allycores != null) {
		  nobj.setShared(true);
		  for(int k = 0; k < allycores.size(); ++k) {
		    Integer allyCore = allycores.elementAt(k);
		    if(allyCore == coreNum) {
		      cs.addObject(nobj);
		    } else {
		      if(!transObjQueues.containsKey(allyCore)) {
			transObjQueues.put(allyCore, new LinkedList<ObjectInfo>());
		      }
		      Queue<ObjectInfo> tmpqueue = transObjQueues.get(allyCore);
		      ObjectInfo nobjinfo = new ObjectInfo(nobj);
		      if(!tmpqueue.contains(nobjinfo)) {
			tmpqueue.add(nobjinfo);
		      }
		      tmpqueue = null;
		    }
		  }
		  allycores = null;
		}
	      }
	      nobjs = null;
	    }
	    cp.addAction(action);
	    Vector<ObjectSimulator> transObjs = cs.finishTask();
	    if(transObjs != null) {
	      for(int j = 0; j < transObjs.size(); j++) {
		ObjectSimulator tobj = transObjs.elementAt(j);
		// send the object to target core according to pre-decide scheduling
		Queue<Integer> cores = cs.getTargetCores(tobj.getCurrentFS());
		tobj.setCurrentFS(cs.getTargetFState(tobj.getCurrentFS()));
		if(cores == null) {
		  // this obj will reside on this core
		  cs.addObject(tobj);
		} else {
		  Integer targetCore = cores.poll();
		  if(targetCore == coreNum) {
		    // this obj will reside on this core
		    cs.addObject(tobj);
		  } else {
		    if(!transObjQueues.containsKey(targetCore)) {
		      transObjQueues.put(targetCore, new LinkedList<ObjectInfo>());
		    }
		    Queue<ObjectInfo> tmpqueue = transObjQueues.get(targetCore);
		    tmpqueue.add(new ObjectInfo(tobj));
		    tmpqueue = null;
		  }
		  cores.add(targetCore);
		}
		cores = null;
		// check if this object becoming shared or not
		Vector<Integer> allycores = cs.getAllyCores(tobj.getCurrentFS());
		if(allycores != null) {
		  tobj.setShared(true);
		  for(int k = 0; k < allycores.size(); ++k) {
		    Integer allyCore = allycores.elementAt(k);
		    if(allyCore == coreNum) {
		      cs.addObject(tobj);
		    } else {
		      if(!transObjQueues.containsKey(allyCore)) {
			transObjQueues.put(allyCore, new LinkedList<ObjectInfo>());
		      }
		      Queue<ObjectInfo> tmpqueue = transObjQueues.get(allyCore);
		      ObjectInfo nobjinfo = new ObjectInfo(tobj);
		      if(!tmpqueue.contains(nobjinfo)) {
			tmpqueue.add(nobjinfo);
		      }
		      tmpqueue = null;
		    }
		  }
		  allycores = null;
		}
	      }
	      transObjs = null;
	    }
	    // add 'transport' tasks
	    Iterator it_entries = transObjQueues.entrySet().iterator();
	    while(it_entries.hasNext()) {
	      Entry<Integer, Queue<ObjectInfo>> tmpentry = (Entry<Integer, Queue<ObjectInfo>>)it_entries.next();
	      Integer tmpCoreNum = tmpentry.getKey();
	      Queue<ObjectInfo> nobjs = tmpentry.getValue();
	      TransTaskSimulator tmptask = new TransTaskSimulator(cs, tmpCoreNum, nobjs);
	      this.tasks.add(tmptask);
	      tmpentry = null;
	      nobjs = null;
	    }
	    transObjQueues = null;
	  } else if (task.getCurrentRun().getExetype() == 1) {
	    action = new Action(coreNum, Action.TASKABORT);
	    action.setTd(cs.getRtask().getTd());
	    cp.addAction(action);
	    Vector<ObjectSimulator> transObjs = cs.finishTask();
	  } else if (task.getCurrentRun().getExetype() == 2) {
	    action = new Action(coreNum, Action.TASKREMOVE);
	    action.setTd(cs.getRtask().getTd());
	    cp.addAction(action);
	    Vector<ObjectSimulator> transObjs = cs.finishTask();
	  }
	  // Choose a new task for this core
	  TaskSimulator newTask = cs.process();
	  if(newTask != null) {
	    this.tasks.add(newTask);
	    // add a TASKSTART action into this checkpoint
	    action = new Action(coreNum, Action.TASKSTART);
	    action.setTd(cs.getRtask().getTd());
	    cp.addAction(action);
	  }
	}
      }
      this.checkpoints.add(cp);
      finishTasks = null;
    }

    if(this.state.PRINTSCHEDULESIM) {
	SchedulingUtil.printSimulationResult("SimulatorResult_" + this.invoketime + ".dot", this.processTime,
		this.coreNum, this.checkpoints);
    }
    System.out.println("Simulate scheduling #" + this.invoketime + ": ");
    System.out.println("\tTotal execution time is: " + this.processTime);
    System.out.println("\tUtility of cores: ");
    for(int j = 0; j < this.cores.size(); j++) {
      System.out.println("\t\tcore" + j + ": " + getUtility(j) + "%");
    }
    
    this.checkpoints.clear();
    this.checkpoints = null;
    return this.processTime;
  }

  public class CheckPoint {
    private int timepoint;
    private Vector<Action> actions;

    public CheckPoint(int timepoint) {
      super();
      this.timepoint = timepoint;
      this.actions = new Vector<Action>();
    }

    public Vector<Action> getActions() {
      return actions;
    }

    public void addAction(Action action) {
      this.actions.add(action);
    }

    public int getTimepoint() {
      return timepoint;
    }
  }

  public class Action {
    public static final int ADDOBJ = 0;
    public static final int TASKFINISH = 1;
    public static final int TFWITHOBJ = 2;
    public static final int TASKSTART = 3;
    public static final int TASKABORT = 4;
    public static final int TASKREMOVE = 5;

    private int coreNum;
    private int type;
    private TaskDescriptor td;
    private Hashtable<ClassDescriptor, Integer> nObjs;
    private int nObjNum;
    private ClassDescriptor transObj;

    public Action(int coreNum, int type) {
      super();
      this.coreNum = coreNum;
      this.type = type;
      this.td = null;
      if(this.type == TFWITHOBJ) {
	this.nObjs = new Hashtable<ClassDescriptor, Integer>();
      } else {
	this.nObjs = null;
      }
      this.nObjNum = -1;
      this.transObj = null;
    }

    public Action(int coreNum, int type, int objNum, ClassDescriptor transObj) {
      super();
      assert(type == ADDOBJ);
      this.coreNum = coreNum;
      this.type = type;
      this.td = null;
      this.nObjNum = objNum;
      this.transObj = transObj;
    }

    public void addNewObj(ClassDescriptor cd, Integer num) {
      assert(this.type == TFWITHOBJ);

      if(this.nObjs.containsKey(cd)) {
	Integer sum = this.nObjs.get(cd) + num;
	this.nObjs.put(cd, sum);
      } else {
	this.nObjs.put(cd, num);
      }
    }

    public int getCoreNum() {
      return coreNum;
    }

    public int getType() {
      return type;
    }

    public int getNObjNum() {
      return nObjNum;
    }

    public ClassDescriptor getTransObj() {
      return transObj;
    }

    public TaskDescriptor getTd() {
      return td;
    }

    public void setTd(TaskDescriptor td) {
      this.td = td;
    }

    public Hashtable<ClassDescriptor, Integer> getNObjs() {
      return nObjs;
    }
  }

}