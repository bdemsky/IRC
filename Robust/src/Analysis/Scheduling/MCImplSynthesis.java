package Analysis.Scheduling;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import Analysis.OwnershipAnalysis.OwnershipAnalysis;
import Analysis.TaskStateAnalysis.FEdge;
import Analysis.TaskStateAnalysis.FlagState;
import Analysis.TaskStateAnalysis.TaskAnalysis;
import IR.ClassDescriptor;
import IR.State;
import IR.TaskDescriptor;
import IR.TypeUtil;

public class MCImplSynthesis {

    State state;
    ScheduleAnalysis scheduleAnalysis;
    TaskAnalysis taskAnalysis;
    OwnershipAnalysis ownershipAnalysis;
    ScheduleSimulator scheduleSimulator;
    
    int coreNum;
    int scheduleThreshold;
    int probThreshold;
    int generateThreshold;
    
    //Random rand;

    public MCImplSynthesis(State state, 
	                   TaskAnalysis ta,
	                   OwnershipAnalysis oa) {
	this.state = state;
	this.coreNum = state.CORENUM;
	this.taskAnalysis = ta;
	this.ownershipAnalysis = oa;
	this.scheduleAnalysis = new ScheduleAnalysis(state,
		                                     ta);
	this.scheduleAnalysis.setCoreNum(this.coreNum);
	this.scheduleSimulator = new ScheduleSimulator(this.coreNum,
		                                       state,
		                                       ta);
	this.scheduleThreshold = 1000;
	this.probThreshold = 0;
	this.generateThreshold = 30;
	//this.rand = new Random();
    }

    public int getCoreNum() {
	return this.scheduleAnalysis.getCoreNum();
    }

    public int getScheduleThreshold() {
        return scheduleThreshold;
    }

    public void setScheduleThreshold(int scheduleThreshold) {
        this.scheduleThreshold = scheduleThreshold;
    }

    public int getProbThreshold() {
        return probThreshold;
    }

    public void setProbThreshold(int probThreshold) {
        this.probThreshold = probThreshold;
    }

    public int getGenerateThreshold() {
        return generateThreshold;
    }

    public void setGenerateThreshold(int generateThreshold) {
        this.generateThreshold = generateThreshold;
    }

