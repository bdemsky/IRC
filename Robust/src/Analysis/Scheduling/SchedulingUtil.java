package Analysis.Scheduling;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import Analysis.Scheduling.ScheduleSimulator.Action;
import Analysis.Scheduling.ScheduleSimulator.CheckPoint;
import Analysis.TaskStateAnalysis.Allocations;
import Analysis.TaskStateAnalysis.FEdge;
import Analysis.TaskStateAnalysis.FlagState;
import Analysis.TaskStateAnalysis.FEdge.NewObjInfo;
import IR.ClassDescriptor;
import IR.Operation;
import IR.State;
import IR.Tree.FlagExpressionNode;
import IR.Tree.FlagNode;
import IR.Tree.FlagOpNode;
import Util.Edge;
import Util.GraphNode;
import Util.Namer;

public class SchedulingUtil {
    
    public static Vector<ScheduleNode> generateScheduleGraph(State state, 
	                                                     Vector<ScheduleNode> scheduleNodes,
	                                                     Vector<ScheduleEdge> scheduleEdges,
	                                                     Vector<Vector<ScheduleNode>> rootnodes, 
	                                                     Vector<Vector<CombinationUtil.Combine>> combine, 
	                                                     int gid) {
	Vector<ScheduleNode> result = new Vector<ScheduleNode>();

	// clone the ScheduleNodes
	Hashtable<ScheduleNode, Hashtable<ClassNode, ClassNode>> sn2hash = 
	    new Hashtable<ScheduleNode, Hashtable<ClassNode, ClassNode>>();
	Hashtable<ScheduleNode, ScheduleNode> sn2sn = 
	    new Hashtable<ScheduleNode, ScheduleNode>();
	cloneScheduleGraph(scheduleNodes,
		           scheduleEdges,
		           sn2hash,
		           sn2sn,
		           result,
		           gid);

	// combine those nodes in combine with corresponding rootnodes
	for(int i = 0; i < combine.size(); i++) {
	    if(combine.elementAt(i) != null) {
		for(int j = 0; j < combine.elementAt(i).size(); j++) {
		    CombinationUtil.Combine tmpcombine = combine.elementAt(i).elementAt(j);
		    ScheduleNode tocombine = sn2sn.get(tmpcombine.node);
		    ScheduleNode root = sn2sn.get(rootnodes.elementAt(tmpcombine.root).elementAt(tmpcombine.index));
		    ScheduleEdge se = (ScheduleEdge)tocombine.inedges().next();
		    try {
			if(root.equals(((ScheduleNode)se.getSource()))) {
			    root.mergeSEdge(se);
			    if(ScheduleEdge.NEWEDGE == se.getType()) {
				// As se has been changed into an internal edge inside a ScheduleNode,
				// change the source and target of se from original ScheduleNodes into ClassNodes.
				se.setTarget(se.getTargetCNode());
				//se.setSource(se.getSourceCNode());
				//se.getTargetCNode().addEdge(se);
				se.getSourceCNode().addEdge(se);
			    }
			} else {
			    root.mergeSNode(tocombine);
			}
		    } catch(Exception e) {
			e.printStackTrace();
			System.exit(-1);
		    }
		    result.removeElement(tocombine);
		}
	    }
	}
	
	assignCids(result);
	
	sn2hash.clear();
	sn2hash = null;
	sn2sn.clear();
	sn2sn = null;

	if(state.PRINTSCHEDULING) {
	    String path = state.outputdir + "scheduling_" + gid + ".dot";
	    SchedulingUtil.printScheduleGraph(path, result);
	}

	return result;
    }
    
