package Analysis.Scheduling;

import java.io.FileOutputStream;
import java.io.PrintStream;
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
  private int processTime;
  private int invoketime;

  State state;
  TaskAnalysis taskanalysis;

  public ScheduleSimulator(int corenum, 
	                   State state, 
	                   TaskAnalysis taskanalysis) {
    this.coreNum = corenum;
    this.scheduling = null;
    this.cores = null;
    this.tasks = null;
    this.processTime = 0;
    this.invoketime = 0;
    this.state = state;
    this.taskanalysis = taskanalysis;
  }

  public ScheduleSimulator(int corenum, 
	                   Vector<Schedule> scheduling, 
	                   State state, 
	                   TaskAnalysis taskanalysis) {
    super();
    this.coreNum = corenum;
    this.scheduling = scheduling;
    this.cores = new Vector<CoreSimulator>(this.coreNum);
    for(int i = 0; i < this.coreNum; i++) {
      this.cores.add(new CoreSimulator(FIFORSchedule.getFIFORSchedule(), i));
    }
    this.tasks = new Vector<TaskSimulator>();
    this.processTime = 0;
    this.invoketime = 0;
    this.state = state;
    this.taskanalysis = taskanalysis;
    applyScheduling();
  }
  
  public int simulate(Vector<Vector<Schedule>> schedulings,
	              Vector<Integer> selectedScheduling,
	              Vector<Vector<SimExecutionEdge>> selectedSimExeGraphs) {      
      int processTime = Integer.MAX_VALUE;
      /*if(schedulings.size() > 1500) {
	  int index = 0;
	  int upperbound = schedulings.size();
	  long seed = 0;
	  java.util.Random r = new java.util.Random(seed);
	  for(int ii = 0; ii < 1500; ii++) {
	      index = (int)((Math.abs((double)r.nextInt() 
		           /(double)Integer.MAX_VALUE)) * upperbound);
	      System.out.println("Scheduling index:" + index);
	      Vector<Schedule> scheduling = schedulings.elementAt(index);
	      this.setScheduling(scheduling);
	      Vector<SimExecutionEdge> simexegraph = new Vector<SimExecutionEdge>();
	      Vector<CheckPoint> checkpoints = new Vector<CheckPoint>();
	      int tmpTime = this.process(checkpoints, simexegraph);
	      if(tmpTime < processTime) {
		  selectedScheduling.clear();
		  selectedScheduling.add(index);
		  selectedSimExeGraphs.clear();
		  selectedSimExeGraphs.add(simexegraph);
		  processTime = tmpTime;
	      } else if(tmpTime == processTime) {
		  selectedScheduling.add(index);
		  selectedSimExeGraphs.add(simexegraph);
	      }
	      scheduling = null;
	      checkpoints = null;
	      simexegraph = null;
	  }
      } else {*/
	  Iterator it_scheduling = schedulings.iterator();
	  int index = 0;
	  while(it_scheduling.hasNext()) {
	      Vector<Schedule> scheduling = 
		  (Vector<Schedule>)it_scheduling.next();
	      System.out.println("Scheduling index:" + scheduling.elementAt(0).getGid());
	      this.setScheduling(scheduling);
	      Vector<SimExecutionEdge> simexegraph = new Vector<SimExecutionEdge>();
	      Vector<CheckPoint> checkpoints = new Vector<CheckPoint>();
	      int tmpTime = this.process(checkpoints, simexegraph);
	      if(tmpTime < processTime) {
		  selectedScheduling.clear();
		  selectedScheduling.add(index);
		  selectedSimExeGraphs.clear();
		  selectedSimExeGraphs.add(simexegraph);
		  processTime = tmpTime;
	      } else if(tmpTime == processTime) {
		  selectedScheduling.add(index);
		  selectedSimExeGraphs.add(simexegraph);
	      }
	      scheduling = null;
	      checkpoints = null;
	      index++;
	  }
	  it_scheduling = null;
      //}
      
      System.out.print("Selected schedulings with least exectution time " + processTime + ": \n\t");
      for(int i = 0; i < selectedScheduling.size(); i++) {
	  int gid = schedulings.elementAt(selectedScheduling.elementAt(i)).elementAt(0).getGid();
	  System.out.print(gid + ", ");
      }
      System.out.println();
      
      return processTime;
  }

  public int getCoreNum() {
    return this.coreNum;
  }

  public void setCoreNum(int corenum) {
    this.coreNum = corenum;
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

  public int process(Vector<CheckPoint> checkpoints,
	             Vector<SimExecutionEdge> simexegraph) {
    assert(this.scheduling != null);

    this.invoketime++;
    this.processTime = 0;
    
    // helper structures for building SimExecutionGraph
    Hashtable<SimExecutionNode, Action> senode2action = 
	    new Hashtable<SimExecutionNode, Action>();
    SimExecutionNode[] lastseNodes = new SimExecutionNode[this.cores.size()];
    Hashtable<Action, Integer> action2exetime = 
	    new Hashtable<Action, Integer>();
    Hashtable<TransTaskSimulator, SimExecutionNode> tttask2senode = 
	    new Hashtable<TransTaskSimulator, SimExecutionNode>();
    Hashtable<Integer, Integer> obj2transtime = 
	    new Hashtable<Integer, Integer>();
    Hashtable<Integer, SimExecutionEdge> obj2lastseedge = 
	    new Hashtable<Integer, SimExecutionEdge>();

    // first decide next task to execute on each core
    int i = 0;
    for(i = 0; i < this.cores.size(); i++) {
      CoreSimulator cs = this.cores.elementAt(i);
      TaskSimulator task = cs.process();
      if(task != null) {
	this.tasks.add(task);
      }
      lastseNodes[i] = null;
    }

    // add STARTTASK checkpoint for all the initial tasks
    CheckPoint cp = new CheckPoint(this.processTime,
	                           this.coreNum);
    for(i = 0; i < this.tasks.size(); i++) {
      TaskSimulator task = this.tasks.elementAt(i);
      int coreid = task.getCs().getCoreNum();
      Action action = new Action(coreid, 
	                         Action.TASKSTART,
	                         task);
      cp.addAction(action);
      if(!(task instanceof TransTaskSimulator)) {
	  cp.removeSpareCore(coreid);
	  SimExecutionNode seNode = new SimExecutionNode(coreid, this.processTime);
	  seNode.setSpareCores(cp.getSpareCores());
	  senode2action.put(seNode, action);
	  action2exetime.put(action, -1);
	  lastseNodes[coreid] = seNode;
      }
    }
    checkpoints.add(cp);

    while(true) {
      // if no more tasks on each core, simulation finish
      if(this.tasks.size() == 0) {
	break;
      }

      // for each task in todo queue, decide the execution path of this time
      // according to statistic information
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
      
      // advance to next finish point
      this.processTime += finishTime;
      cp = new CheckPoint(this.processTime,
	                  this.coreNum);
      for(i = 0; i < this.tasks.size(); i++) {
	TaskSimulator task = this.tasks.elementAt(i);
	if(!finishTasks.contains(task)) {
	  task.getCs().updateTask(finishTime);
	  if(!(task instanceof TransTaskSimulator)) {
	      cp.removeSpareCore(task.getCs().getCoreNum());
	  }
	}
      }
      
      Action action = null;
      for(i = 0; i < finishTasks.size(); i++) {
	TaskSimulator task = finishTasks.elementAt(i);
	this.tasks.removeElement(task);
	if(task instanceof TransTaskSimulator) {
	    // handle TransTaskSimulator task's completion
	    finishTransTaskSimulator(task,
                                     cp,
                                     simexegraph,
                                     senode2action,
                                     lastseNodes,
                                     action2exetime,
                                     tttask2senode,
                                     obj2transtime);
	} else {
	  CoreSimulator cs = task.getCs();
	  Vector<TransTaskSimulator> tttasks = new Vector<TransTaskSimulator>();
	  
	  Vector<ObjectSimulator> transObjs = null;
	  if(task.getCurrentRun().getExetype() == 0) {
	      // normal execution of a task
	      transObjs = finishTaskNormal(task,
		                           cp,
		                           tttasks,
		                           senode2action,
		                           lastseNodes,
		                           action2exetime);
	  } else if (task.getCurrentRun().getExetype() == 1) {
	      // task abort
	      finishTaskAbnormal(cs,
	                         cp,
	                         senode2action,
	                         lastseNodes,
	                         action2exetime,
	                         Action.TASKABORT);
	  } else if (task.getCurrentRun().getExetype() == 2) {
	      // task remove
	      finishTaskAbnormal(cs,
		                 cp,
		                 senode2action,
		                 lastseNodes,
		                 action2exetime,
		                 Action.TASKREMOVE);
	  }
	  
	  // Choose a new task for this core
	  generateNewTask(cs,
		          cp,
		          transObjs,
		          tttasks,
		          simexegraph,
		          senode2action,
		          lastseNodes,
		          action2exetime,
		          tttask2senode,
		          obj2transtime,
		          obj2lastseedge);
	  tttasks.clear();
	  tttasks = null;
	  transObjs = null;
	}// end of if(task instanceof TransTaskSimulator) else
      }
      checkpoints.add(cp);
      finishTasks = null;
    } // end of while(true)
      
    // add the end node into the SimExecutionGraph
    SimExecutionNode seNode = new SimExecutionNode(this.coreNum, this.processTime);
    for(int j = 0; j < lastseNodes.length; j++) {
	SimExecutionNode lastsenode = lastseNodes[j];
	// create edges between previous senode on this core to this node
	if(lastsenode != null) {
	    Action tmpaction = senode2action.get(lastsenode);
	    int weight = tmpaction != null? action2exetime.get(tmpaction) : 0;  // TODO ????
	    SimExecutionEdge seEdge = new SimExecutionEdge(seNode,
		                                           lastsenode.getCoreNum(),
		                                           tmpaction != null? tmpaction.getTd():null, 
		                                           weight,
		                                           tmpaction != null? tmpaction.getTaskParams():null);
	    lastsenode.addEdge(seEdge);
	    
	    // setup data dependencies for the task
	    Vector<Integer> taskparams = seEdge.getTaskparams();
	    if(taskparams != null) {
		for(int k = 0; k < taskparams.size(); k++) {
		    Integer tparam = taskparams.elementAt(k);
		    SimExecutionEdge lastedge = obj2lastseedge.get(tparam);
		    if(lastedge != null) {
			if(lastedge.getCoreNum() != seEdge.getCoreNum()) {
			    // the obj is transferred from another core
			    // create an seEdge for this transfer
			    int transweight = obj2transtime.get(tparam);
			    SimExecutionEdge transseEdge = new SimExecutionEdge((SimExecutionNode)seEdge.getSource(),
				                                                lastedge.getCoreNum(),
				                                                null, // TODO: not sure if this is enough
				                                                transweight,
				                                                null);
			    if(((SimExecutionNode)seEdge.getSource()).getTimepoint() < 
				    ((SimExecutionNode)lastedge.getTarget()).getTimepoint()) {
				System.err.println("ScheduleSimulator:393");
				System.exit(-1);
			    }
			    lastedge.getTarget().addEdge(transseEdge);
			    simexegraph.add(transseEdge);
			    transseEdge.addPredicate(lastedge);
			    seEdge.addPredicate(transseEdge);
			} else {
			    seEdge.addPredicate(lastedge);
			}
		    }
		    // update the last edge associated to the parameter obj
		    obj2lastseedge.put(tparam, seEdge);
		}
	    }
	    taskparams = null;
	    simexegraph.add(seEdge); // add the seEdge afger all corresponding transfer edges
	}	  
	lastseNodes[j] = null;
    }

    senode2action.clear();
    senode2action = null;
    lastseNodes = null;
    action2exetime.clear();
    action2exetime = null;
    tttask2senode.clear();
    tttask2senode = null;
    obj2transtime.clear();
    obj2transtime = null;
    obj2lastseedge.clear();
    obj2lastseedge = null;

    int gid = this.scheduling.elementAt(0).getGid();
    if(this.state.PRINTSCHEDULESIM) {
	SchedulingUtil.printSimulationResult("SimulatorResult_" + gid + ".dot", 
		                             this.processTime,
		                             this.coreNum, 
		                             checkpoints);
    }
    System.out.println("Simulate scheduling #" + gid + ": ");
    System.out.println("\tTotal execution time is: " + this.processTime);
    System.out.println("\tUtility of cores: ");
    for(int j = 0; j < this.cores.size(); j++) {
      System.out.println("\t\tcore" + j + ": " + getUtility(j) + "%");
    }
    
    return this.processTime;
  }
  
  private void finishTransTaskSimulator(TaskSimulator task,
	                                CheckPoint cp,
	                                Vector<SimExecutionEdge> simexegraph,
	                                Hashtable<SimExecutionNode, Action> senode2action,
	                                SimExecutionNode[] lastseNodes,
	                                Hashtable<Action, Integer> action2exetime,
	                                Hashtable<TransTaskSimulator, SimExecutionNode> tttask2senode,
	                                Hashtable<Integer, Integer> obj2transtime) {
      TransTaskSimulator tmptask = (TransTaskSimulator)task;
      // add ADDOBJ task to targetCore
      int targetCoreNum = tmptask.getTargetCoreNum();
      ObjectInfo objinfo = tmptask.refreshTask();
      ObjectSimulator nobj = objinfo.obj;
      FlagState fs = objinfo.fs;
      int version = objinfo.version;
      this.cores.elementAt(targetCoreNum).addObject(nobj, fs, version);
      Action action = new Action(targetCoreNum, Action.ADDOBJ, 1, nobj.getCd());
      cp.addAction(action);

      // get the obj transfer time and associated senode
      SimExecutionNode senode = tttask2senode.get(tmptask);
      obj2transtime.put(nobj.getOid(), this.processTime - senode.getTimepoint());

      if(!tmptask.isFinished()) {
	  // still have some objects to be transferred
	  this.tasks.add(task);
      }
      if(this.cores.elementAt(targetCoreNum).getRtask() == null) {
	  TaskSimulator newTask = this.cores.elementAt(targetCoreNum).process();
	  if(newTask != null) {
	      this.tasks.add(newTask);
	      // add a TASKSTART action into this checkpoint
	      action = new Action(targetCoreNum, 
		                  Action.TASKSTART,
		                  newTask);
	      cp.addAction(action);
	      if(!(newTask instanceof TransTaskSimulator)) {
		  cp.removeSpareCore(targetCoreNum);
		  SimExecutionNode seNode = new SimExecutionNode(targetCoreNum, this.processTime);
		  seNode.setSpareCores(cp.getSpareCores());
		  senode2action.put(seNode, action);
		  action2exetime.put(action, -1);

		  SimExecutionNode lastsenode = lastseNodes[targetCoreNum];
		  // create edges between previous senode on this core to this node
		  if(lastsenode != null) {
		      Action tmpaction = senode2action.get(lastsenode);
		      SimExecutionEdge seEdge = null;
		      if(tmpaction == null) {
			  seEdge = new SimExecutionEdge(seNode,
				                        lastsenode.getCoreNum(),
				                        null,
				                        0,
				                        null);
		      } else {
			  int weight =  action2exetime.get(tmpaction);
			  seEdge = new SimExecutionEdge(seNode,
				                        lastsenode.getCoreNum(),
				                        tmpaction.getTd(),
				                        weight,
				                        tmpaction.getTaskParams());
		      }
		      lastsenode.addEdge(seEdge);
		      simexegraph.add(seEdge);
		  }
		  lastseNodes[targetCoreNum] = seNode;	      
	      }
	  }
      }
  }
  
  private Vector<ObjectSimulator> finishTaskNormal(TaskSimulator task,
                                                   CheckPoint cp,
                                                   Vector<TransTaskSimulator> tttasks,
                                                   Hashtable<SimExecutionNode, Action> senode2action,
                                                   SimExecutionNode[] lastseNodes,
                                                   Hashtable<Action, Integer> action2exetime) {
      Vector<ObjectSimulator> totransObjs = new Vector<ObjectSimulator>();
      CoreSimulator cs = task.getCs();
      int corenum = cs.getCoreNum();
      Hashtable<Integer, Queue<ObjectInfo>> transObjQueues = 
	  new Hashtable<Integer, Queue<ObjectInfo>>();
      Action action = null;
      if(task.getCurrentRun().getNewObjs() == null) {
	  // task finish without new objects
	  action = new Action(corenum, 
		              Action.TASKFINISH,
		              cs.getRtask());
	  // get the execution time of this task
	  SimExecutionNode lastsenode = lastseNodes[corenum];
	  Action startaction = senode2action.get(lastsenode);
	  action2exetime.put(startaction, cp.getTimepoint() - lastsenode.getTimepoint());
	  
      } else {
	  // task finish with new objects
	  action = new Action(corenum, 
		              Action.TFWITHOBJ,
		              cs.getRtask());
	  // get the execution time of this task
	  SimExecutionNode lastsenode = lastseNodes[corenum];
	  Action startaction = senode2action.get(lastsenode);
	  action2exetime.put(startaction, cp.getTimepoint() - lastsenode.getTimepoint());

	  // get the infomation of how to send new objects
	  Vector<ObjectSimulator> nobjs = task.getCurrentRun().getNewObjs();
	  for(int j = 0; j < nobjs.size(); j++) {
	      ObjectSimulator nobj = nobjs.elementAt(j);
	      totransObjs.add(nobj);
	      
	      action.addNewObj(nobj.getCd(), Integer.valueOf(1));
	      // send the new object to target core according to pre-decide scheduling
	      Queue<Integer> cores = cs.getTargetCores(nobj.getCurrentFS());
	      if(cores == null) {
		  // this obj will reside on this core
		  cs.addObject(nobj);
	      } else {
		  Integer targetCore = cores.poll();
		  if(targetCore == corenum) {
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
		      if(allyCore == corenum) {
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
      
      // group the new objects need to transfer
      Vector<ObjectSimulator> transObjs = cs.finishTask();
      if(transObjs != null) {
	  totransObjs.addAll(transObjs);
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
		  if(targetCore == corenum) {
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
		      if(allyCore == corenum) {
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
      }
      transObjs = null;
      
      // add 'transport' tasks
      Iterator it_entries = transObjQueues.entrySet().iterator();
      while(it_entries.hasNext()) {
	  Entry<Integer, Queue<ObjectInfo>> tmpentry = (Entry<Integer, Queue<ObjectInfo>>)it_entries.next();
	  Integer tmpCoreNum = tmpentry.getKey();
	  Queue<ObjectInfo> nobjs = tmpentry.getValue();
	  TransTaskSimulator tmptask = new TransTaskSimulator(cs, tmpCoreNum, nobjs);
	  this.tasks.add(tmptask);
	  tttasks.add(tmptask);
	  tmpentry = null;
	  nobjs = null;
      }
      transObjQueues = null;
      
      return totransObjs;
  }

  private void generateNewTask(CoreSimulator cs,
	                       CheckPoint cp,
	                       Vector<ObjectSimulator> nobjs,
	                       Vector<TransTaskSimulator> tttasks,
	                       Vector<SimExecutionEdge> simexegraph,
	                       Hashtable<SimExecutionNode, Action> senode2action,
	                       SimExecutionNode[] lastseNodes,
	                       Hashtable<Action, Integer> action2exetime,
	                       Hashtable<TransTaskSimulator, SimExecutionNode> tttask2senode,
	                       Hashtable<Integer, Integer> obj2transtime,
	                       Hashtable<Integer, SimExecutionEdge> obj2lastseedge) {
      TaskSimulator newTask = cs.process();
      int corenum = cs.getCoreNum();
      SimExecutionEdge seEdge = null;
      if(newTask != null) {
	  this.tasks.add(newTask);
	  // add a TASKSTART action into this checkpoint
	  Action action = new Action(corenum, 
		                     Action.TASKSTART,
		                     newTask);
	  cp.addAction(action);
	  if(!(newTask instanceof TransTaskSimulator)) {
	      cp.removeSpareCore(cs.getCoreNum());
	      SimExecutionNode seNode = new SimExecutionNode(corenum, this.processTime);
	      seNode.setSpareCores(cp.getSpareCores());
	      senode2action.put(seNode, action);
	      action2exetime.put(action, -1);		
	      SimExecutionNode lastsenode = lastseNodes[corenum];
	      // create edges between previous senode on this core to this node
	      if(lastsenode != null) {
		  Action tmpaction = senode2action.get(lastsenode);
		  int weight = tmpaction != null? action2exetime.get(tmpaction):0;
		  seEdge = new SimExecutionEdge(seNode,
			                        lastsenode.getCoreNum(),
			                        tmpaction!= null?tmpaction.getTd():null,
			                        weight,
			                        tmpaction!=null?tmpaction.getTaskParams():null);
		  lastsenode.addEdge(seEdge);
	      }
	      lastseNodes[corenum] = seNode;
	      for(int tmpindex = 0; tmpindex < tttasks.size(); tmpindex++) {
		  tttask2senode.put(tttasks.elementAt(tmpindex), seNode);
	      }
	  }
      } else if(tttasks.size() > 0) {
	  SimExecutionNode seNode = new SimExecutionNode(corenum, this.processTime);
	  seNode.setSpareCores(cp.getSpareCores());
	  // no action associated here
	  SimExecutionNode lastsenode = lastseNodes[corenum];
	  // create edges between previous senode on this core to this node
	  if(lastsenode != null) {
	      Action tmpaction = senode2action.get(lastsenode);
	      int weight = action2exetime.get(tmpaction);
	      seEdge = new SimExecutionEdge(seNode,
		                            lastsenode.getCoreNum(),
		                            tmpaction.getTd(),
		                            weight,
		                            tmpaction.getTaskParams());
	      lastsenode.addEdge(seEdge);
	  }
	  lastseNodes[corenum] = seNode;
	  for(int tmpindex = 0; tmpindex < tttasks.size(); tmpindex++) {
	      tttask2senode.put(tttasks.elementAt(tmpindex), seNode);
	  }
      }
      if(seEdge != null) {
	  // setup data dependencies for the task
	  Vector<Integer> taskparams = seEdge.getTaskparams();
	  if(taskparams != null) {
	      for(int i = 0; i < taskparams.size(); i++) {
		  Integer tparam = taskparams.elementAt(i);
		  SimExecutionEdge lastedge = obj2lastseedge.get(tparam);
		  if(lastedge != null) {
		      if(lastedge.getCoreNum() != seEdge.getCoreNum()) {
			  // the obj is transferred from another core
			  // create an seEdge for this transfer
			  int weight = obj2transtime.get(tparam);
			  SimExecutionEdge transseEdge = new SimExecutionEdge((SimExecutionNode)seEdge.getSource(),
				                                              lastedge.getCoreNum(),
				                                              null, // TODO: not sure if this is enough
				                                              weight,
				                                              null);
			  if(((SimExecutionNode)seEdge.getSource()).getTimepoint() < 
				  ((SimExecutionNode)lastedge.getTarget()).getTimepoint()) {
			      System.err.println("ScheduleSimulator:757");
			      System.exit(-1);
			  }
			  lastedge.getTarget().addEdge(transseEdge);
			  simexegraph.add(transseEdge);
			  transseEdge.addPredicate(lastedge);
			  seEdge.addPredicate(transseEdge);
		      } else {
			  seEdge.addPredicate(lastedge);
		      }
		  }
		  // update the last edge associated to the parameter obj
		  obj2lastseedge.put(tparam, seEdge);
	      }
	  }
	  taskparams = null;
	  simexegraph.add(seEdge); // add the seEdge afger all corresponding transfer edges
	  
	  // set seEdge as the last execution edge for all newly created objs
	  if(nobjs != null) {
	      for(int i = 0; i < nobjs.size(); i++) {
		  ObjectSimulator nobj = nobjs.elementAt(i);
		  obj2lastseedge.put(nobj.getOid(), seEdge);
	      }
	  }
      }
  }
  
  private void finishTaskAbnormal(CoreSimulator cs,
	                          CheckPoint cp,
	                          Hashtable<SimExecutionNode, Action> senode2action,
	                          SimExecutionNode[] lastseNodes,
	                          Hashtable<Action, Integer> action2exetime,
	                          int type) {
      Action action = new Action(cs.getCoreNum(), 
	                         type,
	                         cs.getRtask());
      cp.addAction(action);
      cs.finishTask();

      // remove the corresponding action on the starting SimExecutionNode
      SimExecutionNode lastsenode = lastseNodes[cs.getCoreNum()];
      /*if(lastsenode.getInedgeVector().size() > 0) {
	  //SimExecutionEdge inseedge = (SimExecutionEdge)lastsenode.getinedge(0);
	  //lastseNodes[cs.getCoreNum()] = (SimExecutionNode)inseedge.getSource();
      } /*else {
	  lastseNodes[cs.getCoreNum()] = null;
      }*/
      Action tmpaction = senode2action.remove(lastsenode);
      action2exetime.remove(tmpaction);
  }
  
  public class CheckPoint {
    private int timepoint;
    private Vector<Action> actions;
    private Vector<Integer> spareCores;

    public CheckPoint(int timepoint, 
	              int corenum) {
      super();
      this.timepoint = timepoint;
      this.actions = new Vector<Action>();
      this.spareCores = new Vector<Integer>();
      for(int i = 0; i < corenum; i++) {
	  this.spareCores.add(i);
      }
    }

    public Vector<Action> getActions() {
      return actions;
    }

    public void addAction(Action action) {
      this.actions.add(action);
    }
    
    public void removeSpareCore(int core) {
	for(int i = 0 ; i < this.spareCores.size(); i++) {
	    if(this.spareCores.elementAt(i) == core) {
		for(int j = i; j < this.spareCores.size() - 1; j++) {
		    this.spareCores.setElementAt(this.spareCores.elementAt(j + 1), j);
		}
		this.spareCores.remove(this.spareCores.size() - 1);
		return;
	    }
	}
    }

    public int getTimepoint() {
      return timepoint;
    }

    public Vector<Integer> getSpareCores() {
        return spareCores;
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
    private Vector<Integer> taskparams;
    private Hashtable<ClassDescriptor, Integer> nObjs;
    private int nObjNum;
    private ClassDescriptor transObj;

    public Action(int corenum, 
	          int type) {
      this.coreNum = corenum;
      this.type = type;
      this.td = null;
      this.taskparams = null;
      if(this.type == TFWITHOBJ) {
	this.nObjs = new Hashtable<ClassDescriptor, Integer>();
      } else {
	this.nObjs = null;
      }
      this.nObjNum = -1;
      this.transObj = null;
    }
    
    public Action(int corenum, 
	          int type, 
	          TaskSimulator ts) {
	assert(this.type != ADDOBJ);
	
	this.coreNum = corenum;
	this.type = type;
	this.td = ts.getTd();
	Vector<Queue<ObjectSimulator>> paraQueues = ts.getParaQueues();
	this.taskparams = new Vector<Integer>();
	if((this.type != TASKABORT) && (this.type != TASKREMOVE)) {
	    for(int i = 0; i < paraQueues.size(); i++) {
		ObjectSimulator tpara = paraQueues.elementAt(i).peek();
		this.taskparams.add(tpara.getOid());
	    }
	    paraQueues = null;
	}
	if(this.type == TFWITHOBJ) {
	    this.nObjs = new Hashtable<ClassDescriptor, Integer>();
	} else {
	    this.nObjs = null;
	}
	this.nObjNum = -1;
	this.transObj = null;
    }

    public Action(int corenum, 
	          int type, 
	          int objNum, 
	          ClassDescriptor transObj) {
      assert(type == ADDOBJ);
      this.coreNum = corenum;
      this.type = type;
      this.td = null;
      this.taskparams = null;
      this.nObjNum = objNum;
      this.transObj = transObj;
    }

    public void addNewObj(ClassDescriptor cd, 
	                  Integer num) {
      assert(this.type == TFWITHOBJ);

      if(this.nObjs.containsKey(cd)) {
	Integer sum = this.nObjs.get(cd) + num;
	this.nObjs.put(cd, sum);
      } else {
	this.nObjs.put(cd, num);
      }
    }

    public int getCoreNum() {
      return this.coreNum;
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
    
    public Vector<Integer> getTaskParams() {
	return this.taskparams;
    }

    public Hashtable<ClassDescriptor, Integer> getNObjs() {
      return nObjs;
    }
  }

}