    public Vector<Schedule> synthesis() {
	// Print stuff to the original output and error streams.
	// The stuff printed through the 'origOut' and 'origErr' references
	// should go to the console on most systems while the messages
	// printed through the 'System.out' and 'System.err' will end up in
	// the files we created for them.
	//origOut.println ("\nRedirect:  Round #2");
	//System.out.println ("Test output via 'SimulatorResult.out'.");
	//origOut.println ("Test output via 'origOut' reference.");

	// Save the current standard input, output, and error streams
	// for later restoration.
	PrintStream origOut = System.out;

	// Create a new output stream for the standard output.
	PrintStream stdout  = null;
	try {
	    stdout = new PrintStream(
		    new FileOutputStream(this.state.outputdir + "SimulatorResult_" 
			                 + this.coreNum + ".out"));
	} catch (Exception e) {
	    // Sigh.  Couldn't open the file.
	    System.out.println("Redirect:  Unable to open output file!");
	    System.exit(1);
	}

	// Print stuff to the original output and error streams.
	// On most systems all of this will end up on your console when you
	// run this application.
	//origOut.println ("\nRedirect:  Round #1");
	//System.out.println ("Test output via 'System.out'.");
	//origOut.println ("Test output via 'origOut' reference.");

	// Set the System out and err streams to use our replacements.
	System.setOut(stdout);
	      
	Vector<Schedule> scheduling = null;
	Vector<ScheduleNode> schedulinggraph = null;
	int gid = 1;

	// generate multiple schedulings
	this.scheduleAnalysis.setScheduleThreshold(this.scheduleThreshold);
	this.scheduleAnalysis.schedule(this.generateThreshold);
	if(this.generateThreshold > 5) {
	    this.generateThreshold = 5;
	}

	Vector<Vector<ScheduleNode>> scheduleGraphs = null;
	Vector<Vector<ScheduleNode>> newscheduleGraphs = 
	    this.scheduleAnalysis.getScheduleGraphs();
	Vector<Vector<Schedule>> schedulings = new Vector<Vector<Schedule>>();
	Vector<Integer> selectedSchedulings = new Vector<Integer>();
	Vector<Vector<SimExecutionEdge>> selectedSimExeGraphs = 
	    new Vector<Vector<SimExecutionEdge>>();
	
	// check all multi-parameter tasks
	Vector<TaskDescriptor> multiparamtds = new Vector<TaskDescriptor>();
	Iterator it_tasks = this.state.getTaskSymbolTable().getDescriptorsIterator();
	while(it_tasks.hasNext()) {
	    TaskDescriptor td = (TaskDescriptor)it_tasks.next();
	    if(td.numParameters() > 1) {
		multiparamtds.addElement(td);
	    }
	}
	it_tasks = null;

	int tryindex = 1;
	int bestexetime = Integer.MAX_VALUE;
	Random rand = new Random();
	// simulate the generated schedulings and try to optimize it
	do {
	    System.out.print("+++++++++++++++++++++++++++++++++++++++++++++++++++\n");
	    System.out.print("Simulate and optimize round: #" + tryindex + ": \n");
	    gid += newscheduleGraphs.size();
	    if(scheduleGraphs != null) {
		for(int i = 0; i < scheduleGraphs.size(); i++) {
		    Vector<ScheduleNode> tmpgraph = scheduleGraphs.elementAt(i);
		    for(int j = 0; j < tmpgraph.size(); j++) {
			tmpgraph.elementAt(j).getEdgeVector().clear();
			tmpgraph.elementAt(j).getInedgeVector().clear();
		    }
		    tmpgraph.clear();
		    tmpgraph = null;
		}
		scheduleGraphs.clear();
	    }
	    scheduleGraphs = newscheduleGraphs;
	    schedulings.clear();
	    // get scheduling layouts from schedule graphs
	    for(int i = 0; i < scheduleGraphs.size(); i++) {
		Vector<ScheduleNode> scheduleGraph = scheduleGraphs.elementAt(i);
		Vector<Schedule> tmpscheduling = 
		    generateScheduling(scheduleGraph, multiparamtds);
		schedulings.add(tmpscheduling);
		scheduleGraph = null;
		tmpscheduling = null;
	    }
	    selectedSchedulings.clear();
	    for(int i = 0; i < selectedSimExeGraphs.size(); i++) {
		selectedSimExeGraphs.elementAt(i).clear();
	    }
	    selectedSimExeGraphs.clear();
	    int tmpexetime = this.scheduleSimulator.simulate(schedulings, 
		                                             selectedSchedulings, 
		                                             selectedSimExeGraphs);
	    if(tmpexetime < bestexetime) {
		bestexetime = tmpexetime;
		if(scheduling != null) {
		    scheduling.clear();
		    for(int j = 0; j < schedulinggraph.size(); j++) {
			schedulinggraph.elementAt(j).getEdgeVector().clear();
			schedulinggraph.elementAt(j).getInedgeVector().clear();
		    }
		    schedulinggraph.clear();
		}
		scheduling = schedulings.elementAt(selectedSchedulings.elementAt(0));
		schedulinggraph = scheduleGraphs.elementAt(selectedSchedulings.elementAt(0));
		System.out.print("end of: #" + tryindex + " (bestexetime: " + bestexetime + ")\n");
		System.out.print("+++++++++++++++++++++++++++++++++++++++++++++++++++\n");
		tryindex++;
	    } else if(tmpexetime == bestexetime) {
		System.out.print("end of: #" + tryindex + " (bestexetime: " + bestexetime + ")\n");
		System.out.print("+++++++++++++++++++++++++++++++++++++++++++++++++++\n");
		tryindex++;
		if((Math.abs(rand.nextInt()) % 100) < this.probThreshold) {
		    break;
		}
	    } else {
		break;
	    }

	    // try to optimize the best one scheduling
	    newscheduleGraphs = optimizeScheduling(scheduleGraphs, 
		                                   selectedSchedulings, 
		                                   selectedSimExeGraphs,
		                                   gid,
		                                   this.scheduleThreshold);
	    if(tmpexetime < bestexetime) {
		scheduleGraphs.remove(selectedSchedulings.elementAt(0));
	    }
	}while(newscheduleGraphs != null); // TODO: could it possibly lead to endless loop?

	if(scheduleGraphs != null) {
	    scheduleGraphs.clear();
	}
	scheduleGraphs = null;
	newscheduleGraphs = null;
	schedulings.clear();
	schedulings = null;
	selectedSchedulings.clear();
	selectedSchedulings = null;
	for(int i = 0; i < selectedSimExeGraphs.size(); i++) {
	    selectedSimExeGraphs.elementAt(i).clear();
	}
	selectedSimExeGraphs.clear();
	selectedSimExeGraphs = null;
	multiparamtds.clear();
	multiparamtds = null;

	System.out.print("+++++++++++++++++++++++++++++++++++++++++++++++++++\n");
	System.out.print("selected bestexetime: " + bestexetime + "\n");
	String path = this.state.outputdir + "scheduling_selected.dot";
	SchedulingUtil.printScheduleGraph(path, schedulinggraph);

	// Close the streams.
	try {
	    stdout.close();
	    stdout = null;
	    System.setOut(origOut);
	} catch (Exception e) {
	    origOut.println("Redirect:  Unable to close files!");
	}
	
	schedulinggraph.clear();
	schedulinggraph = null;

	return scheduling;
    }
    
