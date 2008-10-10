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
import IR.Tree.FlagExpressionNode;
import IR.Tree.FlagNode;
import IR.Tree.FlagOpNode;
import Util.Edge;
import Util.GraphNode;
import Util.Namer;

public class SchedulingUtil {

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

  public static boolean isTaskTrigger_flag(FlagExpressionNode fen,FlagState fs) {
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

  public static void printScheduleGraph(String path, Vector<ScheduleNode> sNodes) {
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

  private static void traverseSNodes(PrintWriter output, Vector<ScheduleNode> sNodes) {
    //Draw clusters representing ScheduleNodes
    Iterator it = sNodes.iterator();
    while (it.hasNext()) {
      ScheduleNode gn = (ScheduleNode) it.next();
      Iterator edges = gn.edges();
      output.println("\tsubgraph " + gn.getLabel() + "{");
      output.println("\t\tlabel=\"" + gn.getTextLabel() + "\";");
      Iterator it_cnodes = gn.getClassNodesIterator();
      traverseCNodes(output, it_cnodes);
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
    }
  }

  private static void traverseCNodes(PrintWriter output, Iterator it) {
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

  private static void traverseFlagStates(PrintWriter output, Collection nodes) {
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
	    for(Iterator nodeit=nonmerge(node, nodes).iterator(); nodeit.hasNext();) {
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
	      }
	      output.println("\t" + gn.getLabel() + " -> " + node2.getLabel() + " [" + "label=\"" + edgelabel + "\"" + edgedotnodeparams + "];");
	    }
	  }
	}
    }
  }

  private static Set nonmerge(GraphNode gn, Collection nodes) {
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
      }
    }
    return newset;
  }

  public static void printSimulationResult(String path, int time, int coreNum, Vector<CheckPoint> checkpoints) {
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
	  if(!tmplastTasks.contains(cNum)) {
	    tmplastTasks.put(cNum, lastTasks[cNum]);
	  }
	  if(!(tmpisset.contains(cNum)) && (isTaskFinish[cNum]) && !(tmpisTaskFinish.contains(cNum))) {
	    tmpisTaskFinish.add(cNum);             // records those with task finished the first time visit it
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
	    tmpLabel.append("<" + taction.getTd().getSymbol() + ">finishes;");
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
	    tmpLabel.append("<" + taction.getTd().getSymbol() + ">finishes; ");
	    Iterator<Entry<ClassDescriptor, Integer>> it_entry = (Iterator<Entry<ClassDescriptor, Integer>>)taction.getNObjs().entrySet().iterator();
	    while(it_entry.hasNext()) {
	      Entry<ClassDescriptor, Integer> entry = it_entry.next();
	      tmpLabel.append(entry.getValue() + "(" + entry.getKey().getSymbol() + ")");
	      if(it_entry.hasNext()) {
		tmpLabel.append(",");
	      } else {
		tmpLabel.append(";");
	      }
	    }
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
	    tmpLabel.append("<" + taction.getTd().getSymbol() + ">starts;");
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
	    tmpLabel.append("<" + taction.getTd().getSymbol() + ">aborts;");
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
	    tmpLabel.append("<" + taction.getTd().getSymbol() + ">removes;");
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
	output.println("}");
	output.print("\t");
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
	      }while(next - prev > max2);
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
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }
}