    public static void cloneScheduleGraph(Vector<ScheduleNode> scheduleNodes,
                                          Vector<ScheduleEdge> scheduleEdges,
                                          Hashtable<ScheduleNode, Hashtable<ClassNode, ClassNode>> sn2hash,
                                          Hashtable<ScheduleNode, ScheduleNode> sn2sn,
                                          Vector<ScheduleNode> result,
                                          int gid) {
	for(int i = 0; i < scheduleNodes.size(); i++) {
	    Hashtable<ClassNode, ClassNode> cn2cn = new Hashtable<ClassNode, ClassNode>();
	    ScheduleNode tocopy = scheduleNodes.elementAt(i);
	    ScheduleNode temp = (ScheduleNode)tocopy.clone(cn2cn, gid);
	    result.add(i, temp);
	    sn2hash.put(temp, cn2cn);
	    sn2sn.put(tocopy, temp);
	    cn2cn = null;
	}
	// clone the ScheduleEdges
	for(int i = 0; i < scheduleEdges.size(); i++) {
	    ScheduleEdge sse = scheduleEdges.elementAt(i);
	    ScheduleNode csource = sn2sn.get(sse.getSource());
	    ScheduleNode ctarget = sn2sn.get(sse.getTarget());
	    Hashtable<ClassNode, ClassNode> sourcecn2cn = sn2hash.get(csource);
	    Hashtable<ClassNode, ClassNode> targetcn2cn = sn2hash.get(ctarget);
	    ScheduleEdge se =  null;
	    switch(sse.getType()) {
	    case ScheduleEdge.NEWEDGE: {
		se = new ScheduleEdge(ctarget, "new", sse.getFstate(), sse.getType(), gid);       //new ScheduleEdge(ctarget, "new", sse.getClassDescriptor(), sse.getIsNew(), gid);
		se.setProbability(sse.getProbability());
		se.setNewRate(sse.getNewRate());
		break;
	    }

	    case ScheduleEdge.TRANSEDGE: {
		se = new ScheduleEdge(ctarget, "transmit", sse.getFstate(), sse.getType(), gid);       //new ScheduleEdge(ctarget, "transmit", sse.getClassDescriptor(), false, gid);
		break;
	    }
	    }
	    se.setSourceCNode(sourcecn2cn.get(sse.getSourceCNode()));
	    se.setTargetCNode(targetcn2cn.get(sse.getTargetCNode()));
	    se.setFEdge(sse.getFEdge());
	    se.setTargetFState(sse.getTargetFState());
	    se.setIsclone(true);
	    csource.addEdge(se);
	    sourcecn2cn = null;
	    targetcn2cn = null;
	}
    }
    
    public static void assignCids(Vector<ScheduleNode> result) {
	Hashtable<Integer, Integer> hcid2cid = new Hashtable<Integer, Integer>();
	int ncid = 0;
	for(int i = 0; i < result.size(); i++) {
	    ScheduleNode tmpnode = result.elementAt(i);
	    tmpnode.computeHashcid();
	    int hcid = tmpnode.getHashcid();
	    if(hcid2cid.containsKey(hcid)) {
		// already have a cid for this node
		tmpnode.setCid(hcid2cid.get(hcid));
	    } else {
		// generate a new cid for such node
		tmpnode.setCid(ncid);
		hcid2cid.put(hcid, ncid);
		ncid++;
	    }
	}
	hcid2cid.clear();
	hcid2cid = null;
    }
    
    //  Organize the scheduleNodes in order of their cid
    public static Vector<Vector<ScheduleNode>> rangeScheduleNodes(Vector<ScheduleNode> scheduleNodes) {
	Vector<Vector<ScheduleNode>> sNodeVecs = new Vector<Vector<ScheduleNode>>();
	
	for(int i = 0; i < scheduleNodes.size(); i++) {
	    ScheduleNode tmpn = scheduleNodes.elementAt(i);
	    int tmpcid = tmpn.getCid();
	    int index = 0;
	    for(index = 0; index < sNodeVecs.size(); index++) {
		if(sNodeVecs.elementAt(index).elementAt(0).getCid() > tmpcid) {
		    // find the place to insert
		    sNodeVecs.add(sNodeVecs.lastElement());
		    for(int j = sNodeVecs.size() - 2; j > index; j--) {
			sNodeVecs.setElementAt(sNodeVecs.elementAt(j - 1), j);
		    }
		    sNodeVecs.setElementAt(new Vector<ScheduleNode>(), index);
		} else if(sNodeVecs.elementAt(index).elementAt(0).getCid() == tmpcid) {
		    break;
		}
	    }
	    if(index == sNodeVecs.size()) {
		sNodeVecs.add(new Vector<ScheduleNode>());
	    }
	    
	    /*int index = tmpcid;
	    while(sNodeVecs.size() <= index) {
		sNodeVecs.add(null);
	    }
	    if(sNodeVecs.elementAt(index) == null) {
		sNodeVecs.setElementAt(new Vector<ScheduleNode>(), index);
	    }*/
	    sNodeVecs.elementAt(index).add(tmpn);
	}
	
	return sNodeVecs;
    }