    // for test
    // get the distribution info of new search algorithm
    public void distribution() {
	// Print stuff to the original output and error streams.
	// The stuff printed through the 'origOut' and 'origErr' references
	// should go to the console on most systems while the messages
	// printed through the 'System.out' and 'System.err' will end up in
	// the files we created for them.
	//origOut.println ("\nRedirect:  Round #2");
	//System.out.println ("Test output via 'SimulatorResult.out'.");
	//origOut.println ("Test output via 'origOut' reference.");

	// Save the current standard input, output, and error streams
	// for later restoration.
	PrintStream origOut = System.out;

	// Create a new output stream for the standard output.
	PrintStream stdout  = null;
	try {
	    stdout = new PrintStream(
		    new FileOutputStream(this.state.outputdir + "SimulatorResult_" 
			                 + this.coreNum + ".out"));
	} catch (Exception e) {
	    // Sigh.  Couldn't open the file.
	    System.out.println("Redirect:  Unable to open output file!");
	    System.exit(1);
	}

	// Print stuff to the original output and error streams.
	// On most systems all of this will end up on your console when you
	// run this application.
	//origOut.println ("\nRedirect:  Round #1");
	//System.out.println ("Test output via 'System.out'.");
	//origOut.println ("Test output via 'origOut' reference.");

	// Set the System out and err streams to use our replacements.
	System.setOut(stdout);

	// generate multiple schedulings
	this.scheduleAnalysis.setScheduleThreshold(1000);
	this.scheduleAnalysis.schedule(20);
	this.generateThreshold = 5;
	this.probThreshold = 0;

	Vector<Vector<ScheduleNode>> scheduleGraphs = null;
	Vector<Vector<ScheduleNode>> totestscheduleGraphs = 
	    this.scheduleAnalysis.getScheduleGraphs();
	Vector<Vector<Schedule>> schedulings = new Vector<Vector<Schedule>>();
	Vector<Integer> selectedSchedulings = new Vector<Integer>();
	Vector<Vector<SimExecutionEdge>> selectedSimExeGraphs = 
	    new Vector<Vector<SimExecutionEdge>>();
	
	// check all multi-parameter tasks
	Vector<TaskDescriptor> multiparamtds = new Vector<TaskDescriptor>();
	Iterator it_tasks = this.state.getTaskSymbolTable().getDescriptorsIterator();
	while(it_tasks.hasNext()) {
	    TaskDescriptor td = (TaskDescriptor)it_tasks.next();
	    if(td.numParameters() > 1) {
		multiparamtds.addElement(td);
	    }
	}
	it_tasks = null;
	
	File file=new File(this.state.outputdir + "distributeinfo_s_" + this.coreNum + ".out");
	FileOutputStream dotstream = null; 
	File file2=new File(this.state.outputdir + "distributeinfo_o_" + this.coreNum + ".out");
	FileOutputStream dotstream2 = null; 
	try {
	    dotstream = new FileOutputStream(file,false);
	    dotstream2 = new FileOutputStream(file2,false);
	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
	}
	PrintWriter output = new java.io.PrintWriter(dotstream, true);
	PrintWriter output2 = new java.io.PrintWriter(dotstream2, true);
	output.println("start time(1,000,000 cycles): " + totestscheduleGraphs.size());
	output2.println("optimized time(1,000,000 cycles): " + totestscheduleGraphs.size());
	for(int ii = 0; ii < 1/*totestscheduleGraphs.size()*/; ii++) {
	    Vector<Vector<ScheduleNode>> newscheduleGraphs = 
		new Vector<Vector<ScheduleNode>>();
	    newscheduleGraphs.add(totestscheduleGraphs.elementAt(ii));
	    int tryindex = 1;
	    int bestexetime = Integer.MAX_VALUE;
	    int gid = 1;
	    Vector<Schedule> scheduling = null;
	    Vector<ScheduleNode> schedulinggraph = null;
	    boolean isfirst = true;
	    Random rand = new Random();
	    // simulate the generated schedulings and try to optimize it
	    System.out.print("=========================================================\n");
	    System.out.print("# " + ii + ": \n");
	    do {
		System.out.print("+++++++++++++++++++++++++++++++++++++++++++++++++++\n");
		System.out.print("Simulate and optimize round: #" + tryindex + ": \n");    
		gid += newscheduleGraphs.size();
		if(scheduleGraphs != null) {
		    for(int i = 0; i < scheduleGraphs.size(); i++) {
			Vector<ScheduleNode> tmpgraph = scheduleGraphs.elementAt(i);
			for(int j = 0; j < tmpgraph.size(); j++) {
			    ScheduleNode snode = tmpgraph.elementAt(j);
			    snode.getEdgeVector().clear();
			    snode.getInedgeVector().clear();
			    snode.getScheduleEdges().clear();
			    snode.getClassNodes().clear();
			}
			tmpgraph.clear();
			tmpgraph = null;
		    }
		    scheduleGraphs.clear();
		}
		scheduleGraphs = newscheduleGraphs;
		schedulings.clear();
		// get scheduling layouts from schedule graphs
		for(int i = 0; i < scheduleGraphs.size(); i++) {
		    Vector<ScheduleNode> scheduleGraph = scheduleGraphs.elementAt(i);
		    Vector<Schedule> tmpscheduling = 
			generateScheduling(scheduleGraph, multiparamtds);
		    schedulings.add(tmpscheduling);
		    scheduleGraph = null;
		    tmpscheduling = null;
		}
		selectedSchedulings.clear();
		for(int i = 0; i < selectedSimExeGraphs.size(); i++) {
		    selectedSimExeGraphs.elementAt(i).clear();
		}
		selectedSimExeGraphs.clear();
		int tmpexetime = this.scheduleSimulator.simulate(schedulings, 
			                                         selectedSchedulings, 
			                                         selectedSimExeGraphs);
		if(isfirst) {
		    output.println(((float)tmpexetime/1000000));
		    isfirst = false;
		}
		if(tmpexetime < bestexetime) {
		    bestexetime = tmpexetime;
		    if(scheduling != null) {
			scheduling.clear();
			for(int j = 0; j < schedulinggraph.size(); j++) {
			    ScheduleNode snode = schedulinggraph.elementAt(j);
			    snode.getEdgeVector().clear();
			    snode.getInedgeVector().clear();
			    snode.getScheduleEdges().clear();
			    snode.getClassNodes().clear();
			}
			schedulinggraph.clear();
		    }
		    scheduling = schedulings.elementAt(selectedSchedulings.elementAt(0));
		    schedulinggraph = scheduleGraphs.elementAt(selectedSchedulings.elementAt(0));
		    tryindex++;
		    System.out.print("end of: #" + tryindex + " (bestexetime: " + bestexetime + ")\n");
		    System.out.print("+++++++++++++++++++++++++++++++++++++++++++++++++++\n");
		} else if(tmpexetime == bestexetime) {
		    System.out.print("end of: #" + tryindex + " (bestexetime: " + bestexetime + ")\n");
		    System.out.print("+++++++++++++++++++++++++++++++++++++++++++++++++++\n");
		    tryindex++;
		    if((Math.abs(rand.nextInt()) % 100) < this.probThreshold) {
			break;
		    }
		} else {
		    break;
		}

		// try to optimize theschedulings best one scheduling
		newscheduleGraphs = optimizeScheduling(scheduleGraphs, 
			                               selectedSchedulings, 
			                               selectedSimExeGraphs,
			                               gid,
			                               this.scheduleThreshold);
		if(tmpexetime < bestexetime) {
		    scheduleGraphs.remove(selectedSchedulings.elementAt(0));
		}
	    }while(newscheduleGraphs != null); // TODO: could it possibly lead to endless loop?

	    scheduleGraphs.clear();
	    scheduleGraphs = null;
	    scheduling = null;
	    schedulinggraph = null;
	    if(newscheduleGraphs != null) {
		newscheduleGraphs.clear();
	    }
	    newscheduleGraphs = null;
	    totestscheduleGraphs.elementAt(ii).clear();
	    for(int i = 0; i < schedulings.size(); i++) {
		schedulings.elementAt(i).clear();
	    }
	    schedulings.clear();
	    selectedSchedulings.clear();
	    for(int i = 0; i < selectedSimExeGraphs.size(); i++) {
		selectedSimExeGraphs.elementAt(i).clear();
	    }
	    selectedSimExeGraphs.clear();
	    
	    output2.println(((float)bestexetime/1000000));
	    System.out.print("=========================================================\n");
	}

	if(scheduleGraphs != null) {
	    scheduleGraphs.clear();
	}
	scheduleGraphs = null;
	totestscheduleGraphs = null;
	for(int i = 0; i < schedulings.size(); i++) {
	    schedulings.elementAt(i).clear();
	}
	schedulings.clear();
	schedulings = null;
	selectedSchedulings.clear();
	selectedSchedulings = null;
	selectedSimExeGraphs.clear();
	selectedSimExeGraphs = null;
	multiparamtds.clear();
	multiparamtds = null;

	// Close the streams.
	try {
	    output.close();
	    stdout.close();
	    output = null;
	    stdout = null;
	    System.setOut(origOut);
	} catch (Exception e) {
	    origOut.println("Redirect:  Unable to close files!");
	}

	return;
    }

