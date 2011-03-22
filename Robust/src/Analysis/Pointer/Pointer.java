package Analysis.Pointer;
import java.util.*;
import IR.Flat.*;
import IR.*;
import Analysis.Liveness;
import Analysis.Pointer.BasicBlock.BBlock;
import Analysis.Pointer.AllocFactory.AllocNode;
import Analysis.Disjoint.Alloc;
import Analysis.Disjoint.Taint;
import Analysis.Disjoint.TaintSet;
import Analysis.Disjoint.Canonical;
import Analysis.Disjoint.HeapAnalysis;
import Analysis.CallGraph.CallGraph;
import Analysis.OoOJava.RBlockRelationAnalysis;
import Analysis.OoOJava.Accessible;
import Analysis.Disjoint.ExistPred;
import Analysis.Disjoint.ReachGraph;
import Analysis.Disjoint.EffectsAnalysis;
import Analysis.Disjoint.BuildStateMachines;
import java.io.*;


public class Pointer implements HeapAnalysis{
  HashMap<FlatMethod, BasicBlock> blockMap;
  HashMap<BBlock, Graph> bbgraphMap;
  HashMap<FlatNode, Graph> graphMap;
  HashMap<FlatCall, Set<BBlock>> callMap;
  HashMap<BBlock, Set<PPoint>> returnMap;
  HashMap<BBlock, Set<TempDescriptor>> bblivetemps;

  private boolean OoOJava=false;
  CallGraph callGraph;
  State state;
  TypeUtil typeUtil;
  AllocFactory allocFactory;
  LinkedList<Delta> toprocess;
  TempDescriptor returntmp;
  RBlockRelationAnalysis taskAnalysis;
  EffectsAnalysis effectsAnalysis;
  Accessible accessible;

  public Pointer(State state, TypeUtil typeUtil, CallGraph callGraph, RBlockRelationAnalysis taskAnalysis, Liveness liveness) {
    this(state, typeUtil);
    this.callGraph=callGraph;
    this.OoOJava=true;
    this.taskAnalysis=taskAnalysis;
    this.effectsAnalysis=new EffectsAnalysis();
    effectsAnalysis.state=state;
    effectsAnalysis.buildStateMachines=new BuildStateMachines();
    accessible=new Accessible(state, callGraph, taskAnalysis, liveness);
    accessible.doAnalysis();
    State.logEvent("Done Writing Accessible Analysis");
  }

  public Pointer(State state, TypeUtil typeUtil) {
    this.state=state;
    this.blockMap=new HashMap<FlatMethod, BasicBlock>();
    this.bbgraphMap=new HashMap<BBlock, Graph>();
    this.bblivetemps=new HashMap<BBlock, Set<TempDescriptor>>();
    this.graphMap=new HashMap<FlatNode, Graph>();
    this.callMap=new HashMap<FlatCall, Set<BBlock>>();
    this.returnMap=new HashMap<BBlock, Set<PPoint>>();
    this.typeUtil=typeUtil;
    this.allocFactory=new AllocFactory(state, typeUtil);
    this.toprocess=new LinkedList<Delta>();
    ClassDescriptor stringcd=typeUtil.getClass(TypeUtil.ObjectClass);
    this.returntmp=new TempDescriptor("RETURNVAL", stringcd);
  }

  public EffectsAnalysis getEffectsAnalysis() {
    return effectsAnalysis;
  }

  public BasicBlock getBBlock(FlatMethod fm) {
    if (!blockMap.containsKey(fm)) {
      blockMap.put(fm, BasicBlock.getBBlock(fm));
      Hashtable<FlatNode, Set<TempDescriptor>> livemap=Liveness.computeLiveTemps(fm);
      for(BBlock bblock:blockMap.get(fm).getBlocks()) {
	FlatNode fn=bblock.nodes.get(0);
	if (fn==fm) {
	  HashSet<TempDescriptor> fmset=new HashSet<TempDescriptor>();
	  fmset.addAll((List<TempDescriptor>)Arrays.asList(fm.writesTemps()));
	  bblivetemps.put(bblock, fmset);
	} else {
	  Set<TempDescriptor> livetemps=livemap.get(fn);
	  bblivetemps.put(bblock, livetemps);
	  livetemps.add(returntmp);
	}
      }
    }
    return blockMap.get(fm);
  }
  
  Delta buildInitialContext() {
    MethodDescriptor md=typeUtil.getMain();
    FlatMethod fm=state.getMethodFlat(md);
    BasicBlock bb=getBBlock(fm);
    BBlock start=bb.getStart();
    Delta delta=new Delta(new PPoint(start), true);
    MySet<Edge> arrayset=new MySet<Edge>();
    MySet<Edge> varset=new MySet<Edge>();
    Edge arrayedge=new Edge(allocFactory.StringArray, null, allocFactory.Strings);
    Edge stringedge=new Edge(fm.getParameter(0), allocFactory.StringArray);
    delta.addHeapEdge(arrayedge);
    delta.addVarEdge(stringedge);

    return delta;
  }


  public Graph getGraph(FlatNode fn) {
    return graphMap.get(fn);
  }

  public void doAnalysis() {

    toprocess.add(buildInitialContext());
    nextdelta:
    while(!toprocess.isEmpty()) {
      Delta delta=toprocess.remove();
      PPoint ppoint=delta.getBlock();
      BBlock bblock=ppoint.getBBlock();
      Vector<FlatNode> nodes=bblock.nodes();
      int startindex=0;

      if (ppoint.getIndex()==-1) {
	//Build base graph for entrance to this basic block
	//System.out.println("Processing "+bblock.nodes.get(0).toString().replace(' ','_'));
	//delta.print();
	delta=applyInitDelta(delta, bblock);
	//System.out.println("Generating:");
	//delta.print();
      } else {
	//System.out.println("Processing Call "+bblock.nodes.get(ppoint.getIndex()).toString().replace(' ','_'));
	//delta.print();

	startindex=ppoint.getIndex()+1;
	delta=applyCallDelta(delta, bblock);
	//System.out.println("Generating:");
	//delta.print();
      }
      Graph graph=bbgraphMap.get(bblock);
      Graph nodeGraph=null;
      boolean init=delta.getInit();
      if (!init&&delta.isEmpty())
	continue nextdelta;
      
      //Compute delta at exit of each node
      for(int i=startindex; i<nodes.size();i++) {
	FlatNode currNode=nodes.get(i);
	//System.out.println("Start Processing "+currNode);
	if (!graphMap.containsKey(currNode)) {
	  if (isNEEDED(currNode))
	    graphMap.put(currNode, new Graph(graph));
	  else {
	    if (i==0) {
	      //base graph works for us
	      graphMap.put(currNode, new Graph(graph));
	    } else {
	      //just use previous graph
	      graphMap.put(currNode, graphMap.get(nodes.get(i-1)));
	    }
	  }
	}
	nodeGraph=graphMap.get(currNode);
	delta=processNode(bblock, i, currNode, delta, nodeGraph);
	//System.out.println("Processing "+currNode+" and generating delta:");
	//delta.print();
      }
      generateFinalDelta(bblock, delta, nodeGraph);
    }

    //DEBUG
    if (false) {
      int debugindex=0;
      for(Map.Entry<BBlock, Graph> e:bbgraphMap.entrySet()) {
	Graph g=e.getValue();
	plotGraph(g,"BB"+e.getKey().nodes.get(0).toString().replace(' ','_'));
	debugindex++;
      }
      
      for(FlatMethod fm:blockMap.keySet()) {
	System.out.println(fm.printMethod());
      }
      for(Map.Entry<FlatNode, Graph> e:graphMap.entrySet()) {
	FlatNode fn=e.getKey();
	Graph g=e.getValue();
	plotGraph(g,"FN"+fn.toString()+debugindex);
	debugindex++;
      } 
    }

    State.logEvent("Done With Pointer Analysis");


    if (OoOJava) {
      effectsAnalysis.buildStateMachines.writeStateMachines();
      State.logEvent("Done Writing State Machines");
    }
  }