  /*public static int maxDivisor(int l, int r) {
      int a = l;
      int b = r;
      int c = 0;

      while(true) {
          if(a == 0) {
              return b << c;
          } else if(b == 0) {
              return a << c;
          }

          if(((a&1)==0) && ((b&1)==0)) {
              // a and b are both even
              a >>= 1;
              b >>= 1;
   ++c;
          } else if(((a&1)==0) && ((b&1)!=0)) {
              // a is even, b is odd
              a >>= 1;
          } else if (((a&1)!=0) && ((b&1)==0)) {
              // a is odd, b is even
              b >>= 1;
          } else if (((a&1)!=0) && ((b&1)!=0)) {
              // a and b are both odd
              int tmp = a>b? b:a;
              a = a>b ? (a-b):(b-a);
              b = tmp;
          }
      }
     }*/

  public static boolean isTaskTrigger_flag(FlagExpressionNode fen,
	                                   FlagState fs) {
    if (fen==null)
      return true;
    else if (fen instanceof FlagNode)
      return fs.get(((FlagNode)fen).getFlag());
    else
      switch (((FlagOpNode)fen).getOp().getOp()) {
      case Operation.LOGIC_AND:
	return ((isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs)) && (isTaskTrigger_flag(((FlagOpNode)fen).getRight(),fs)));

      case Operation.LOGIC_OR:
	return ((isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs)) || (isTaskTrigger_flag(((FlagOpNode)fen).getRight(),fs)));