    private Vector<Vector<ScheduleNode>> optimizeScheduling(Vector<Vector<ScheduleNode>> scheduleGraphs,
	                                                    Vector<Integer> selectedScheduleGraphs,
	                                                    Vector<Vector<SimExecutionEdge>> selectedSimExeGraphs,
	                                                    int gid,
	                                                    int count) {
	if(this.coreNum == 1) {
	    // single core
	    return null;
	}
	
	Vector<Vector<ScheduleNode>> optimizeschedulegraphs = null;
	int lgid = gid;
	int left = count;

	for(int i = 0; i < selectedScheduleGraphs.size(); i++) {
	    Vector<ScheduleNode> schedulegraph = scheduleGraphs.elementAt(
		    selectedScheduleGraphs.elementAt(i));
	    Vector<SimExecutionEdge> simexegraph = selectedSimExeGraphs.elementAt(i);
	    Vector<SimExecutionEdge> criticalPath = analyzeCriticalPath(simexegraph); 
	    Vector<Vector<ScheduleNode>> tmposchedulegraphs = optimizeCriticalPath(schedulegraph, 
		                                                                   criticalPath,
		                                                                   lgid,
		                                                                   left);
	    if(tmposchedulegraphs != null) {
		if(optimizeschedulegraphs == null) {
		    optimizeschedulegraphs = new Vector<Vector<ScheduleNode>>();
		}
		optimizeschedulegraphs.addAll(tmposchedulegraphs);
		lgid += tmposchedulegraphs.size();
		left -= tmposchedulegraphs.size();
		if(left == 0) {
		    schedulegraph = null;
		    simexegraph = null;
		    criticalPath = null;
		    tmposchedulegraphs = null;
		    break;
		}
	    }
	    schedulegraph = null;
	    simexegraph = null;
	    criticalPath = null;
	    tmposchedulegraphs = null;
	}

	return optimizeschedulegraphs;
    }

    private Vector<SimExecutionEdge> analyzeCriticalPath(Vector<SimExecutionEdge> simexegraph) {
	// first figure out the critical path
	Vector<SimExecutionEdge> criticalPath = new Vector<SimExecutionEdge>();
	SimExecutionNode senode = (SimExecutionNode)simexegraph.elementAt(0).getSource();
	getCriticalPath(senode, criticalPath);
	computeBestStartPoint(criticalPath);
	
	return criticalPath;
    }
    
    private void computeBestStartPoint(Vector<SimExecutionEdge> criticalPath) {
	// calculate the earliest start time of each task on the critial path
	for(int i = 0; i < criticalPath.size(); i++) {
	    SimExecutionEdge seedge = criticalPath.elementAt(i);
	    Vector<SimExecutionEdge> predicates = seedge.getPredicates();
	    if(predicates != null) {
		// have predicates
		int starttime = 0;
		// check the latest finish time of all the predicates
		for(int j = 0; j < predicates.size(); j++) {
		    SimExecutionEdge predicate = predicates.elementAt(j);
		    int tmptime = predicate.getBestStartPoint() + predicate.getWeight();
		    if(tmptime > starttime) {
			starttime = tmptime;
			seedge.setLastpredicateEdge(predicate);
			if(predicate.getTd() != null) {
			    seedge.setLastpredicateNode((SimExecutionNode)predicate.getTarget());
			} else {
			    // transfer edge
			    seedge.setLastpredicateNode((SimExecutionNode)predicate.getSource());
			}
		    }
		}
		seedge.setBestStartPoint(starttime);
	    } else if(seedge.getSource().getInedgeVector().size() > 0) {
		// should have only one in edge
		int starttime = ((SimExecutionNode)seedge.getSource()).getTimepoint();
		seedge.setBestStartPoint(starttime);
	    } else {
		// no predicates
		seedge.setBestStartPoint(0);
	    }
	    predicates = null;
	}
    }
    