  void plotGraph(Graph g, String name) {
    try {
      PrintWriter pw=new PrintWriter(new FileWriter(name.toString().replace(' ','_')+".dot"));
      g.printGraph(pw, name);
      pw.close();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
  

  /* This function builds the last delta for a basic block.  It
   * handles the case for the first time the basic block is
   * evaluated.*/

  void buildInitDelta(Graph graph, Delta newDelta) {
    //First compute the set of temps
    HashSet<TempDescriptor> tmpSet=new HashSet<TempDescriptor>();
    tmpSet.addAll(graph.varMap.keySet());
    tmpSet.addAll(graph.parent.varMap.keySet());
    
    //Next build the temp map part of the delta
    for(TempDescriptor tmp:tmpSet) {
      MySet<Edge> edgeSet=new MySet<Edge>();
      /* Get target set */
      if (graph.varMap.containsKey(tmp))
	edgeSet.addAll(graph.varMap.get(tmp));
      else
	edgeSet.addAll(graph.parent.varMap.get(tmp));
      newDelta.varedgeadd.put(tmp, edgeSet);
    }
    
    //Next compute the set of src allocnodes
    HashSet<AllocNode> nodeSet=new HashSet<AllocNode>();
    nodeSet.addAll(graph.nodeMap.keySet());
    nodeSet.addAll(graph.parent.nodeMap.keySet());
    
    for(AllocNode node:nodeSet) {
      MySet<Edge> edgeSet=new MySet<Edge>();
      /* Get edge set */
      if (graph.nodeMap.containsKey(node))
	edgeSet.addAll(graph.nodeMap.get(node));
      else
	edgeSet.addAll(graph.parent.nodeMap.get(node));
      newDelta.heapedgeadd.put(node, edgeSet);
      
      /* Compute ages */
      if (graph.oldNodes.containsKey(node)) {

      } else if (graph.parent.oldNodes.containsKey(node)) {
	//parent graphs only contain true...no need to check
	newDelta.addOldNodes.put(node, Boolean.TRUE);
      }
    }
    
    for(AllocNode node:graph.oldNodes.keySet()) {
      if (graph.oldNodes.get(node).booleanValue())
	newDelta.addOldNodes.put(node, Boolean.TRUE);
    }

    for(AllocNode node:graph.parent.oldNodes.keySet()) {
      //make sure child doesn't override
      if (!graph.oldNodes.containsKey(node))
	newDelta.addOldNodes.put(node, Boolean.TRUE);
    }

    newDelta.addNodeAges.addAll(graph.nodeAges);
    newDelta.addNodeAges.addAll(graph.parent.nodeAges);
  }

  /* This function build the delta for the exit of a basic block. */

  void generateFinalDelta(BBlock bblock, Delta delta, Graph graph) {
    Delta newDelta=new Delta(null, false);
    if (delta.getInit()) {
      buildInitDelta(graph, newDelta);
    } else {
      /* We can break the old delta...it is done being used */
      /* First we will build variable edges */
      HashSet<TempDescriptor> tmpSet=new HashSet<TempDescriptor>();
      tmpSet.addAll(delta.basevaredge.keySet());
      tmpSet.addAll(delta.varedgeadd.keySet());
      for(TempDescriptor tmp:tmpSet) {
	/* Start with the new incoming edges */
	MySet<Edge> newbaseedge=delta.basevaredge.get(tmp);
	/* Remove the remove set */
	if (newbaseedge==null)
	  newbaseedge=new MySet<Edge>();
	newbaseedge.removeAll(delta.varedgeremove.get(tmp));
	/* Add in the new set*/
	newbaseedge.addAll(delta.varedgeadd.get(tmp));
	/* Store the results */
	newDelta.varedgeadd.put(tmp, newbaseedge);
      }
      delta.basevaredge.clear();

      /* Next we build heap edges */
      HashSet<AllocNode> nodeSet=new HashSet<AllocNode>();
      nodeSet.addAll(delta.baseheapedge.keySet());
      nodeSet.addAll(delta.heapedgeadd.keySet());
      nodeSet.addAll(delta.heapedgeremove.keySet());
      for(AllocNode node:nodeSet) {
	/* Start with the new incoming edges */
	MySet<Edge> newheapedge=new MySet<Edge>(delta.baseheapedge.get(node));
	/* Remove the remove set */
	MySet<Edge> removeset=delta.heapedgeremove.get(node);

	if (removeset!=null)
	  newheapedge.removeAll(removeset);

	/* Add in the add set */
	MySet<Edge> settoadd=delta.heapedgeadd.get(node);
	if (settoadd!=null)
	  newheapedge.addAll(settoadd);
	newDelta.heapedgeadd.put(node, newheapedge);

	/* Remove the newly created edges..no need to propagate a diff for those */
	if (removeset!=null) {
	  removeset.removeAll(delta.baseheapedge.get(node));
	  newDelta.heapedgeremove.put(node, removeset);
	}
      }

      /* Compute new ages */
      newDelta.addNodeAges.addAll(delta.baseNodeAges);
      newDelta.addNodeAges.addAll(delta.addNodeAges);
      HashSet<AllocNode> oldNodes=new HashSet<AllocNode>();

      /* Compute whether old nodes survive */
      oldNodes.addAll(delta.baseOldNodes.keySet());
      oldNodes.addAll(delta.addOldNodes.keySet());
      for(AllocNode node:oldNodes) {
	if (delta.addOldNodes.containsKey(node)) {
	  if (delta.addOldNodes.get(node).booleanValue()) {
	    newDelta.addOldNodes.put(node, Boolean.TRUE);
	  }
	} else {
	  if (delta.baseOldNodes.get(node).booleanValue()) {
	    newDelta.addOldNodes.put(node, Boolean.TRUE);
	  }
	}
      }
    }

    /* Now we need to propagate newdelta */
    if (!newDelta.heapedgeadd.isEmpty()||!newDelta.heapedgeremove.isEmpty()||!newDelta.varedgeadd.isEmpty()||!newDelta.addNodeAges.isEmpty()||!newDelta.addOldNodes.isEmpty()) {
      /* We have a delta to propagate */
      if (returnMap.containsKey(bblock)) {
	//exit of call block
	boolean first=true;

	for(PPoint caller:returnMap.get(bblock)) {
	  //System.out.println("Sending Return BBlock to "+caller.getBBlock().nodes.get(caller.getIndex()).toString().replace(' ','_'));
	  //newDelta.print();
	  if (first) {
	    newDelta.setBlock(caller);
	    toprocess.add(newDelta);
	    first=false;
	  } else {
	    Delta d=newDelta.diffBlock(caller);
	    toprocess.add(d);
	  }
	}
      } else {
	//normal block
	Vector<BBlock> blockvector=bblock.next();
	for(int i=0;i<blockvector.size();i++) {
	  //System.out.println("Sending BBlock to "+blockvector.get(i).nodes.get(0).toString().replace(' ','_'));
	  //newDelta.print();
	  if (i==0) {
	    newDelta.setBlock(new PPoint(blockvector.get(i)));
	    toprocess.add(newDelta);
	  } else {
	    Delta d=newDelta.diffBlock(new PPoint(blockvector.get(i)));
	    toprocess.add(d);
	  }
	}
      }
    } else {
      //System.out.println("EMPTY DELTA");
      //System.out.println("delta");
      //delta.print();
      //System.out.println("newDelta");
      //newDelta.print();
    }
  }

  boolean isNEEDED(FlatNode node) {
    switch(node.kind()) {
    case FKind.FlatSetFieldNode: {
      FlatSetFieldNode n=(FlatSetFieldNode)node;
      return n.getSrc().getType().isPtr();
    }
    case FKind.FlatSetElementNode: {
      FlatSetElementNode n=(FlatSetElementNode)node;
      return n.getSrc().getType().isPtr();
    }
    case FKind.FlatFieldNode: {
      FlatFieldNode n=(FlatFieldNode)node;
      return n.getDst().getType().isPtr();
    }
    case FKind.FlatElementNode: {
      FlatElementNode n=(FlatElementNode)node;
      return n.getDst().getType().isPtr();
    }
    }
    return true;
  }

  Delta processNode(BBlock bblock, int index, FlatNode node, Delta delta, Graph newgraph) {
    switch(node.kind()) {
    case FKind.FlatNew:
      return processNewNode((FlatNew)node, delta, newgraph);
    case FKind.FlatFieldNode:
    case FKind.FlatElementNode:
      return processFieldElementNode(node, delta, newgraph);
    case FKind.FlatCastNode:
    case FKind.FlatOpNode:
    case FKind.FlatReturnNode:
      return processCopyNode(node, delta, newgraph);
    case FKind.FlatSetFieldNode:
    case FKind.FlatSetElementNode:
      return processSetFieldElementNode(node, delta, newgraph);
    case FKind.FlatSESEEnterNode:
      return processSESEEnterNode((FlatSESEEnterNode) node, delta, newgraph);
    case FKind.FlatSESEExitNode:
      return processSESEExitNode((FlatSESEExitNode) node, delta, newgraph);
    case FKind.FlatMethod:
    case FKind.FlatExit:
    case FKind.FlatBackEdge:
    case FKind.FlatGenReachNode:
      return processFlatNop(node, delta, newgraph);
    case FKind.FlatCall:
      return processFlatCall(bblock, index, (FlatCall) node, delta, newgraph);
    default:
      throw new Error("Unrecognized node:"+node);
    }
  }

  Delta processSESEEnterNode(FlatSESEEnterNode sese, Delta delta, Graph graph) {
    if (!OoOJava)
      return processFlatNop(sese, delta, graph);
    if (delta.getInit()) {
      removeInitTaints(null, delta, graph);
      for (TempDescriptor tmp:sese.getInVarSet()) {
	Taint taint=Taint.factory(sese,  null, tmp, AllocFactory.dummySite, sese, ReachGraph.predsEmpty);
	MySet<Edge> edges=GraphManip.getEdges(graph, delta, tmp);
	for(Edge e:edges) {
	  Edge newe=e.addTaint(taint);
	  delta.addVarEdge(newe);
	}
      }
    } else {
      removeDiffTaints(null, delta);
      for (TempDescriptor tmp:sese.getInVarSet()) {
	Taint taint=Taint.factory(sese,  null, tmp, AllocFactory.dummySite, sese, ReachGraph.predsEmpty);
	MySet<Edge> edges=GraphManip.getDiffEdges(delta, tmp);
	for(Edge e:edges) {
	  Edge newe=e.addTaint(taint);
	  delta.addVarEdge(newe);
	}
      }
    }


    applyDiffs(graph, delta);
    return delta;
  }
  
  private boolean isRecursive(FlatSESEEnterNode sese) {
    MethodDescriptor md=sese.getmdEnclosing();
    boolean isrecursive=callGraph.getCalleeSet(md).contains(md);
    return isrecursive;
  }

  Delta processSESEExitNode(FlatSESEExitNode seseexit, Delta delta, Graph graph) {
    if (!OoOJava)
      return processFlatNop(seseexit, delta, graph);
    FlatSESEEnterNode sese=seseexit.getFlatEnter();
    //Strip Taints from this SESE
    if (delta.getInit()) {
      removeInitTaints(isRecursive(sese)?null:sese, delta, graph);
    } else {
      removeDiffTaints(isRecursive(sese)?null:sese, delta);
    }
    applyDiffs(graph, delta);
    return delta;
  }
  
  void removeDiffTaints(FlatSESEEnterNode sese, Delta delta) {
    //Start with variable edges
    {
      MySet<Edge> edgestoadd=new MySet<Edge>();
      MySet<Edge> edgestoremove=new MySet<Edge>();
      
      //Process base diff edges
      processEdgeMap(sese, delta.basevaredge, null, delta.varedgeremove, edgestoremove, edgestoadd); 
      //Process delta edges
      processEdgeMap(sese, delta.varedgeadd, null, null, edgestoremove, edgestoadd); 
      for(Edge e:edgestoremove) {
	delta.removeVarEdge(e);
      }
      for(Edge e:edgestoadd) {
	delta.addVarEdge(e);
      }
    }

    //Now do heap edges
    {
      MySet<Edge> edgestoadd=new MySet<Edge>();
      MySet<Edge> edgestoremove=new MySet<Edge>();

      //Process base diff edges
      processEdgeMap(sese, delta.baseheapedge, null, delta.heapedgeremove, edgestoremove, edgestoadd); 
      //Process delta edges
      processEdgeMap(sese, delta.heapedgeadd, null, null, edgestoremove, edgestoadd); 
      for(Edge e:edgestoremove) {
	delta.removeHeapEdge(e);
      }
      for(Edge e:edgestoadd) {
	delta.addHeapEdge(e);
      }
    }
  }

  void removeInitTaints(FlatSESEEnterNode sese, Delta delta, Graph graph) {
    //Start with variable edges
    {
      MySet<Edge> edgestoadd=new MySet<Edge>();
      MySet<Edge> edgestoremove=new MySet<Edge>();
      
      //Process parent edges
      processEdgeMap(sese, graph.parent.varMap, graph.varMap, delta.varedgeremove, edgestoremove, edgestoadd);
      //Process graph edges
      processEdgeMap(sese, graph.varMap, null, delta.varedgeremove, edgestoremove, edgestoadd); 
      //Process delta edges
      processEdgeMap(sese, delta.varedgeadd, null, null, edgestoremove, edgestoadd); 
      for(Edge e:edgestoremove) {
	delta.removeVarEdge(e);
      }
      for(Edge e:edgestoadd) {
	delta.addVarEdge(e);
      }
    }

    //Now do heap edges
    {
      MySet<Edge> edgestoadd=new MySet<Edge>();
      MySet<Edge> edgestoremove=new MySet<Edge>();

      //Process parent edges
      processEdgeMap(sese, graph.parent.nodeMap, graph.nodeMap, delta.heapedgeremove, edgestoremove, edgestoadd);
      //Process graph edges
      processEdgeMap(sese, graph.nodeMap, null, delta.heapedgeremove, edgestoremove, edgestoadd); 
      //Process delta edges
      processEdgeMap(sese, delta.heapedgeadd, null, null, edgestoremove, edgestoadd); 
      for(Edge e:edgestoremove) {
	delta.removeHeapEdge(e);
      }
      for(Edge e:edgestoadd) {
	delta.addHeapEdge(e);
      }
    }
  }

  void processEdgeMap(FlatSESEEnterNode sese, HashMap<?, MySet<Edge>> edgemap, HashMap<?, MySet<Edge>> childmap, HashMap<?, MySet<Edge>> removemap, MySet<Edge> edgestoremove, MySet<Edge> edgestoadd) {
    for(Map.Entry<?, MySet<Edge>> entry:edgemap.entrySet()) {
      //If the parent map exists and overrides this entry, skip it
      if (childmap!=null&&childmap.containsKey(entry.getKey()))
	continue;
      for(Edge e:entry.getValue()) {
	//check whether this edge has been removed
	if (removemap!=null&&removemap.containsKey(entry.getKey())&&
	    removemap.get(entry.getKey()).contains(e))
	  continue;
	//have real edge
	TaintSet ts=e.getTaints();
	TaintSet newts=null;
	//update non-null taint set
	if (ts!=null)
	  newts=Canonical.removeInContextTaintsNP(ts, sese);
	if (newts!=null) {
	  edgestoremove.add(e);
	  edgestoadd.add(e.changeTaintSet(newts));
	}
      }
    }
  }

  /* This function compute the edges for the this variable for a
   * callee if it exists. */

  void processThisTargets(HashSet<ClassDescriptor> targetSet, Graph graph, Delta delta, Delta newDelta, HashSet<AllocNode> nodeset, Stack<AllocNode> tovisit, MySet<Edge> edgeset, TempDescriptor tmpthis, HashSet<AllocNode> oldnodeset) {
    //Handle the this temp
    if (tmpthis!=null) {
      MySet<Edge> edges=(oldnodeset!=null)?GraphManip.getDiffEdges(delta, tmpthis):GraphManip.getEdges(graph, delta, tmpthis);
      newDelta.varedgeadd.put(tmpthis, (MySet<Edge>) edges.clone());
      edgeset.addAll(edges);
      for(Edge e:edges) {
	AllocNode dstnode=e.dst;
	if (!nodeset.contains(dstnode)&&(oldnodeset==null||!oldnodeset.contains(dstnode))) {
	  TypeDescriptor type=dstnode.getType();
	  if (!type.isArray()) {
	    targetSet.add(type.getClassDesc());
	  } else {
	    //arrays don't have code
	    targetSet.add(typeUtil.getClass(TypeUtil.ObjectClass));
	  }
	  nodeset.add(dstnode);
	  tovisit.add(dstnode);
	}
      }
    }
  }

  /* This function compute the edges for a call's parameters. */

  void processParams(Graph graph, Delta delta, Delta newDelta, HashSet<AllocNode> nodeset, Stack<AllocNode> tovisit, MySet<Edge> edgeset, FlatCall fcall, boolean diff) {
    //Go through each temp
    for(int i=0;i<fcall.numArgs();i++) {
      TempDescriptor tmp=fcall.getArg(i);
      MySet<Edge> edges=diff?GraphManip.getDiffEdges(delta, tmp):GraphManip.getEdges(graph, delta, tmp);
      newDelta.varedgeadd.put(tmp, (MySet<Edge>) edges.clone());
      edgeset.addAll(edges);
      for(Edge e:edges) {
	if (!nodeset.contains(e.dst)) {
	  nodeset.add(e.dst);
	  tovisit.add(e.dst);
	}
      }
    }
  }

  /* This function computes the reachable nodes for a callee. */

  void computeReachableNodes(Graph graph, Delta delta, Delta newDelta, HashSet<AllocNode> nodeset, Stack<AllocNode> tovisit, MySet<Edge> edgeset, HashSet<AllocNode> oldnodeset) {
      while(!tovisit.isEmpty()) {
	AllocNode node=tovisit.pop();
	MySet<Edge> edges=GraphManip.getEdges(graph, delta, node);
	if (!edges.isEmpty()) {
	  newDelta.heapedgeadd.put(node, edges);
	  edgeset.addAll(edges);
	  for(Edge e:edges) {
	    if (!nodeset.contains(e.dst)&&(oldnodeset==null||!oldnodeset.contains(e.dst))) {
	      nodeset.add(e.dst);
	      tovisit.add(e.dst);
	    }
	  }
	}
      }
  }

  HashSet<MethodDescriptor> computeTargets(FlatCall fcall, Delta newDelta) {
    TempDescriptor tmpthis=fcall.getThis();
    MethodDescriptor md=fcall.getMethod();
    HashSet<MethodDescriptor> targets=new HashSet<MethodDescriptor>();
    if (md.isStatic()) {
      targets.add(md);
    } else {
      //Compute Edges
      for(Edge e:newDelta.varedgeadd.get(tmpthis)) {
	AllocNode node=e.dst;
	ClassDescriptor cd=node.getType().getClassDesc();
	//Figure out exact method called and add to set
	MethodDescriptor calledmd=cd.getCalledMethod(md);
	targets.add(calledmd);
      }
    }
    return targets;
  }

  void fixMapping(FlatCall fcall, HashSet<MethodDescriptor> targets, MySet<Edge> oldedgeset, Delta newDelta, BBlock callblock, int callindex) {
    Delta basedelta=null;
    TempDescriptor tmpthis=fcall.getThis();

    for(MethodDescriptor calledmd:targets) {
      FlatMethod fm=state.getMethodFlat(calledmd);
      boolean newmethod=false;
      
      //Build tmpMap
      HashMap<TempDescriptor, TempDescriptor> tmpMap=new HashMap<TempDescriptor, TempDescriptor>();
      int offset=0;
      if(tmpthis!=null) {
	tmpMap.put(tmpthis, fm.getParameter(offset++));
      }
      for(int i=0;i<fcall.numArgs();i++) {
	TempDescriptor tmp=fcall.getArg(i);
	tmpMap.put(tmp,fm.getParameter(i+offset));
      }

      //Get basicblock for the method
      BasicBlock block=getBBlock(fm);
      
      //Hook up exits
      if (!callMap.containsKey(fcall)) {
	callMap.put(fcall, new HashSet<BBlock>());
      }
      
      Delta returnDelta=null;
      if (!callMap.get(fcall).contains(block.getStart())) {
	callMap.get(fcall).add(block.getStart());
	newmethod=true;
	
	//Hook up return
	if (!returnMap.containsKey(block.getExit())) {
	  returnMap.put(block.getExit(), new HashSet<PPoint>());
	}
	returnMap.get(block.getExit()).add(new PPoint(callblock, callindex));
	
	if (bbgraphMap.containsKey(block.getExit())) {
	  //Need to push existing results to current node
	  if (returnDelta==null) {
	    returnDelta=new Delta(null, false);
	    Vector<FlatNode> exitblocknodes=block.getExit().nodes();
	    FlatExit fexit=(FlatExit)exitblocknodes.get(exitblocknodes.size()-1);
	    buildInitDelta(graphMap.get(fexit), returnDelta);
	    if (!returnDelta.heapedgeadd.isEmpty()||!returnDelta.heapedgeremove.isEmpty()||!returnDelta.varedgeadd.isEmpty()) {
	      returnDelta.setBlock(new PPoint(callblock, callindex));
	      toprocess.add(returnDelta);
	    }
	  } else {
	    if (!returnDelta.heapedgeadd.isEmpty()||!returnDelta.heapedgeremove.isEmpty()||!returnDelta.varedgeadd.isEmpty()) {
	      toprocess.add(returnDelta.diffBlock(new PPoint(callblock, callindex)));
	    }
	  }
	}
      }
      
      if (oldedgeset==null) {
	//First build of this graph
	//Build and enqueue delta...safe to just use existing delta
	Delta d=newDelta.changeParams(tmpMap, new PPoint(block.getStart()));
	//System.out.println("AProcessing "+block.getStart().nodes.get(0).toString().replace(' ','_'));
	//d.print();
	toprocess.add(d);
      } else if (newmethod) {
	if (basedelta==null) {
	  basedelta=newDelta.buildBase(oldedgeset);
	}
	//Build and enqueue delta
	Delta d=basedelta.changeParams(tmpMap, new PPoint(block.getStart()));
	//System.out.println("BProcessing "+block.getStart().nodes.get(0).toString().replace(' ','_'));
	//d.print();
	toprocess.add(d);
      } else  {
	//Build and enqueue delta
	Delta d=newDelta.changeParams(tmpMap, new PPoint(block.getStart()));
	//System.out.println("CProcessing "+block.getStart().nodes.get(0).toString().replace(' ','_'));
	//d.print();
	toprocess.add(d);
      }
    }
  }


  /* This function computes all edges that start outside of the callee
   * context and go into the callee context */

  void computeExternalEdges(Graph graph, Delta delta, HashSet<AllocNode> nodeset, HashSet<AllocNode> deltaset, MySet<Edge> externaledgeset) {
    //Do heap edges first
    HashSet<AllocNode> externalnodes=new HashSet<AllocNode>();
    externalnodes.addAll(delta.baseheapedge.keySet());
    externalnodes.addAll(delta.heapedgeadd.keySet());
    externalnodes.addAll(delta.heapedgeremove.keySet());
    //remove allinternal nodes
    externalnodes.removeAll(nodeset);
    for(AllocNode extNode:externalnodes) {
      //Compute set of edges from given node
      MySet<Edge> edges=new MySet<Edge>(delta.baseheapedge.get(extNode));
      edges.removeAll(delta.heapedgeremove.get(extNode));
      edges.addAll(delta.heapedgeadd.get(extNode));
      
      for(Edge e:edges) {
	if (nodeset.contains(e.dst))
	  externaledgeset.add(e);
      }
    }

    //Do var edges now
    HashSet<TempDescriptor> temps=new HashSet<TempDescriptor>();
    temps.addAll(delta.basevaredge.keySet());
    temps.addAll(delta.varedgeadd.keySet());
    temps.addAll(delta.varedgeremove.keySet());
    //remove allinternal nodes
    temps.removeAll(nodeset);
    
    for(TempDescriptor tmp:temps) {
      //Compute set of edges from given node
      MySet<Edge> edges=new MySet<Edge>(delta.basevaredge.get(tmp));
      
      edges.removeAll(delta.varedgeremove.get(tmp));
      edges.addAll(delta.varedgeadd.get(tmp));
      
      for(Edge e:edges) {
	if (nodeset.contains(e.dst))
	  externaledgeset.add(e);
      }
    }
  }

  /* This function removes the caller reachable edges from the
   * callee's heap. */
  
  void removeEdges(Graph graph, Delta delta, HashSet<AllocNode> nodeset, MySet<Edge> edgeset, MySet<Edge> externaledgeset) {
    //Want to remove the set of internal edges
    for(Edge e:edgeset) {
      if (e.src!=null&&!graph.callerEdges.contains(e)) {
	delta.removeHeapEdge(e);
      }
    }

    //Want to remove the set of external edges
    for(Edge e:externaledgeset) {
      //want to remove the set of internal edges
      if (!graph.callerEdges.contains(e))
	delta.removeEdge(e);
    }
  }

  Delta processFlatCall(BBlock callblock, int callindex, FlatCall fcall, Delta delta, Graph graph) {
    Delta newDelta=new Delta(null, false);

    if (delta.getInit()) {
      MySet<Edge> edgeset=new MySet<Edge>();
      MySet<Edge> externaledgeset=new MySet<Edge>();
      HashSet<AllocNode> nodeset=new HashSet<AllocNode>();
      HashSet<ClassDescriptor> targetSet=new HashSet<ClassDescriptor>();
      Stack<AllocNode> tovisit=new Stack<AllocNode>();
      TempDescriptor tmpthis=fcall.getThis();
      graph.callerEdges=new MySet<Edge>();

      //Handle the this temp
      processThisTargets(targetSet, graph, delta, newDelta, nodeset, tovisit, edgeset, tmpthis, null);

      //Go through each temp
      processParams(graph, delta, newDelta, nodeset, tovisit, edgeset, fcall, false);
      
      //Traverse all reachable nodes
      computeReachableNodes(graph, delta, newDelta, nodeset, tovisit, edgeset, null);

      //Compute call targets
      HashSet<MethodDescriptor> newtargets=computeTargets(fcall, newDelta);

      //Fix mapping
      fixMapping(fcall, newtargets, null, newDelta, callblock, callindex);

      //Compute edges into region to splice out
      computeExternalEdges(graph, delta, nodeset, null, externaledgeset);

      //Splice out internal edges
      removeEdges(graph, delta, nodeset, edgeset, externaledgeset);

      //store data structures
      graph.externalEdgeSet=externaledgeset;
      graph.reachNode=nodeset;
      graph.reachEdge=edgeset;
      
      graph.callTargets=newtargets;
      graph.callNodeAges=new HashSet<AllocNode>();
      graph.callOldNodes=new HashSet<AllocNode>();

      //Apply diffs to graph
      applyDiffs(graph, delta, true);
    } else {
      MySet<Edge> edgeset=new MySet<Edge>();
      MySet<Edge> externaledgeset=new MySet<Edge>();
      HashSet<AllocNode> nodeset=new HashSet<AllocNode>();
      MySet<Edge> oldedgeset=graph.reachEdge;
      HashSet<AllocNode> oldnodeset=graph.reachNode;

      HashSet<ClassDescriptor> targetSet=new HashSet<ClassDescriptor>();
      Stack<AllocNode> tovisit=new Stack<AllocNode>();
      TempDescriptor tmpthis=fcall.getThis();
      //Fix up delta to get rid of unnecessary heap edge removals
      for(Map.Entry<AllocNode, MySet<Edge>> entry:delta.heapedgeremove.entrySet()) {
	for(Iterator<Edge> eit=entry.getValue().iterator();eit.hasNext();) {
	  Edge e=eit.next();
	  if (graph.callerEdges.contains(e))
	    eit.remove();
	}
      }

      //Fix up delta to get rid of unnecessary var edge removals
      for(Map.Entry<TempDescriptor, MySet<Edge>> entry:delta.varedgeremove.entrySet()) {
	for(Iterator<Edge> eit=entry.getValue().iterator();eit.hasNext();) {
	  Edge e=eit.next();
	  if (graph.callerEdges.contains(e))
	    eit.remove();
	}
      }
      
      //Handle the this temp
      processThisTargets(targetSet, graph, delta, newDelta, nodeset, tovisit, edgeset, tmpthis, oldnodeset);

      //Go through each temp
      processParams(graph, delta, newDelta, nodeset, tovisit, edgeset, fcall, true);
      //Go through each new heap edge that starts from old node
      MySet<Edge> newedges=GraphManip.getDiffEdges(delta, oldnodeset);
      edgeset.addAll(newedges);
      for(Edge e:newedges) {
	//Add new edges that start from old node to newDelta
	AllocNode src=e.src;
	if (!newDelta.heapedgeadd.containsKey(src)) {
	  newDelta.heapedgeadd.put(src, new MySet<Edge>());
	}
	newDelta.heapedgeadd.get(src).add(e);
	if (!nodeset.contains(e.dst)&&!oldnodeset.contains(e.dst)) {
	  nodeset.add(e.dst);
	  tovisit.add(e.dst);
	}
      }

      //Traverse all reachable nodes
      computeReachableNodes(graph, delta, newDelta, nodeset, tovisit, edgeset, oldnodeset);
      //Compute call targets
      HashSet<MethodDescriptor> newtargets=computeTargets(fcall, newDelta);
      graph.callTargets.addAll(newtargets);
      //add in new nodeset and edgeset
      oldnodeset.addAll(nodeset);
      oldedgeset.addAll(edgeset);
      //Fix mapping
      fixMapping(fcall, graph.callTargets, oldedgeset, newDelta, callblock, callindex);
      //Compute edges into region to splice out
      computeExternalEdges(graph, delta, oldnodeset, nodeset, externaledgeset);

      //Splice out internal edges
      removeEdges(graph, delta, nodeset, edgeset, externaledgeset);

      //Add external edges back in
      processCallExternal(graph, delta, externaledgeset);

      //Move new edges that should be summarized
      processSummarization(graph, delta);
      
      //Add in new external edges
      graph.externalEdgeSet.addAll(externaledgeset);
      //Apply diffs to graph
      applyDiffs(graph, delta);
    }
    return delta;
  }

  void processSummarization(Graph graph, Delta delta) {
    processSumHeapEdgeSet(delta.heapedgeadd, delta, graph);
    processSumHeapEdgeSet(delta.baseheapedge, delta, graph);
    processSumVarEdgeSet(delta.varedgeadd, delta, graph);
    processSumVarEdgeSet(delta.basevaredge, delta, graph);
  }

  void processSumVarEdgeSet(HashMap<TempDescriptor, MySet<Edge>> map, Delta delta, Graph graph) {
    MySet<Edge> edgestoadd=new MySet<Edge>();
    MySet<Edge> edgestoremove=new MySet<Edge>();
    for(Iterator<Map.Entry<TempDescriptor, MySet<Edge>>> eit=map.entrySet().iterator();eit.hasNext();) {
      Map.Entry<TempDescriptor, MySet<Edge>> entry=eit.next();
      MySet<Edge> edgeset=entry.getValue();

      for(Edge e:edgeset) {
	Edge copy=e.copy();
	boolean rewrite=false;
	if (copy.dst!=null&&graph.callNodeAges.contains(copy.dst)) {
	  copy.dst=allocFactory.getAllocNode(copy.dst, true);
	  rewrite=true;
	}
	if (rewrite) {
	  edgestoremove.add(e);
	  edgestoadd.add(copy);
	}
      }
    }
    for(Edge e:edgestoremove) {
      delta.removeVarEdge(e);
    }
    for(Edge e:edgestoadd) {
      delta.addVarEdge(e);
    }
  }
  
  public Alloc getAllocationSiteFromFlatNew(FlatNew node) {
    return allocFactory.getAllocNode(node, false).getAllocSite();
  }
 
  void processSumHeapEdgeSet(HashMap<AllocNode, MySet<Edge>> map, Delta delta, Graph graph) {
    MySet<Edge> edgestoadd=new MySet<Edge>();
    MySet<Edge> edgestoremove=new MySet<Edge>();
    for(Iterator<Map.Entry<AllocNode, MySet<Edge>>> eit=map.entrySet().iterator();eit.hasNext();) {
      Map.Entry<AllocNode, MySet<Edge>> entry=eit.next();
      AllocNode node=entry.getKey();
      MySet<Edge> edgeset=entry.getValue();

      for(Edge e:edgeset) {
	Edge copy=e.copy();
	boolean rewrite=false;
	if (copy.src!=null&&graph.callNodeAges.contains(copy.src)) {
	  copy.src=allocFactory.getAllocNode(copy.src, true);
	  rewrite=true;
	}
	if (copy.dst!=null&&graph.callNodeAges.contains(copy.dst)) {
	  copy.dst=allocFactory.getAllocNode(copy.dst, true);
	  rewrite=true;
	}
	if (rewrite) {
	  edgestoremove.add(e);
	  edgestoadd.add(copy);
	}
      }
    }
    for(Edge e:edgestoremove) {
      delta.removeHeapEdge(e);
    }
    for(Edge e:edgestoadd) {
      delta.addHeapEdge(e);
    }
  }

  //Handle external edges
  void processCallExternal(Graph graph, Delta newDelta, MySet<Edge> externalEdgeSet) {
    //Add external edges in
    for(Edge e:externalEdgeSet) {
      //First did we age the source
      Edge newedge=e.copy();
      if (newedge.src!=null&&!e.src.isSummary()&&graph.callNodeAges.contains(e.src)) {
	AllocNode summaryNode=allocFactory.getAllocNode(newedge.src, true);
	newedge.src=summaryNode;
      }
      //Compute target
      if (graph.callNodeAges.contains(e.dst)&&!e.dst.isSummary()) {
	if (graph.callOldNodes.contains(e.dst)) {
	  //Need two edges
	  Edge copy=newedge.copy();
	  mergeEdge(graph, newDelta, copy);
	}
	//Now add summarized node
	newedge.dst=allocFactory.getAllocNode(newedge.dst, true);
	mergeCallEdge(graph, newDelta, newedge);
      } else {
	//Add edge to single node
	mergeEdge(graph, newDelta, newedge);
      }
    }
  }

  /* This function applies callee deltas to the caller heap. */

  Delta applyCallDelta(Delta delta, BBlock bblock) {
    Delta newDelta=new Delta(null, false);
    Vector<FlatNode> nodes=bblock.nodes();
    PPoint ppoint=delta.getBlock();
    FlatCall fcall=(FlatCall)nodes.get(ppoint.getIndex());
    Graph graph=graphMap.get(fcall);
    Graph oldgraph=(ppoint.getIndex()==0)?
      bbgraphMap.get(bblock):
      graphMap.get(nodes.get(ppoint.getIndex()-1));
    Set<FlatSESEEnterNode> seseCallers=OoOJava?taskAnalysis.getTransitiveExecutingRBlocks(fcall):null;

    //Age outside nodes if necessary
    for(Iterator<AllocNode> nodeit=delta.addNodeAges.iterator();nodeit.hasNext();) {
      AllocNode node=nodeit.next();
      if (!graph.callNodeAges.contains(node)) {
	graph.callNodeAges.add(node);
	newDelta.addNodeAges.add(node);
      }
      if (!graph.reachNode.contains(node)&&!node.isSummary()) {
	/* Need to age node in existing graph*/
	summarizeInGraph(graph, newDelta, node);
      }
    }
    //Add heap edges in
    for(Map.Entry<AllocNode, MySet<Edge>> entry:delta.heapedgeadd.entrySet()) {
      for(Edge e:entry.getValue()) {
	boolean addedge=false;
	Edge edgetoadd=null;
	if (e.statuspredicate==Edge.NEW) {
	  edgetoadd=e;
	} else {
	  Edge origEdgeKey=e.makeStatus(allocFactory);
	  if (oldgraph.nodeMap.containsKey(origEdgeKey.src)&&
	      oldgraph.nodeMap.get(origEdgeKey.src).contains(origEdgeKey)) {
	    Edge origEdge=oldgraph.nodeMap.get(origEdgeKey.src).get(origEdgeKey);
	    //copy the predicate
	    origEdgeKey.statuspredicate=origEdge.statuspredicate;
	    edgetoadd=origEdgeKey;
	  }
	}
	if (seseCallers!=null&&edgetoadd!=null)
	  edgetoadd.taintModify(seseCallers);
	mergeCallEdge(graph, newDelta, edgetoadd);
      }
    }
    
    processCallExternal(graph, newDelta, graph.externalEdgeSet);

    //Add edge for return value
    if (fcall.getReturnTemp()!=null) {
      MySet<Edge> returnedge=delta.varedgeadd.get(returntmp);
      if (returnedge!=null)
	for(Edge e:returnedge) {
	  Edge newedge=e.copy();
	  newedge.srcvar=fcall.getReturnTemp();
	  if (seseCallers!=null)
	    newedge.taintModify(seseCallers);
	  if (graph.getEdges(fcall.getReturnTemp())==null||!graph.getEdges(fcall.getReturnTemp()).contains(newedge))
	    newDelta.addEdge(newedge);
	}
    }
    applyDiffs(graph, newDelta);
    return newDelta;
  }
  
  public void mergeEdge(Graph graph, Delta newDelta, Edge edgetoadd) {
    if (edgetoadd!=null) {
      Edge match=graph.getMatch(edgetoadd);

      if (match==null||!match.subsumes(edgetoadd)) {
	Edge mergededge=edgetoadd.merge(match);
	newDelta.addEdge(mergededge);
      }
    }
  }

  /* This is a call produced edge...need to remember this */

  public void mergeCallEdge(Graph graph, Delta newDelta, Edge edgetoadd) {
    if (edgetoadd!=null) {
      Edge match=graph.getMatch(edgetoadd);

      if (match==null||!match.subsumes(edgetoadd)) {
	Edge mergededge=edgetoadd.merge(match);
	newDelta.addEdge(mergededge);
	graph.callerEdges.add(mergededge);
	//System.out.println("ADDING: "+ mergededge);
      }
    }
  }


  /* Summarizes out of context nodes in graph */
  void summarizeInGraph(Graph graph, Delta newDelta, AllocNode singleNode) {
    AllocNode summaryNode=allocFactory.getAllocNode(singleNode, true);

    //Handle outgoing heap edges
    MySet<Edge> edgeset=graph.getEdges(singleNode);

    for(Edge e:edgeset) {
      Edge rewrite=e.rewrite(singleNode, summaryNode);
      //Remove old edge
      newDelta.removeHeapEdge(e);
      mergeCallEdge(graph, newDelta, rewrite);
    }
    
    //Handle incoming edges
    MySet<Edge> backedges=graph.getBackEdges(singleNode);
    for(Edge e:backedges) {
      if (e.dst==singleNode) {
	//Need to get original edge so that predicate will be correct
	Edge match=graph.getMatch(e);
	if (match!=null) {
	  Edge rewrite=match.rewrite(singleNode, summaryNode);
	  newDelta.removeEdge(match);
	  mergeCallEdge(graph, newDelta, rewrite);
	}
      }
    }
  }

  void applyDiffs(Graph graph, Delta delta) {
    applyDiffs(graph, delta, false);
  }

  void applyDiffs(Graph graph, Delta delta, boolean genbackwards) {
    //build backwards map if requested
    if (genbackwards&&graph.backMap==null) {
      graph.backMap=new HashMap<AllocNode, MySet<Edge>>();
      if (graph.parent.backMap==null) {
	graph.parent.backMap=new HashMap<AllocNode, MySet<Edge>>();
	for(Map.Entry<AllocNode, MySet<Edge>> entry:graph.nodeMap.entrySet()) {
	  for(Edge e:entry.getValue()) {
	    if (!graph.parent.backMap.containsKey(e.dst))
	      graph.parent.backMap.put(e.dst, new MySet<Edge>());
	    graph.parent.backMap.get(e.dst).add(e);
	  }
	}
	for(Map.Entry<TempDescriptor, MySet<Edge>> entry:graph.varMap.entrySet()) {
	  for(Edge e:entry.getValue()) {
	    if (!graph.parent.backMap.containsKey(e.dst))
	      graph.parent.backMap.put(e.dst, new MySet<Edge>());
	    graph.parent.backMap.get(e.dst).add(e);
	  }
	}
      }
    }

    //Add hidden base edges
    for(Map.Entry<AllocNode, MySet<Edge>> e: delta.baseheapedge.entrySet()) {
      AllocNode node=e.getKey();
      MySet<Edge> edges=e.getValue();
      if (graph.nodeMap.containsKey(node)) {
	MySet<Edge> nodeEdges=graph.nodeMap.get(node);
	nodeEdges.addAll(edges);
      }
    }

    //Remove heap edges
    for(Map.Entry<AllocNode, MySet<Edge>> e: delta.heapedgeremove.entrySet()) {
      AllocNode node=e.getKey();
      MySet<Edge> edgestoremove=e.getValue();
      if (graph.nodeMap.containsKey(node)) {
	//Just apply diff to current map
	graph.nodeMap.get(node).removeAll(edgestoremove);
      } else {
	//Generate diff from parent graph
	MySet<Edge> parentedges=graph.parent.nodeMap.get(node);
	if (parentedges!=null) {
	  MySet<Edge> newedgeset=Util.setSubtract(parentedges, edgestoremove);
	  graph.nodeMap.put(node, newedgeset);
	}
      }
    }

    //Add heap edges
    for(Map.Entry<AllocNode, MySet<Edge>> e: delta.heapedgeadd.entrySet()) {
      AllocNode node=e.getKey();
      MySet<Edge> edgestoadd=e.getValue();
      //If we have not done a subtract, then 
      if (!graph.nodeMap.containsKey(node)) {
	//Copy the parent entry
	if (graph.parent.nodeMap.containsKey(node))
	  graph.nodeMap.put(node, (MySet<Edge>)graph.parent.nodeMap.get(node).clone());
	else
	  graph.nodeMap.put(node, new MySet<Edge>());
      }
      Edge.mergeEdgesInto(graph.nodeMap.get(node),edgestoadd);
      if (genbackwards) {
	for(Edge eadd:edgestoadd) {
	  if (!graph.backMap.containsKey(eadd.dst))
	    graph.backMap.put(eadd.dst, new MySet<Edge>());
	  graph.backMap.get(eadd.dst).add(eadd);
	}
      }
    }

    //Remove var edges
    for(Map.Entry<TempDescriptor, MySet<Edge>> e: delta.varedgeremove.entrySet()) {
      TempDescriptor tmp=e.getKey();
      MySet<Edge> edgestoremove=e.getValue();

      if (graph.varMap.containsKey(tmp)) {
	//Just apply diff to current map
	graph.varMap.get(tmp).removeAll(edgestoremove);
      } else if (graph.parent.varMap.containsKey(tmp)) {
	//Generate diff from parent graph
	MySet<Edge> parentedges=graph.parent.varMap.get(tmp);
	MySet<Edge> newedgeset=Util.setSubtract(parentedges, edgestoremove);
	graph.varMap.put(tmp, newedgeset);
      }
    }

    //Add var edges
    for(Map.Entry<TempDescriptor, MySet<Edge>> e: delta.varedgeadd.entrySet()) {
      TempDescriptor tmp=e.getKey();
      MySet<Edge> edgestoadd=e.getValue();
      if (graph.varMap.containsKey(tmp)) {
	Edge.mergeEdgesInto(graph.varMap.get(tmp), edgestoadd);
      } else 
	graph.varMap.put(tmp, (MySet<Edge>) edgestoadd.clone());
      if (genbackwards) {
	for(Edge eadd:edgestoadd) {
	  if (!graph.backMap.containsKey(eadd.dst))
	    graph.backMap.put(eadd.dst, new MySet<Edge>());
	  graph.backMap.get(eadd.dst).add(eadd);
	}
      }
    }

    //Add node additions
    for(AllocNode node:delta.addNodeAges) {
      graph.nodeAges.add(node);
    }
    
    for(Map.Entry<AllocNode, Boolean> nodeentry:delta.addOldNodes.entrySet()) {
      AllocNode node=nodeentry.getKey();
      Boolean ispresent=nodeentry.getValue();
      graph.oldNodes.put(node, ispresent);
    }
  }

  Delta processSetFieldElementNode(FlatNode node, Delta delta, Graph graph) {
    TempDescriptor src;
    FieldDescriptor fd;
    TempDescriptor dst;
    if (node.kind()==FKind.FlatSetElementNode) {
      FlatSetElementNode fen=(FlatSetElementNode) node;
      src=fen.getSrc();
      fd=null;
      dst=fen.getDst();
    } else {
      FlatSetFieldNode ffn=(FlatSetFieldNode) node;
      src=ffn.getSrc();
      fd=ffn.getField();
      dst=ffn.getDst();
    }
    //Do nothing for non pointers
    if (!src.getType().isPtr())
      return delta;

    if (delta.getInit()) {
      MySet<Edge> srcEdges=GraphManip.getEdges(graph, delta, src);
      MySet<Edge> dstEdges=GraphManip.getEdges(graph, delta, dst);

      if (OoOJava&&!accessible.isAccessible(node, src)) {
	Taint srcStallTaint=Taint.factory(node,  src, AllocFactory.dummySite, node, ReachGraph.predsEmpty);
	srcEdges=Edge.taintAll(srcEdges, srcStallTaint);
	updateVarDelta(graph, delta, src, srcEdges, null);
      }

      if (OoOJava&&!accessible.isAccessible(node, dst)) {
	Taint dstStallTaint=Taint.factory(node,  dst, AllocFactory.dummySite, node, ReachGraph.predsEmpty);
	dstEdges=Edge.taintAll(dstEdges, dstStallTaint);
	updateVarDelta(graph, delta, dst, dstEdges, null);
      }

      MySet<Edge> edgesToAdd=GraphManip.genEdges(dstEdges, fd, srcEdges);
      MySet<Edge> edgesToRemove=null;
      if (dstEdges.size()==1&&!dstEdges.iterator().next().dst.isSummary()&&fd!=null) {
	/* Can do a strong update */
	edgesToRemove=GraphManip.getEdges(graph, delta, dstEdges, fd);
	graph.strongUpdateSet=edgesToRemove;
      } else
	graph.strongUpdateSet=new MySet<Edge>();

      if (OoOJava) {
	effectsAnalysis.analyzeFlatSetFieldNode(dstEdges, fd, node);
      }

      /* Update diff */
      updateHeapDelta(graph, delta, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    } else {
      /* First look at new sources */
      MySet<Edge> edgesToAdd=new MySet<Edge>();
      MySet<Edge> newSrcEdges=GraphManip.getDiffEdges(delta, src);
      MySet<Edge> srcEdges=GraphManip.getEdges(graph, delta, src);
      HashSet<AllocNode> dstNodes=GraphManip.getNodes(graph, delta, dst);
      MySet<Edge> newDstEdges=GraphManip.getDiffEdges(delta, dst);

      if (OoOJava&&!accessible.isAccessible(node, src)) {
	Taint srcStallTaint=Taint.factory(node,  src, AllocFactory.dummySite, node, ReachGraph.predsEmpty);
	newSrcEdges=Edge.taintAll(newSrcEdges, srcStallTaint);
	updateVarDelta(graph, delta, src, newSrcEdges, null);
      }

      if (OoOJava&&!accessible.isAccessible(node, dst)) {
	Taint dstStallTaint=Taint.factory(node,  dst, AllocFactory.dummySite, node, ReachGraph.predsEmpty);
	newDstEdges=Edge.taintAll(newDstEdges, dstStallTaint);
	updateVarDelta(graph, delta, dst, newDstEdges, null);
      }

      if (OoOJava) {
	effectsAnalysis.analyzeFlatSetFieldNode(newDstEdges, fd, node);
      }

      MySet<Edge> edgesToRemove=null;
      if (newDstEdges.size()!=0) {
	if (dstNodes.size()>1&&!dstNodes.iterator().next().isSummary()&&fd!=null) {
	  /* Need to undo strong update */
	  if (graph.strongUpdateSet!=null) {
	    edgesToAdd.addAll(graph.strongUpdateSet);
	    graph.strongUpdateSet=null; //Prevent future strong updates
	  }
	} else if (dstNodes.size()==1&&newDstEdges.size()==1&&!newDstEdges.iterator().next().dst.isSummary()&&graph.strongUpdateSet!=null&&fd!=null) {
	  edgesToRemove=GraphManip.getEdges(graph, delta, dstNodes, fd);
	  graph.strongUpdateSet.addAll(edgesToRemove);
	}
	Edge.mergeEdgesInto(edgesToAdd, GraphManip.genEdges(newDstEdges, fd, srcEdges));
      }

      //Kill new edges
      if (graph.strongUpdateSet!=null&&fd!=null) {
	MySet<Edge> otherEdgesToRemove=GraphManip.getDiffEdges(delta, dstNodes, fd);
	if (edgesToRemove!=null)
	  edgesToRemove.addAll(otherEdgesToRemove);
	else
	  edgesToRemove=otherEdgesToRemove;
	graph.strongUpdateSet.addAll(otherEdgesToRemove);
      }

      //Next look at new destinations
      Edge.mergeEdgesInto(edgesToAdd, GraphManip.genEdges(dstNodes, fd, newSrcEdges));

      /* Update diff */
      updateHeapDelta(graph, delta, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    }
    return delta;
  }

  Delta processCopyNode(FlatNode node, Delta delta, Graph graph) {
    TempDescriptor src;
    TempDescriptor dst;
    if (node.kind()==FKind.FlatOpNode) {
      FlatOpNode fon=(FlatOpNode) node;
      src=fon.getLeft();
      dst=fon.getDest();
    } else if (node.kind()==FKind.FlatReturnNode) {
      FlatReturnNode frn=(FlatReturnNode)node;
      src=frn.getReturnTemp();
      dst=returntmp;
      if (src==null||!src.getType().isPtr()) {
	//This is a NOP
	applyDiffs(graph, delta);
	return delta;
      }
    } else {
      FlatCastNode fcn=(FlatCastNode) node;
      src=fcn.getSrc();
      dst=fcn.getDst();
    }
    if (delta.getInit()) {
      MySet<Edge> srcedges=GraphManip.getEdges(graph, delta, src);
      MySet<Edge> edgesToAdd=GraphManip.genEdges(dst, srcedges);
      MySet<Edge> edgesToRemove=GraphManip.getEdges(graph, delta, dst);
      updateVarDelta(graph, delta, dst, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    } else {
      /* First compute new src nodes */
      MySet<Edge> newSrcEdges=GraphManip.getDiffEdges(delta, src);

      /* Compute the union, and then the set of edges */
      MySet<Edge> edgesToAdd=GraphManip.genEdges(dst, newSrcEdges);
      
      /* Compute set of edges to remove */
      MySet<Edge> edgesToRemove=GraphManip.getDiffEdges(delta, dst);      

      /* Update diff */
      updateVarDelta(graph, delta, dst, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    }
    return delta;
  }

  Delta processFieldElementNode(FlatNode node, Delta delta, Graph graph) {
    TempDescriptor src;
    FieldDescriptor fd;
    TempDescriptor dst;
    TaintSet taint=null;

    if (node.kind()==FKind.FlatElementNode) {
      FlatElementNode fen=(FlatElementNode) node;
      src=fen.getSrc();
      fd=null;
      dst=fen.getDst();
    } else {
      FlatFieldNode ffn=(FlatFieldNode) node;
      src=ffn.getSrc();
      fd=ffn.getField();
      dst=ffn.getDst();
    }
    if (OoOJava&&!accessible.isAccessible(node, src)) {
      taint=TaintSet.factory(Taint.factory(node,  src, AllocFactory.dummySite, node, ReachGraph.predsEmpty));
    }

    //Do nothing for non pointers
    if (!dst.getType().isPtr())
      return delta;
    if (delta.getInit()) {
      MySet<Edge> srcedges=GraphManip.getEdges(graph, delta, src);
      MySet<Edge> edgesToAdd=GraphManip.dereference(graph, delta, dst, srcedges, fd, node, taint);
      MySet<Edge> edgesToRemove=GraphManip.getEdges(graph, delta, dst);
      if (OoOJava)
	effectsAnalysis.analyzeFlatFieldNode(srcedges, fd, node);

      updateVarDelta(graph, delta, dst, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    } else {
      /* First compute new objects we read fields of */
      MySet<Edge> allsrcedges=GraphManip.getEdges(graph, delta, src);
      MySet<Edge> edgesToAdd=GraphManip.diffDereference(delta, dst, allsrcedges, fd, node, taint);
      /* Next compute new targets of fields */
      MySet<Edge> newsrcedges=GraphManip.getDiffEdges(delta, src);
      MySet<Edge> newfdedges=GraphManip.dereference(graph, delta, dst, newsrcedges, fd, node, taint);

      /* Compute the union, and then the set of edges */
      Edge.mergeEdgesInto(edgesToAdd, newfdedges);
      
      /* Compute set of edges to remove */
      MySet<Edge> edgesToRemove=GraphManip.getDiffEdges(delta, dst);      

      if (OoOJava)
	effectsAnalysis.analyzeFlatFieldNode(newsrcedges, fd, node);
      
      /* Update diff */
      updateVarDelta(graph, delta, dst, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    }

    return delta;
  }

  void updateVarDelta(Graph graph, Delta delta, TempDescriptor tmp, MySet<Edge> edgestoAdd, MySet<Edge> edgestoRemove) {
    MySet<Edge> edgeAdd=delta.varedgeadd.get(tmp);
    MySet<Edge> edgeRemove=delta.varedgeremove.get(tmp);
    MySet<Edge> existingEdges=graph.getEdges(tmp);
    if (edgestoRemove!=null)
      for(Edge e: edgestoRemove) {
	//remove edge from delta
	if (edgeAdd!=null)
	  edgeAdd.remove(e);
	//if the edge is already in the graph, add an explicit remove to the delta
	if (existingEdges.contains(e))
	  delta.removeVarEdge(e);
      }
    for(Edge e: edgestoAdd) {
      //Remove the edge from the remove set
      if (edgeRemove!=null)
	edgeRemove.remove(e);
      //Explicitly add it to the add set unless it is already in the graph
      if (typeUtil.isSuperorType(tmp.getType(), e.dst.getType())) {
	if (!existingEdges.contains(e)) {
	  delta.addVarEdge(e);
	} else {
	  //See if the old edge subsumes the new one
	  Edge olde=existingEdges.get(e);
	  if (!olde.subsumes(e)) {
	    delta.addVarEdge(olde.merge(e));
	  }
	}
      }
    }
  }

  void updateHeapDelta(Graph graph, Delta delta, MySet<Edge> edgestoAdd, MySet<Edge> edgestoRemove) {
    if (edgestoRemove!=null)
      for(Edge e: edgestoRemove) {
	AllocNode src=e.src;
	MySet<Edge> edgeAdd=delta.heapedgeadd.get(src);
	MySet<Edge> existingEdges=graph.getEdges(src);
	//remove edge from delta
	if (edgeAdd!=null)
	  edgeAdd.remove(e);
	//if the edge is already in the graph, add an explicit remove to the delta
	if (existingEdges.contains(e)) {
	  delta.removeHeapEdge(e);
	}
      }
    if (edgestoAdd!=null)
      for(Edge e: edgestoAdd) {
	AllocNode src=e.src;
	MySet<Edge> edgeRemove=delta.heapedgeremove.get(src);
	MySet<Edge> existingEdges=graph.getEdges(src);
	//Remove the edge from the remove set
	if (edgeRemove!=null)
	  edgeRemove.remove(e);
	//Explicitly add it to the add set unless it is already in the graph
	if (!existingEdges.contains(e)) {
	  delta.addHeapEdge(e);
	} else {
	  //See if the old edge subsumes the new one
	  Edge olde=existingEdges.get(e);
	  if (!olde.subsumes(e)) {
	    delta.addHeapEdge(olde.merge(e));
	  }
	}
      }
  }

  Delta processFlatNop(FlatNode node, Delta delta, Graph graph) {
    applyDiffs(graph, delta);
    return delta;
  }
  
  Delta processNewNode(FlatNew node, Delta delta, Graph graph) {
    AllocNode summary=allocFactory.getAllocNode(node, true);
    AllocNode single=allocFactory.getAllocNode(node, false);
    TempDescriptor tmp=node.getDst();

    if (delta.getInit()) {
      /* We don't have to deal with summarization here...  The
       * intuition is that this is the only place where we generate
       * nodes for this allocation site and this is the first time
       * we've analyzed this site */

      //Build new Edge
      Edge e=new Edge(tmp, single);
      //Build new Edge set
      MySet<Edge> newedges=new MySet<Edge>();
      newedges.add(e);
      //Add it into the diffs
      delta.varedgeadd.put(tmp, newedges);
      //Remove the old edges
      MySet<Edge> oldedges=graph.getEdges(tmp);
      if (!oldedges.isEmpty())
	delta.varedgeremove.put(tmp, (MySet<Edge>) oldedges);
      //Apply incoming diffs to graph
      applyDiffs(graph, delta);
      //Note that we create a single node
      delta.addNodeAges.add(single);
      //Kill the old node
      if (delta.addOldNodes.containsKey(single)||delta.baseOldNodes.containsKey(single)) {
	delta.addOldNodes.put(single, Boolean.FALSE);
      }
    } else {
      /* 1. Fix up the variable edge additions */

      for(Iterator<Map.Entry<TempDescriptor, MySet<Edge>>> entryIt=delta.varedgeadd.entrySet().iterator();entryIt.hasNext();) {
	Map.Entry<TempDescriptor, MySet<Edge>> entry=entryIt.next();

	if (entry.getKey()==tmp) {
	  /* Check if this is the tmp we overwrite */
	  entryIt.remove();
	} else {
	  /* Otherwise, check if the target of the edge is changed... */
	  summarizeSet(entry.getValue(), graph.varMap.get(entry.getKey()), single, summary);
	}
      }
      
      /* 2. Fix up the base variable edges */

      for(Iterator<Map.Entry<TempDescriptor, MySet<Edge>>> entryIt=delta.basevaredge.entrySet().iterator();entryIt.hasNext();) {
	Map.Entry<TempDescriptor, MySet<Edge>> entry=entryIt.next();
	TempDescriptor entrytmp=entry.getKey();
	if (entrytmp==tmp) {
	  /* Check is this is the tmp we overwrite, if so add to remove set */
	  Util.relationUpdate(delta.varedgeremove, tmp, null, entry.getValue());
	} else {
	  /* Check if the target of the edge is changed */ 
	  MySet<Edge> newset=(MySet<Edge>)entry.getValue().clone();
	  MySet<Edge> removeset=shrinkSet(newset, graph.varMap.get(entrytmp), single, summary);
	  Util.relationUpdate(delta.varedgeremove, entrytmp, newset, removeset);
	  Util.relationUpdate(delta.varedgeadd, entrytmp, null, newset);
	}
      }


      /* 3. Fix up heap edge additions */

      HashMap<AllocNode, MySet<Edge>> addheapedge=new HashMap<AllocNode, MySet<Edge>>();
      for(Iterator<Map.Entry<AllocNode, MySet<Edge>>> entryIt=delta.heapedgeadd.entrySet().iterator();entryIt.hasNext();) {
	Map.Entry<AllocNode, MySet<Edge>> entry=entryIt.next();
	MySet<Edge> edgeset=entry.getValue();
	AllocNode allocnode=entry.getKey();
	if (allocnode==single) {
	  entryIt.remove();
	  summarizeSet(edgeset, graph.nodeMap.get(summary), single, summary);
	  addheapedge.put(summary, edgeset);
	} else {
	  summarizeSet(edgeset, graph.nodeMap.get(allocnode), single, summary);
	}
      }
      
      /* Merge in diffs */

      for(Map.Entry<AllocNode, MySet<Edge>> entry:addheapedge.entrySet()) {
	AllocNode allocnode=entry.getKey();
	Util.relationUpdate(delta.heapedgeadd, allocnode, null, entry.getValue());
      }

      /* 4. Fix up the base heap edges */

      for(Iterator<Map.Entry<AllocNode, MySet<Edge>>> entryIt=delta.baseheapedge.entrySet().iterator();entryIt.hasNext();) {
	Map.Entry<AllocNode, MySet<Edge>> entry=entryIt.next();
	MySet<Edge> edgeset=entry.getValue();
	AllocNode allocnode=entry.getKey();
	if (allocnode==single) {
	  entryIt.remove();
	}
	AllocNode addnode=(allocnode==single)?summary:allocnode;

	MySet<Edge> newset=(MySet<Edge>) edgeset.clone();
	MySet<Edge> removeset=shrinkSet(newset, graph.nodeMap.get(addnode), single, summary);
	Util.relationUpdate(delta.heapedgeadd, addnode, null, newset);
	Util.relationUpdate(delta.heapedgeremove, allocnode, null, removeset);
      }

      /* Update Node Ages...If the base or addNodeAges set contains a
       * single node, it now should also contain a summary node...  No
       * need to generate a single node as that has already been
       * done. */
      if (delta.baseNodeAges.contains(single)||delta.addNodeAges.contains(single)) {
	delta.addNodeAges.add(summary);
      }

      //Kill the old node if someone tries to add it
      if (delta.addOldNodes.containsKey(single)||delta.baseOldNodes.containsKey(single)) {
	delta.addOldNodes.put(single, Boolean.FALSE);
      }
      
      //Apply incoming diffs to graph
      applyDiffs(graph, delta);      
    }
    return delta;
  }

  /* This function builds a new edge set where oldnode is summarized into new node */

  void summarizeSet(MySet<Edge> edgeset, MySet<Edge> oldedgeset, AllocNode oldnode, AllocNode sumnode) {
    MySet<Edge> newSet=null;
    for(Iterator<Edge> edgeit=edgeset.iterator();edgeit.hasNext();) {
      Edge e=edgeit.next();
      if (e.dst==oldnode||e.src==oldnode) {
	if (newSet==null) {
	  newSet=new MySet<Edge>();
	}
	edgeit.remove();
	e=e.copy();

	if (e.dst==oldnode) {
	  e.dst=sumnode;
	}
	if (e.src==oldnode) {
	  e.src=sumnode;
	}
	if (oldedgeset==null||!oldedgeset.contains(e))
	  newSet.add(e);
      }
    }
    if (newSet!=null)
      edgeset.addAll(newSet);
  }

  /* Shrinks the incoming set to just include rewritten values.
   * Returns a set of the original rewritten values */

  MySet<Edge> shrinkSet(MySet<Edge> edgeset, MySet<Edge> oldedgeset, AllocNode oldnode, AllocNode newnode) {
    MySet<Edge> newSet=null;
    MySet<Edge> removeSet=null;
    for(Iterator<Edge> edgeit=edgeset.iterator();edgeit.hasNext();) {
      Edge e=edgeit.next();
      edgeit.remove();
      if (e.dst==oldnode||e.src==oldnode) {
	if (newSet==null) {
	  newSet=new MySet<Edge>();
	  removeSet=new MySet<Edge>();
	}

	removeSet.add(e);
	e=e.copy();
	if (e.dst==oldnode)
	  e.dst=newnode;
	if (e.src==oldnode)
	  e.src=newnode;
	if (oldedgeset==null||!oldedgeset.contains(e))
	  newSet.add(e);
      }
    }
    if (newSet!=null)
      edgeset.addAll(newSet);
    return removeSet;
  } 

  /* This function returns a completely new Delta...  It is safe to
   * modify this */

  Delta applyInitDelta(Delta delta, BBlock block) {
    //Apply delta to graph
    boolean newGraph=false;
    if (!bbgraphMap.containsKey(block)) {
      bbgraphMap.put(block, new Graph(null));
      newGraph=true;
    }
    Graph graph=bbgraphMap.get(block);

    if (newGraph) {
      Delta newdelta=new Delta(null, true);
      //Add in heap edges and throw away original diff

      for(Map.Entry<AllocNode, MySet<Edge>> entry:delta.heapedgeadd.entrySet()) {
	graph.nodeMap.put(entry.getKey(), new MySet<Edge>(entry.getValue()));
      }
      //Add in var edges and throw away original diff
      Set<TempDescriptor> livetemps=bblivetemps.get(block);

      for(Map.Entry<TempDescriptor, MySet<Edge>> entry:delta.varedgeadd.entrySet()) {
	if (livetemps.contains(entry.getKey()))
	  graph.varMap.put(entry.getKey(), new MySet<Edge>(entry.getValue()));
      }
      //Record that this is initial set...
      graph.nodeAges.addAll(delta.addNodeAges);
      //Add old nodes
      for(Map.Entry<AllocNode, Boolean> oldentry:delta.addOldNodes.entrySet()) {
	if (oldentry.getValue().booleanValue()) {
	  graph.oldNodes.put(oldentry.getKey(), Boolean.TRUE);
	}
      }
      return newdelta;
    } else {
      Delta newdelta=new Delta(null, false);
      //merge in heap edges and variables
      mergeHeapEdges(graph, delta, newdelta);
      mergeVarEdges(graph, delta, newdelta, block);
      mergeAges(graph, delta, newdelta);
      return newdelta;
    }
  }

  /* This function merges in the heap edges.  It updates delta to be
   * the difference */

  void mergeHeapEdges(Graph graph, Delta delta, Delta newdelta) {
    //Merge in edges
    for(Map.Entry<AllocNode, MySet<Edge>> heapedge:delta.heapedgeadd.entrySet()) {
      AllocNode nsrc=heapedge.getKey();
      MySet<Edge> edges=heapedge.getValue();

      if (graph.backMap!=null) {
	for(Edge e:edges) {
	  if (!graph.backMap.containsKey(e.dst))
	    graph.backMap.put(e.dst, new MySet<Edge>());
	  graph.backMap.get(e.dst).add(e);
	}
      }

      if (!graph.nodeMap.containsKey(nsrc)) {
	graph.nodeMap.put(nsrc, new MySet<Edge>());
      }
      MySet<Edge> dstedges=graph.nodeMap.get(nsrc);
      MySet<Edge> diffedges=new MySet<Edge>();
      for(Edge e:edges) {
	if (!dstedges.contains(e)) {
	  //We have a new edge
	  diffedges.add(e);
	  dstedges.add(e);
	} else {
	  Edge origedge=dstedges.get(e);
	  if (!origedge.subsumes(e)) {
	    Edge mergededge=origedge.merge(e);
	    diffedges.add(mergededge);
	    dstedges.add(mergededge);
	  }
	}
      }
      //Done with edge set...
      if (diffedges.size()>0) {
	//completely new
	newdelta.baseheapedge.put(nsrc, diffedges);
      }
    }
  }

  /* This function merges in the var edges.  It updates delta to be
   * the difference */

  void mergeVarEdges(Graph graph, Delta delta, Delta newdelta, BBlock block) {
    //Merge in edges
    Set<TempDescriptor> livetemps=bblivetemps.get(block);
    
    for(Map.Entry<TempDescriptor, MySet<Edge>> varedge:delta.varedgeadd.entrySet()) {
      TempDescriptor tmpsrc=varedge.getKey();
      if (livetemps.contains(tmpsrc)) {
	MySet<Edge> edges=varedge.getValue();
	if (graph.backMap!=null) {
	  for(Edge e:edges) {
	    if (!graph.backMap.containsKey(e.dst))
	      graph.backMap.put(e.dst, new MySet<Edge>());
	    graph.backMap.get(e.dst).add(e);
	  }
	}
	
	if (!graph.varMap.containsKey(tmpsrc)) {
	  graph.varMap.put(tmpsrc, new MySet<Edge>());
	}
	MySet<Edge> dstedges=graph.varMap.get(tmpsrc);
	MySet<Edge> diffedges=new MySet<Edge>();
	for(Edge e:edges) {
	  if (!dstedges.contains(e)) {
	    //We have a new edge
	    diffedges.add(e);
	    dstedges.add(e);
	  } else {
	    Edge origedge=dstedges.get(e);
	    if (!origedge.subsumes(e)) {
	      Edge mergededge=origedge.merge(e);
	      diffedges.add(mergededge);
	      dstedges.add(mergededge);
	    }
	  }
	}
	//Done with edge set...
	if (diffedges.size()>0) {
	  //completely new
	  newdelta.basevaredge.put(tmpsrc,diffedges);
	}
      }
    }
  }

  void mergeAges(Graph graph, Delta delta, Delta newDelta) {
    //Merge in edges
    for(AllocNode node:delta.addNodeAges) {
      if (!graph.nodeAges.contains(node)) {
	graph.nodeAges.add(node);
	newDelta.baseNodeAges.add(node);
      }
    }
    for(Map.Entry<AllocNode, Boolean> oldentry:delta.addOldNodes.entrySet()) {
      AllocNode node=oldentry.getKey();
      boolean ispresent=oldentry.getValue().booleanValue();
      if (ispresent&&!graph.oldNodes.containsKey(node)) {
	graph.oldNodes.put(node, Boolean.TRUE);
	newDelta.baseOldNodes.put(node, Boolean.TRUE);
      }
    }
  }
}