      case Operation.LOGIC_NOT:
	return !(isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs));

      default:
	return false;
      }
  }

  public static void printScheduleGraph(String path, 
	                                Vector<ScheduleNode> sNodes) {
    try {
      File file=new File(path);
      FileOutputStream dotstream=new FileOutputStream(file,false);
      PrintWriter output = new java.io.PrintWriter(dotstream, true);
      output.println("digraph G {");
      output.println("\tcompound=true;\n");
      traverseSNodes(output, sNodes);
      output.println("}\n");
      output.close();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  private static void traverseSNodes(PrintWriter output, 
	                             Vector<ScheduleNode> sNodes) {
    //Draw clusters representing ScheduleNodes
    Iterator it = sNodes.iterator();
    while (it.hasNext()) {
      ScheduleNode gn = (ScheduleNode) it.next();
      Iterator edges = gn.edges();
      output.println("\tsubgraph " + gn.getLabel() + "{");
      output.println("\t\tlabel=\"" + gn.getTextLabel() + "\";");
      Iterator it_cnodes = gn.getClassNodesIterator();
      traverseCNodes(output, it_cnodes);
      it_cnodes = null;
      //Draw the internal 'new' edges
      Iterator it_edges =gn.getScheduleEdgesIterator();
      while(it_edges.hasNext()) {
	ScheduleEdge se = (ScheduleEdge)it_edges.next();
	output.print("\t");
	if(se.getSourceCNode().isclone()) {
	  output.print(se.getSourceCNode().getLabel());
	} else {
	  if(se.getSourceFState() == null) {
	    output.print(se.getSourceCNode().getClusterLabel());
	  } else {
	    output.print(se.getSourceFState().getLabel());
	  }
	}

	output.print(" -> ");
	if(se.isclone()) {
	  if(se.getTargetCNode().isclone()) {
	    output.print(se.getTargetCNode().getLabel());
	  } else {
	    output.print(se.getTargetCNode().getClusterLabel());
	  }
	  output.println(" [label=\"" + se.getLabel() + "\", color=red];");
	} else {
	  output.print(se.getTargetFState().getLabel() + " [label=\"" + se.getLabel() + "\", color=red, ltail=");
	  if(se.getSourceCNode().isclone()) {
	    output.println(se.getSourceCNode().getLabel() + "];");
	  } else {
	    output.println(se.getSourceCNode().getClusterLabel() + "];");
	  }
	}
      }
      output.println("\t}\n");
      it_edges = null;
      //Draw 'new' edges of this ScheduleNode
      while(edges.hasNext()) {
	ScheduleEdge se = (ScheduleEdge)edges.next();
	output.print("\t");
	if(se.getSourceCNode().isclone()) {
	  output.print(se.getSourceCNode().getLabel());
	} else {
	  if(se.getSourceFState() == null) {
	    output.print(se.getSourceCNode().getClusterLabel());
	  } else {
	    output.print(se.getSourceFState().getLabel());
	  }
	}

	output.print(" -> ");
	if(se.isclone()) {
	  if(se.getTargetCNode().isclone()) {
	    output.print(se.getTargetCNode().getLabel());
	  } else {
	    output.print(se.getTargetCNode().getClusterLabel());
	  }
	  output.println(" [label=\"" + se.getLabel() + "\", color=red, style=dashed];");
	} else {
	  output.println(se.getTargetFState().getLabel() + " [label=\"" + se.getLabel() + "\", color=red, style=dashed];");
	}
      }
      edges = null;
    }
    it = null;
  }

  private static void traverseCNodes(PrintWriter output, 
	                             Iterator it) {
    //Draw clusters representing ClassNodes
    while (it.hasNext()) {
      ClassNode gn = (ClassNode) it.next();
      if(gn.isclone()) {
	output.println("\t\t" + gn.getLabel() + " [style=dashed, label=\"" + gn.getTextLabel() + "\", shape=box];");
      } else {
	output.println("\tsubgraph " + gn.getClusterLabel() + "{");
	output.println("\t\tstyle=dashed;");
	output.println("\t\tlabel=\"" + gn.getTextLabel() + "\";");
	traverseFlagStates(output, gn.getFlagStates());
	output.println("\t}\n");
      }
    }
  }

  private static void traverseFlagStates(PrintWriter output, 
	                                 Collection nodes) {
    Set cycleset=GraphNode.findcycles(nodes);
    Vector namers=new Vector();
    namers.add(new Namer());
    namers.add(new Allocations());

    Iterator it = nodes.iterator();
    while (it.hasNext()) {
      GraphNode gn = (GraphNode) it.next();
      Iterator edges = gn.edges();
      String label = "";
      String dotnodeparams="";

      for(int i=0; i<namers.size(); i++) {
	Namer name=(Namer) namers.get(i);
	String newlabel=name.nodeLabel(gn);
	String newparams=name.nodeOption(gn);

	if (!newlabel.equals("") && !label.equals("")) {
	  label+=", ";
	}
	if (!newparams.equals("")) {
	  dotnodeparams+=", " + name.nodeOption(gn);
	}
	label+=name.nodeLabel(gn);
      }
      label += ":[" + ((FlagState)gn).getExeTime() + "]";

      if (!gn.merge)
	output.println("\t" + gn.getLabel() + " [label=\"" + label + "\"" + dotnodeparams + "];");

      if (!gn.merge)
	while (edges.hasNext()) {
	  Edge edge = (Edge) edges.next();
	  GraphNode node = edge.getTarget();
	  if (nodes.contains(node)) {
	      Iterator nodeit=nonmerge(node, nodes).iterator();
	    for(; nodeit.hasNext();) {
	      GraphNode node2=(GraphNode)nodeit.next();
	      String edgelabel = "";
	      String edgedotnodeparams="";

	      for(int i=0; i<namers.size(); i++) {
		Namer name=(Namer) namers.get(i);
		String newlabel=name.edgeLabel(edge);
		String newoption=name.edgeOption(edge);
		if (!newlabel.equals("")&& !edgelabel.equals(""))
		  edgelabel+=", ";
		edgelabel+=newlabel;
		if (!newoption.equals(""))
		  edgedotnodeparams+=", "+newoption;
	      }
	      edgelabel+=":[" + ((FEdge)edge).getExeTime() + "]";
	      edgelabel+=":(" + ((FEdge)edge).getProbability() + "%)";
	      Hashtable<ClassDescriptor, NewObjInfo> hashtable = ((FEdge)edge).getNewObjInfoHashtable();
	      if(hashtable != null) {
		Set<ClassDescriptor> keys = hashtable.keySet();
		Iterator it_keys = keys.iterator();
		while(it_keys.hasNext()) {
		  ClassDescriptor cd = (ClassDescriptor)it_keys.next();
		  NewObjInfo noi = hashtable.get(cd);
		  edgelabel += ":{ class " + cd.getSymbol() + " | " + noi.getNewRate() + " | (" + noi.getProbability() + "%) }";
		}
		keys = null;
		it_keys = null;
	      }
	      output.println("\t" + gn.getLabel() + " -> " + node2.getLabel() + " [" + "label=\"" + edgelabel + "\"" + edgedotnodeparams + "];");
	    }
	    nodeit = null;
	  }
	}
      edges = null;
    }
    cycleset = null;
    namers = null;
    it = null;
  }

  private static Set nonmerge(GraphNode gn, 
	                      Collection nodes) {
    HashSet newset=new HashSet();
    HashSet toprocess=new HashSet();
    toprocess.add(gn);
    while(!toprocess.isEmpty()) {
      GraphNode gn2=(GraphNode)toprocess.iterator().next();
      toprocess.remove(gn2);
      if (!gn2.merge)
	newset.add(gn2);
      else {
	Iterator edges = gn2.edges();
	while (edges.hasNext()) {
	  Edge edge = (Edge) edges.next();
	  GraphNode node = edge.getTarget();
	  if (!newset.contains(node)&&nodes.contains(node))
	    toprocess.add(node);
	}
	edges = null;
      }
    }
    toprocess = null;
    return newset;
  }

  public static void printSimulationResult(String path, 
	                                   int time, 
	                                   int coreNum, 
	                                   Vector<CheckPoint> checkpoints) {
    try {
      File file=new File(path);
      FileOutputStream dotstream=new FileOutputStream(file,false);
      PrintWriter output = new java.io.PrintWriter(dotstream, true);
      output.println("digraph simulation{");
      output.print("\t");
      output.println("node [shape=plaintext];");
      output.print("\t");
      output.println("edge [dir=none];");
      output.print("\t");
      output.println("ranksep=.05;");
      output.println();
      output.print("\t");
      int j = 0;

      // the capital line
      output.print("{rank=source; \"Time\"; ");
      for(j = 0; j < coreNum; j++) {
	output.print("\"core " + j + "\"; ");
      }
      output.println("}");
      // time coordinate nodes
      Vector<String> timeNodes = new Vector<String>();
      String[] lastTaskNodes = new String[coreNum];
      String[] lastTasks = new String[coreNum];
      boolean[] isTaskFinish = new boolean[coreNum];
      for(j = 0; j < coreNum; j++) {
	lastTaskNodes[j] = "first";
	isTaskFinish[j] = true;
	lastTasks[j] = "";
      }
      timeNodes.add("0");
      for(j = 0; j < checkpoints.size(); j++) {
	CheckPoint tcp = checkpoints.elementAt(j);
	Hashtable<Integer, String> tmplastTasks = new Hashtable<Integer, String>();
	Vector<Integer> tmpisTaskFinish = new Vector<Integer>();
	Vector<Integer> tmpisset = new Vector<Integer>();
	String tnode = String.valueOf(tcp.getTimepoint());
	if(!timeNodes.contains(tnode)) {
	  timeNodes.add(tnode);
	}
	Vector<Action> actions = tcp.getActions();
	Hashtable<String, StringBuffer> tmpTaskNodes = new Hashtable<String, StringBuffer>();
	for(int i = 0; i < actions.size(); i++) {
	  Action taction = actions.elementAt(i);
	  int cNum = taction.getCoreNum();
	  if(!tmplastTasks.containsKey(cNum)) {
	    tmplastTasks.put(cNum, lastTasks[cNum]);
	  }
	  if(!(tmpisset.contains(cNum)) 
		  && (isTaskFinish[cNum]) 
		  && !(tmpisTaskFinish.contains(cNum))) {
	    tmpisTaskFinish.add(cNum);  // records those with task finished the first time visit it
	  }
	  String tmpTaskNode = "\"" + tnode + "core" + cNum + "\"";
	  StringBuffer tmpLabel = null;
	  boolean isfirst = false;
	  if(!tmpTaskNodes.containsKey(tmpTaskNode)) {
	    tmpTaskNodes.put(tmpTaskNode, new StringBuffer(tnode + ":"));
	    isfirst = true;
	  }
	  tmpLabel = tmpTaskNodes.get(tmpTaskNode);
	  switch(taction.getType()) {
	  case Action.ADDOBJ: {
	    if(!isfirst) {
	      tmpLabel.append("\\n");
	    }
	    tmpLabel.append("(" + taction.getTransObj().getSymbol() + ")arrives;");
	    if(!(lastTaskNodes[cNum].equals(tmpTaskNode))) {
	      output.print("\t");
	      if(lastTaskNodes[cNum].equals("first")) {
		output.print("\"core " + cNum + "\"->" + tmpTaskNode);
	      } else {
		output.print(lastTaskNodes[cNum] + "->" + tmpTaskNode);
	      }
	      if(tmpisTaskFinish.contains(cNum)) {
		output.print(" [style=invis]");
	      }
	      output.println(";");
	      lastTaskNodes[cNum] = tmpTaskNode;
	    }
	    break;
	  }

	  case Action.TASKFINISH: {
	    if(!isfirst) {
	      tmpLabel.append("\\n");
	    }
	    tmpLabel.append("<" + taction.getTd().getSymbol() + "(");
	    /*Vector<Integer> taskparams = taction.getTaskParams();
	    for(int ii = 0; ii < taskparams.size(); ii++) {
		tmpLabel.append(taskparams.elementAt(ii));
		if(ii < taskparams.size() - 1) {
		    tmpLabel.append(",");
		}
	    }*/
	    tmpLabel.append(")>finishes;");
	    if(!(lastTaskNodes[cNum].equals("first"))) {
	      if(!(lastTaskNodes[cNum].equals(tmpTaskNode))) {
		output.print("\t");
		output.println(lastTaskNodes[cNum] + "->" + tmpTaskNode + ";");
		lastTaskNodes[cNum] = tmpTaskNode;
	      }
	      if(tmpisset.contains(cNum)) {
		isTaskFinish[cNum] &= true;
	      } else {
		isTaskFinish[cNum] = true;
		tmpisset.add(cNum);
	      }
	      lastTasks[cNum] = "";
	    } else {
	      throw new Exception("Error: unexpected task finish");
	    }
	    break;
	  }

	  case Action.TFWITHOBJ: {
	    if(!isfirst) {
	      tmpLabel.append("\\n");
	    }
	    tmpLabel.append("<" + taction.getTd().getSymbol() + "(");
	    /*Vector<Integer> taskparams = taction.getTaskParams();
	    for(int ii = 0; ii < taskparams.size(); ii++) {
		tmpLabel.append(taskparams.elementAt(ii));
		if(ii < taskparams.size() - 1) {
		    tmpLabel.append(",");
		}
	    }*/
	    tmpLabel.append(")>finishes;");
	    Iterator<Entry<ClassDescriptor, Integer>> it_entry = (Iterator<Entry<ClassDescriptor, Integer>>)taction.getNObjs().entrySet().iterator();
	    while(it_entry.hasNext()) {
	      Entry<ClassDescriptor, Integer> entry = it_entry.next();
	      tmpLabel.append(entry.getValue() + "(" + entry.getKey().getSymbol() + ")");
	      if(it_entry.hasNext()) {
		tmpLabel.append(",");
	      } else {
		tmpLabel.append(";");
	      }
	      entry = null;
	    }
	    it_entry = null;
	    if(!(lastTaskNodes[cNum].equals("first"))) {
	      if (!(lastTaskNodes[cNum].equals(tmpTaskNode))) {
		output.print("\t");
		output.println(lastTaskNodes[cNum] + "->" + tmpTaskNode + ";");
		lastTaskNodes[cNum] = tmpTaskNode;
	      }
	      if(tmpisset.contains(cNum)) {
		isTaskFinish[cNum] &= true;
	      } else {
		isTaskFinish[cNum] = true;
		tmpisset.add(cNum);
	      }
	      lastTasks[cNum] = "";
	    } else {
	      throw new Exception("Error: unexpected task finish");
	    }
	    break;
	  }

	  case Action.TASKSTART: {
	    if(!isfirst) {
	      tmpLabel.append("\\n");
	    }
	    tmpLabel.append("<" + taction.getTd().getSymbol() + "(");
	    /*Vector<Integer> taskparams = taction.getTaskParams();
	    for(int ii = 0; ii < taskparams.size(); ii++) {
		tmpLabel.append(taskparams.elementAt(ii));
		if(ii < taskparams.size() - 1) {
		    tmpLabel.append(",");
		}
	    }*/
	    tmpLabel.append(")>starts;");
	    lastTasks[cNum] = taction.getTd().getSymbol();

	    if (!(lastTaskNodes[cNum].equals(tmpTaskNode))) {
	      output.print("\t");
	      if(lastTaskNodes[cNum].equals("first")) {
		output.print("\"core " + cNum + "\"->" + tmpTaskNode);
	      } else {
		output.print(lastTaskNodes[cNum] + "->" + tmpTaskNode);
	      }
	      if(tmpisTaskFinish.contains(cNum)) {
		output.print(" [style=invis]");
	      }
	      output.println(";");
	      lastTaskNodes[cNum] = tmpTaskNode;
	    }
	    isTaskFinish[cNum] &= false;
	    break;
	  }

	  case Action.TASKABORT: {
	    if(!isfirst) {
	      tmpLabel.append("\\n");
	    }
	    tmpLabel.append("<" + taction.getTd().getSymbol() + "(");
	    /*Vector<Integer> taskparams = taction.getTaskParams();
	    for(int ii = 0; ii < taskparams.size(); ii++) {
		tmpLabel.append(taskparams.elementAt(ii));
		if(ii < taskparams.size() - 1) {
		    tmpLabel.append(",");
		}
	    }*/
	    tmpLabel.append(")>aborts;");
	    if(!(lastTaskNodes[cNum].equals("first")) &&
	       (tmplastTasks.get(cNum).equals(taction.getTd().getSymbol()))) {
	      if(!(lastTaskNodes[cNum].equals(tmpTaskNode))) {
		output.print("\t");
		output.println(lastTaskNodes[cNum] + "->" + tmpTaskNode + ";");
		lastTaskNodes[cNum] = tmpTaskNode;
	      }
	      if(tmpisset.contains(cNum)) {
		isTaskFinish[cNum] &= true;
	      } else {
		isTaskFinish[cNum] = true;
		tmpisset.add(cNum);
	      }
	      lastTasks[cNum] = "";
	    } else {
	      throw new Exception("Error: unexpected task aborts");
	    }
	    break;
	  }

	  case Action.TASKREMOVE: {
	    if(!isfirst) {
	      tmpLabel.append("\\n");
	    }
	    tmpLabel.append("<" + taction.getTd().getSymbol() + "(");
	    /*Vector<Integer> taskparams = taction.getTaskParams();
	    for(int ii = 0; ii < taskparams.size(); ii++) {
		tmpLabel.append(taskparams.elementAt(ii));
		if(ii < taskparams.size() - 1) {
		    tmpLabel.append(",");
		}
	    }*/
	    tmpLabel.append(")>removes;");
	    if(!(lastTaskNodes[cNum].equals("first")) &&
	       (tmplastTasks.get(cNum).equals(taction.getTd().getSymbol()))) {
	      if(!(lastTaskNodes[cNum].equals(tmpTaskNode))) {
		output.print("\t");
		output.println(lastTaskNodes[cNum] + "->" + tmpTaskNode + ";");
		lastTaskNodes[cNum] = tmpTaskNode;
	      }
	      if(tmpisset.contains(cNum)) {
		isTaskFinish[cNum] &= true;
	      } else {
		isTaskFinish[cNum] = true;
		tmpisset.add(cNum);
	      }
	      lastTasks[cNum] = "";
	    } else {
	      throw new Exception("Error: unexpected task remove");
	    }
	    break;
	  }
	  }
	}
	Enumeration<String> keys = tmpTaskNodes.keys();
	while(keys.hasMoreElements()) {
	  String tmpTaskNode = keys.nextElement();
	  output.print("\t");
	  output.println(tmpTaskNode + "[label=\"" + tmpTaskNodes.get(tmpTaskNode).toString() + "\"]");
	}
	output.print("\t");
	output.print("{rank=same; rankdir=LR; " + tnode + "; ");
	keys = tmpTaskNodes.keys();
	while(keys.hasMoreElements()) {
	  String tmpTaskNode = keys.nextElement();
	  output.print(tmpTaskNode);
	  output.print("; ");
	}
	keys = null;
	output.println("}");
	output.print("\t");
	tmplastTasks = null;
	tmpisTaskFinish = null;
	tmpisset = null;
	actions = null;
	tmpTaskNodes = null;
      }
      output.print("\t");
      output.print("\t");
      int prev = Integer.parseInt(timeNodes.elementAt(0));
      int next = 0;
      int max = 0;
      int max2 = 0;
      for(j = 1; j < timeNodes.size(); j++) {
	next = Integer.parseInt(timeNodes.elementAt(j));
	int delta = next - prev;
	if(max < delta) {
	  max2 = max;
	  max = delta;
	} else if((max != delta) && (max2 < delta)) {
	  max2 = delta;
	}
	prev = next;
      }
      if(max2 == 0) {
	max2 = 1;
      } else if(max/max2 > 100) {
	max2 = max/100;
      }
      output.println("\"Time\"->" + timeNodes.elementAt(0) + "[style=invis];");
      prev = Integer.parseInt(timeNodes.elementAt(0));
      next = 0;
      for(j = 1; j < timeNodes.size(); j++) {
	next = Integer.parseInt(timeNodes.elementAt(j));
	if(next - prev > max2) {
	  do {
	    output.print(prev + "->");
	    prev += max2;
	  } while(next - prev > max2);
	  output.println(next + ";");
	} else {
	  output.println("{rank=same; rankdir=LR; " + prev + "; " + next + "}");
	  output.println(prev + "->" + next + "[style=invis];");
	}
	prev = next;
      }

      /*for(j = 0; j < time; j++) {
         output.print(j + "->");
         }
         output.println(timeNodes.lastElement() + ";");*/
      output.println("}");
      output.close();
      timeNodes = null;
      lastTaskNodes = null;
      lastTasks = null;
      isTaskFinish = null;
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }
  
  public static void printCriticalPath(String path,
	                               Vector<SimExecutionEdge> criticalPath) {
      try {
	  File file=new File(path);
	  FileOutputStream dotstream=new FileOutputStream(file,false);
	  PrintWriter output = new java.io.PrintWriter(dotstream, true);
	  output.println("digraph simulation{");
	  output.print("\t");
	  output.println("node [shape=plaintext];");
	  output.print("\t");
	  output.println("edge [dir=none];");
	  output.print("\t");
	  output.println("ranksep=.05;");
	  output.println();
	  output.print("\t");
	  Vector<SimExecutionNode> nodes = new Vector<SimExecutionNode>();
	  String label = "";
	  String dotnodeparams="";

	  for(int i = 0; i < criticalPath.size(); i++) {
	      SimExecutionEdge seedge = criticalPath.elementAt(i);
	      SimExecutionNode startnode = (SimExecutionNode)seedge.getSource();
	      SimExecutionNode endnode = (SimExecutionNode)seedge.getTarget();
	      if(!nodes.contains(startnode)) {
		  label = startnode.getCoreNum() + ":" + startnode.getTimepoint();
		  output.println("\t" + startnode.getLabel() + " [label=\"" 
			         + label + "\" ];");
	      }
	      if(!nodes.contains(endnode)) {
		  label = endnode.getCoreNum() + ":" + endnode.getTimepoint();
		  output.println("\t" + endnode.getLabel() + " [label=\"" 
			         + label + "\" ];");
	      }
	      output.println("\t" + startnode.getLabel() + " -> " + endnode.getLabel() 
		             + " [" + "label=\"" + seedge.getLabel() + "\"];");
	  }
	  output.println("}");
	  output.close();
	  nodes = null;
      } catch (Exception e) {
	  e.printStackTrace();
	  System.exit(-1);
      }
  }
}