    // TODO: currently only get one critical path. It's possible that there are
    // multiple critical paths and some of them can not be optimized while others
    // can. Need to fix up for this situation.
    private int getCriticalPath(SimExecutionNode senode, 
	                        Vector<SimExecutionEdge> criticalPath) {
	Vector<SimExecutionEdge> edges = (Vector<SimExecutionEdge>)senode.getEdgeVector();
	if((edges == null) || (edges.size() == 0)) {
	    edges = null;
	    return 0;
	}
	Vector<SimExecutionEdge> subcriticalpath = new Vector<SimExecutionEdge>();
	SimExecutionEdge edge = edges.elementAt(0);
	int sum = edge.getWeight() + getCriticalPath((SimExecutionNode)edge.getTarget(),
		                                      subcriticalpath);
	criticalPath.clear();
	criticalPath.add(edge);
	criticalPath.addAll(subcriticalpath);
	for(int i = 1; i < edges.size(); i++) {
	    edge = edges.elementAt(i);
	    subcriticalpath.clear();
	    int tmpsum = edge.getWeight() 
	               + getCriticalPath((SimExecutionNode)edge.getTarget(),
                                         subcriticalpath);
	    if(tmpsum > sum) {
		// find a longer path
		sum = tmpsum;
		criticalPath.clear();
		criticalPath.add(edge);
		criticalPath.addAll(subcriticalpath);
	    }
	}
	edges = null;
	subcriticalpath.clear();
	subcriticalpath = null;
	return sum;
    }

    private Vector<Vector<ScheduleNode>> optimizeCriticalPath(Vector<ScheduleNode> scheduleGraph,
	                                                      Vector<SimExecutionEdge> criticalPath,
	                                                      int gid,
	                                                      int count) {
	Vector<Vector<ScheduleNode>> optimizeschedulegraphs = null;
	int lgid = gid;
	int left = count;
	
	// for test, print out the criticalPath
	if(this.state.PRINTCRITICALPATH) {
	    SchedulingUtil.printCriticalPath(this.state.outputdir + "criticalpath_" + lgid + ".dot", 
		                             criticalPath);
	}
	
	// first check all seedges whose real start point is late than predicted
	// earliest start time and group them
	int opcheckpoint = Integer.MAX_VALUE;
	Vector<Integer> sparecores = null;
	// first group according to core index, 
	// then group according to task type
	Hashtable<Integer, Hashtable<TaskDescriptor, Vector<SimExecutionEdge>>> tooptimize = 
	    new Hashtable<Integer, Hashtable<TaskDescriptor, Vector<SimExecutionEdge>>>();
	for(int i = 0; i < criticalPath.size(); i++) {
	    SimExecutionEdge seedge = criticalPath.elementAt(i);
	    int starttime = seedge.getBestStartPoint();
	    if(starttime < ((SimExecutionNode)seedge.getSource()).getTimepoint()) {
		// no restrictions due to data dependencies
		// have potential to be parallelled and start execution earlier
		seedge.setFixedTime(false);
		// consider to optimize it only when its predicates can NOT 
		// be optimized, otherwise first considering optimize its predicates
		SimExecutionEdge lastpredicateedge = seedge.getLastpredicateEdge();
		if(lastpredicateedge.isFixedTime()) {
		    if(opcheckpoint >= starttime) {
			// only consider the tasks with smallest best start time
			if(opcheckpoint > starttime) {
			    tooptimize.clear();
			    opcheckpoint = starttime;
			    SimExecutionNode lastpredicatenode = seedge.getLastpredicateNode();
			    int timepoint = lastpredicatenode.getTimepoint();
			    if(lastpredicateedge.getTd() == null) {
				// transfer edge
				timepoint += lastpredicateedge.getWeight();
			    }
			    // mapping to critical path
			    for(int index = 0; index < criticalPath.size(); index++) {
				SimExecutionEdge tmpseedge = criticalPath.elementAt(index);
				SimExecutionNode tmpsenode = 
				    (SimExecutionNode)tmpseedge.getTarget();
				if(tmpsenode.getTimepoint() > timepoint) {
				    // get the spare core info
				    sparecores = tmpsenode.getSpareCores();
				    break;
				}
			    }
			}
			int corenum = seedge.getCoreNum();
			if(!tooptimize.containsKey(corenum)) {
			    tooptimize.put(corenum, 
				    new Hashtable<TaskDescriptor, Vector<SimExecutionEdge>>());
			}
			if(!tooptimize.get(corenum).containsKey(seedge.getTd())) {
			    tooptimize.get(corenum).put(seedge.getTd(), 
				    new Vector<SimExecutionEdge>());
			}
			tooptimize.get(corenum).get(seedge.getTd()).add(seedge);
		    }
		}
	    }
	}

	if(tooptimize.size() > 0) {
	    Iterator<Integer> it_cores = tooptimize.keySet().iterator();
	    // check if it is possible to optimize these tasks
	    if((sparecores == null) || (sparecores.size() == 0)) {
		// lack of spare cores
		while(it_cores.hasNext()) {
		    int corenum = it_cores.next();
		    Hashtable<TaskDescriptor, Vector<SimExecutionEdge>> candidatetasks = 
			tooptimize.get(corenum);
		    if(candidatetasks.keySet().size() > 1) {
			// there are multiple tasks could be optimized to start from 
			// this timepoint, try to change original execution order
			Iterator<TaskDescriptor> it_tds = candidatetasks.keySet().iterator();
			TaskDescriptor td = null;
			int starttime = Integer.MAX_VALUE;
			do {
			    TaskDescriptor tmptd = it_tds.next();
			    Vector<SimExecutionEdge> seedges = candidatetasks.get(td);
			    int tmptime = ((SimExecutionNode)seedges.elementAt(0).getSource()).getTimepoint();
			    for(int i = 1; i < seedges.size(); i++) {
				int ttime = ((SimExecutionNode)seedges.elementAt(i).getSource()).getTimepoint();
				if(ttime < tmptime) {
				    tmptime = ttime;
				}
			    }
			    if(tmptime < starttime) {
				starttime = tmptime;
				td = tmptd;
			    }
			    seedges = null;
			}while(it_tds.hasNext());
			it_tds = null;
			// TODO: only consider non-multi-param tasks currently
			if(td.numParameters() == 1) {
			    Hashtable<Integer, Hashtable<TaskDescriptor, Vector<SimExecutionEdge>>> tooptimize2 = 
				new Hashtable<Integer, Hashtable<TaskDescriptor, Vector<SimExecutionEdge>>>();
			    tooptimize2.put(corenum, candidatetasks);
			    Vector<Vector<ScheduleNode>> ops = innerOptimizeCriticalPath(scheduleGraph,
                                                                                         tooptimize2,
                                                                                         null,
                                                                                         lgid,
                                                                                         left);
			    if(ops != null) {
				if(optimizeschedulegraphs == null) {
				    optimizeschedulegraphs = new Vector<Vector<ScheduleNode>>();
				}
				optimizeschedulegraphs.addAll(ops);
				lgid += optimizeschedulegraphs.size();
				left -= optimizeschedulegraphs.size();
			    }
			    tooptimize2.clear();
			    tooptimize2 = null;
			    ops = null;
			}
		    }
		    candidatetasks = null;
		}
		
		if(left == 0) {
		    it_cores = null;
		    return optimizeschedulegraphs;
		}

		// flush the dependences and earliest start time
		it_cores = tooptimize.keySet().iterator();
		while(it_cores.hasNext()) {
		    Hashtable<TaskDescriptor, Vector<SimExecutionEdge>> candidatetasks = 
			tooptimize.get(it_cores.next());
		    Iterator<Vector<SimExecutionEdge>> it_edgevecs = 
			candidatetasks.values().iterator();
		    while(it_edgevecs.hasNext()) {
			Vector<SimExecutionEdge> edgevec = it_edgevecs.next();
			for(int j = 0; j < edgevec.size(); j++) {
			    SimExecutionEdge edge = edgevec.elementAt(j);
			    SimExecutionEdge lastpredicateedge = edge.getLastpredicateEdge();
			    SimExecutionNode lastpredicatenode = edge.getLastpredicateNode();
			    // if(edge.getCoreNum() != lastpredicate.getCoreNum()) // should never hit this
			    int timepoint = lastpredicatenode.getTimepoint();
			    if(lastpredicateedge.getTd() == null) {
				// transfer edge
				timepoint += lastpredicateedge.getWeight();
			    }
			    // mapping to critical path
			    for(int index = 0; index < criticalPath.size(); index++) {
				SimExecutionEdge tmpseedge = criticalPath.elementAt(index);
				SimExecutionNode tmpsenode = 
				    (SimExecutionNode)tmpseedge.getTarget();
				if(tmpsenode.getTimepoint() > timepoint) {
				    // update the predicate info
				    if(edge.getPredicates() != null) {
					edge.getPredicates().remove(lastpredicateedge);
				    }
				    edge.addPredicate(criticalPath.elementAt(index));
				    break;
				}
			    }
			}
			edgevec = null;
		    }
		    candidatetasks = null;
		    it_edgevecs = null;
		}
		it_cores = null;
		computeBestStartPoint(criticalPath);
		Vector<Vector<ScheduleNode>> ops = optimizeCriticalPath(scheduleGraph, 
                                                                        criticalPath, 
                                                                        lgid,
                                                                        left);
		if(ops != null) {
		    if(optimizeschedulegraphs == null) {
			optimizeschedulegraphs = new Vector<Vector<ScheduleNode>>();
		    }
		    optimizeschedulegraphs.addAll(ops);
		}
		ops = null;
	    } else {
		// there are spare cores, try to reorganize the tasks to the spare 
		// cores
		Vector<Vector<ScheduleNode>> ops = innerOptimizeCriticalPath(scheduleGraph,
                                                                             tooptimize,
                                                                             sparecores,
                                                                             lgid,
                                                                             left);
		if(ops != null) {
		    if(optimizeschedulegraphs == null) {
			optimizeschedulegraphs = new Vector<Vector<ScheduleNode>>();
		    }
		    optimizeschedulegraphs.addAll(ops);
		}
		ops = null;
	    }
	}
	sparecores = null;
	tooptimize.clear();
	tooptimize = null;

	return optimizeschedulegraphs;
    }
    
    private Vector<Vector<ScheduleNode>> innerOptimizeCriticalPath(Vector<ScheduleNode> scheduleGraph,
	                                                           Hashtable<Integer, Hashtable<TaskDescriptor, Vector<SimExecutionEdge>>> tooptimize,
	                                                           Vector<Integer> sparecores,
	                                                           int gid,
	                                                           int count) {
	int lgid = gid;
	int left = count;
	Vector<Vector<ScheduleNode>> optimizeschedulegraphs = null;
	
	// first clone the whole graph
	Vector<ScheduleNode> newscheduleGraph = 
	    cloneScheduleGraph(scheduleGraph, lgid);

	// these nodes are root nodes
	Vector<ScheduleNode> roots = new Vector<ScheduleNode>();
	for(int i = 0; i < newscheduleGraph.size(); i++) {
	    if((sparecores == null) || (sparecores.contains(i))) {
		roots.add(newscheduleGraph.elementAt(i));
	    }
	}

	// map the tasks associated to SimExecutionedges to original 
	// ClassNode in the ScheduleGraph and split them from previous 
	// ScheduleGraph
	Vector<ScheduleNode> tocombines = new Vector<ScheduleNode>();
	Iterator<Integer> it_cores = tooptimize.keySet().iterator();
	while(it_cores.hasNext()) {
	    int corenum = it_cores.next();
	    Hashtable<TaskDescriptor, Vector<SimExecutionEdge>> candidatetasks = 
		tooptimize.get(corenum);
	    Iterator<TaskDescriptor> it_tds = candidatetasks.keySet().iterator();
	    while(it_tds.hasNext()) {
		TaskDescriptor td = it_tds.next();
		int numtosplit = candidatetasks.get(td).size();
		// TODO: currently do not consider multi-param tasks
		if(td.numParameters() == 1) {
		    ClassDescriptor cd = td.getParamType(0).getClassDesc();
		    ScheduleNode snode = newscheduleGraph.elementAt(corenum); // corresponding ScheduleNode
		    Iterator<ClassNode> it_cnodes = snode.getClassNodesIterator();
		    Vector<ClassNode> tosplit = new Vector<ClassNode>();
		    while((numtosplit > 0) && (it_cnodes.hasNext())) {
			ClassNode cnode = it_cnodes.next();
			if(cnode.getClassDescriptor().equals(cd)) {
			    tosplit.add(cnode);
			    numtosplit--;
			}
		    }
		    it_cnodes = null;
		    // split these node
		    for(int i = 0; i < tosplit.size(); i++) {
			ScheduleNode splitnode = snode.spliteClassNode(tosplit.elementAt(i));
			newscheduleGraph.add(splitnode);
			tocombines.add(splitnode);
		    }
		    tosplit = null;
		}
	    }
	    candidatetasks = null;
	    it_tds = null;
	}
	it_cores = null;
	
	if(tocombines.size() == 0) {
	    return optimizeschedulegraphs;
	}

	SchedulingUtil.assignCids(newscheduleGraph);

	// get all the ScheduleEdge
	Vector<ScheduleEdge> scheduleEdges = new Vector<ScheduleEdge>();
	for(int i= 0; i < newscheduleGraph.size(); i++) {
	    scheduleEdges.addAll((Vector<ScheduleEdge>)newscheduleGraph.elementAt(i).getEdgeVector());
	}

	Vector<Vector<ScheduleNode>> rootNodes =  
	    SchedulingUtil.rangeScheduleNodes(roots);
	Vector<Vector<ScheduleNode>> nodes2combine = 
	    SchedulingUtil.rangeScheduleNodes(tocombines);

	CombinationUtil.CombineGenerator cGen = 
	    CombinationUtil.allocateCombineGenerator(rootNodes, nodes2combine);
	Random rand = new Random();
	while ((left > 0) && (cGen.nextGen())) {
	    if(Math.abs(rand.nextInt()) % 100 > this.generateThreshold) {
		Vector<Vector<CombinationUtil.Combine>> combine = cGen.getCombine();
		Vector<ScheduleNode> sNodes = SchedulingUtil.generateScheduleGraph(this.state,
			                                                           newscheduleGraph,
			                                                           scheduleEdges,
			                                                           rootNodes, 
			                                                           combine, 
			                                                           lgid++);
		if(optimizeschedulegraphs == null) {
		    optimizeschedulegraphs = new Vector<Vector<ScheduleNode>>();
		}
		optimizeschedulegraphs.add(sNodes);
		combine = null;
		sNodes = null;
		left--;
	    }
	}
	cGen.clear();
	rootNodes = null;
	nodes2combine = null;
	newscheduleGraph = null;
	scheduleEdges.clear();
	scheduleEdges = null;
	roots = null;
	tocombines = null;
	
	return optimizeschedulegraphs;
    }
    
    private Vector<ScheduleNode> cloneScheduleGraph(Vector<ScheduleNode> scheduleGraph,
	                                            int gid) {
	Vector<ScheduleNode> result = new Vector<ScheduleNode>();
	
	// get all the ScheduleEdge
	Vector<ScheduleEdge> scheduleEdges = new Vector<ScheduleEdge>();
	for(int i= 0; i < scheduleGraph.size(); i++) {
	    scheduleEdges.addAll((Vector<ScheduleEdge>)scheduleGraph.elementAt(i).getEdgeVector());
	}
	Hashtable<ScheduleNode, Hashtable<ClassNode, ClassNode>> sn2hash = 
	    new Hashtable<ScheduleNode, Hashtable<ClassNode, ClassNode>>();
	Hashtable<ScheduleNode, ScheduleNode> sn2sn = 
	    new Hashtable<ScheduleNode, ScheduleNode>();
	SchedulingUtil.cloneScheduleGraph(scheduleGraph,
		                          scheduleEdges,
		                          sn2hash,
		                          sn2sn,
		                          result,
		                          gid);
	
	SchedulingUtil.assignCids(result);
	scheduleEdges.clear();
	scheduleEdges = null;
	sn2hash.clear();
	sn2hash = null;
	sn2sn.clear();
	sn2sn = null;
	
	return result;
    }

    private Vector<Schedule> generateScheduling(Vector<ScheduleNode> scheduleGraph,
	                                        Vector<TaskDescriptor> multiparamtds) {
	Hashtable<TaskDescriptor, Vector<Schedule>> td2cores = 
	    new Hashtable<TaskDescriptor, Vector<Schedule>>(); // multiparam tasks reside on which cores
	Vector<Schedule> scheduling = new Vector<Schedule>(scheduleGraph.size());
	// for each ScheduleNode create a schedule node representing a core
	Hashtable<ScheduleNode, Integer> sn2coreNum = 
	    new Hashtable<ScheduleNode, Integer>();
	int j = 0;
	for(j = 0; j < scheduleGraph.size(); j++) {
	    sn2coreNum.put(scheduleGraph.elementAt(j), j);
	}
	int startupcore = 0;
	boolean setstartupcore = false;
	Schedule startup = null;
	int gid = scheduleGraph.elementAt(0).getGid();
	for(j = 0; j < scheduleGraph.size(); j++) {
	    Schedule tmpSchedule = new Schedule(j, gid);
	    ScheduleNode sn = scheduleGraph.elementAt(j);

	    Vector<ClassNode> cNodes = sn.getClassNodes();
	    for(int k = 0; k < cNodes.size(); k++) {
		Iterator it_flags = cNodes.elementAt(k).getFlags();
		while(it_flags.hasNext()) {
		    FlagState fs = (FlagState)it_flags.next();
		    Iterator it_edges = fs.edges();
		    while(it_edges.hasNext()) {
			TaskDescriptor td = ((FEdge)it_edges.next()).getTask();
			tmpSchedule.addTask(td);
			if(!td2cores.containsKey(td)) {
			    td2cores.put(td, new Vector<Schedule>());
			}
			Vector<Schedule> tmpcores = td2cores.get(td);
			if(!tmpcores.contains(tmpSchedule)) {
			    tmpcores.add(tmpSchedule);
			}
			tmpcores = null;
			// if the FlagState can be fed to some multi-param tasks,
			// need to record corresponding ally cores later
			if(td.numParameters() > 1) {
			    tmpSchedule.addFState4TD(td, fs);
			}
			if(td.getParamType(0).getClassDesc().getSymbol().equals(
				TypeUtil.StartupClass)) {
			    assert(!setstartupcore);
			    startupcore = j;
			    startup = tmpSchedule;
			    setstartupcore = true;
			}
		    }
		    it_edges = null;
		}
		it_flags = null;
	    }
	    cNodes = null;

	    //  For each of the ScheduleEdge out of this ScheduleNode, add the target ScheduleNode into the queue inside sn
	    Iterator it_edges = sn.edges();
	    while(it_edges.hasNext()) {
		ScheduleEdge se = (ScheduleEdge)it_edges.next();
		ScheduleNode target = (ScheduleNode)se.getTarget();
		Integer targetcore = sn2coreNum.get(target);
		switch(se.getType()) {
		case ScheduleEdge.NEWEDGE: {
		    for(int k = 0; k < se.getNewRate(); k++) {
			tmpSchedule.addTargetCore(se.getFstate(), targetcore);
		    }
		    break;
		}

		case ScheduleEdge.TRANSEDGE: {
		    // 'transmit' edge
		    tmpSchedule.addTargetCore(se.getFstate(), 
			                      targetcore, 
			                      se.getTargetFState());
		    // check if missed some FlagState associated with some multi-parameter
		    // task, which has been cloned when splitting a ClassNode
		    FlagState fs = se.getSourceFState();
		    FlagState tfs = se.getTargetFState();
		    Iterator it = tfs.edges();
		    while(it.hasNext()) {
			TaskDescriptor td = ((FEdge)it.next()).getTask();
			if(td.numParameters() > 1) {
			    if(tmpSchedule.getTasks().contains(td)) {
				tmpSchedule.addFState4TD(td, fs);
			    }
			}
		    }
		    break;
		}
		}
	    }
	    it_edges = sn.getScheduleEdgesIterator();
	    while(it_edges.hasNext()) {
		ScheduleEdge se = (ScheduleEdge)it_edges.next();
		switch(se.getType()) {
		case ScheduleEdge.NEWEDGE: {
		    for(int k = 0; k < se.getNewRate(); k++) {
			tmpSchedule.addTargetCore(se.getFstate(), j);
		    }
		    break;
		}

		case ScheduleEdge.TRANSEDGE: {
		    // 'transmit' edge
		    tmpSchedule.addTargetCore(se.getFstate(), 
			                      j, 
			                      se.getTargetFState());
		    break;
		}
		}
	    }
	    it_edges = null;
	    scheduling.add(tmpSchedule);
	}

	int number = this.coreNum;
	if(scheduling.size() < number) {
	    number = scheduling.size();
	}

	// set up all the associate ally cores
	if(multiparamtds.size() > 0) {
	    for(j = 0; j < multiparamtds.size(); ++j) {
		TaskDescriptor td = multiparamtds.elementAt(j);
		Vector<FEdge> fes = 
		    (Vector<FEdge>) this.taskAnalysis.getFEdgesFromTD(td);
		Vector<Schedule> cores = td2cores.get(td);
		for(int k = 0; k < cores.size(); ++k) {
		    Schedule tmpSchedule = cores.elementAt(k);

		    for(int h = 0; h < fes.size(); ++h) {
			FEdge tmpfe = fes.elementAt(h);
			FlagState tmpfs = (FlagState)tmpfe.getTarget();
			Vector<TaskDescriptor> tmptds = new Vector<TaskDescriptor>();
			if((tmpSchedule.getTargetCoreTable() == null) 
				|| (!tmpSchedule.getTargetCoreTable().containsKey(tmpfs))) {
			    // add up all possible cores' info
			    Iterator it_edges = tmpfs.edges();
			    while(it_edges.hasNext()) {
				TaskDescriptor tmptd = ((FEdge)it_edges.next()).getTask();
				if(!tmptds.contains(tmptd)) {
				    tmptds.add(tmptd);
				    Vector<Schedule> tmpcores = td2cores.get(tmptd);
				    for(int m = 0; m < tmpcores.size(); ++m) {
					if(m != tmpSchedule.getCoreNum()) {
					    // if the FlagState can be fed to some multi-param tasks,
					    // need to record corresponding ally cores later
					    if(tmptd.numParameters() > 1) {
						tmpSchedule.addAllyCore(tmpfs, 
							                tmpcores.elementAt(m).getCoreNum());
					    } else {
						tmpSchedule.addTargetCore(tmpfs, 
							                  tmpcores.elementAt(m).getCoreNum());
					    }
					}
				    }
				    tmpcores = null;
				}
			    }
			    it_edges = null;
			}
			tmptds = null;
		    }

		    if(cores.size() > 1) {
			Vector<FlagState> tmpfss = tmpSchedule.getFStates4TD(td);
			for(int h = 0; h < tmpfss.size(); ++h) {
			    for(int l = 0; l < cores.size(); ++l) {
				if(l != k) {
				    tmpSchedule.addAllyCore(tmpfss.elementAt(h), 
					                    cores.elementAt(l).getCoreNum());
				}
			    }
			}
			tmpfss = null;
		    }
		}
		fes = null;
		cores = null;
	    }
	}
	td2cores = null;
	sn2coreNum = null;

	return scheduling;
    }
}
