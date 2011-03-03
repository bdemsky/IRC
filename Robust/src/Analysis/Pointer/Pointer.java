package Analysis.Pointer;
import java.util.*;
import IR.Flat.*;
import IR.*;
import Analysis.Pointer.BasicBlock.BBlock;
import Analysis.Pointer.AllocFactory.AllocNode;

public class Pointer {
  HashMap<FlatMethod, BasicBlock> blockMap;
  HashMap<BBlock, Graph> bbgraphMap;
  HashMap<FlatNode, Graph> graphMap;
  HashMap<FlatCall, Set<BBlock>> callMap;
  HashMap<BBlock, Set<PPoint>> returnMap;

  State state;
  TypeUtil typeUtil;
  AllocFactory allocFactory;
  LinkedList<Delta> toprocess;
  TempDescriptor returntmp;

  public Pointer(State state, TypeUtil typeUtil) {
    this.state=state;
    this.blockMap=new HashMap<FlatMethod, BasicBlock>();
    this.bbgraphMap=new HashMap<BBlock, Graph>();
    this.graphMap=new HashMap<FlatNode, Graph>();
    this.callMap=new HashMap<FlatCall, Set<BBlock>>();
    this.returnMap=new HashMap<BBlock, Set<PPoint>>();
    this.typeUtil=typeUtil;
    this.allocFactory=new AllocFactory(state, typeUtil);
    this.toprocess=new LinkedList<Delta>();
    ClassDescriptor stringcd=typeUtil.getClass(TypeUtil.ObjectClass);
    this.returntmp=new TempDescriptor("RETURNVAL", stringcd);
  }

  public BasicBlock getBBlock(FlatMethod fm) {
    if (!blockMap.containsKey(fm))
      blockMap.put(fm, BasicBlock.getBBlock(fm));
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
    arrayset.add(arrayedge);
    Edge stringedge=new Edge(fm.getParameter(0), allocFactory.StringArray);
    varset.add(stringedge);
    delta.heapedgeadd.put(allocFactory.StringArray, arrayset);
    delta.varedgeadd.put(fm.getParameter(0), varset);
    return delta;
  }

  public void doAnalysis() {
    toprocess.add(buildInitialContext());

    while(!toprocess.isEmpty()) {
      Delta delta=toprocess.remove();
      PPoint ppoint=delta.getBlock();
      BBlock bblock=ppoint.getBBlock();
      Vector<FlatNode> nodes=bblock.nodes();

      //Build base graph for entrance to this basic block
      delta=applyInitDelta(delta, bblock);
      Graph graph=bbgraphMap.get(bblock);

      Graph nodeGraph=null;
      //Compute delta at exit of each node
      for(int i=0; i<nodes.size();i++) {
	FlatNode currNode=nodes.get(i);
	if (!graphMap.containsKey(currNode)) {
	  graphMap.put(currNode, new Graph(graph));
	}
	nodeGraph=graphMap.get(currNode);
	delta=processNode(bblock, i, currNode, delta, nodeGraph);
      }
      generateFinalDelta(bblock, delta, nodeGraph);
    }
  }

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
      if (graph.nodeAges.contains(node))
	newDelta.addNodeAges.add(node);
      else if (graph.parent.nodeAges.contains(node))
	newDelta.addNodeAges.add(node);

      /* Compute ages */
      if (graph.oldNodes.containsKey(node)) {
	if (graph.oldNodes.get(node).booleanValue())
	  newDelta.addOldNodes.put(node, Boolean.TRUE);
      } else if (graph.parent.oldNodes.containsKey(node)) {
	//parent graphs only contain true...no need to check
	newDelta.addOldNodes.put(node, Boolean.TRUE);
      }
    }
  }

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
	newbaseedge.removeAll(delta.varedgeremove.get(tmp));
	/* Add in the new set*/
	newbaseedge.addAll(delta.varedgeadd.get(tmp));
	/* Store the results */
	newDelta.varedgeadd.put(tmp, newbaseedge);
      }

      /* Next we build heap edges */
      HashSet<AllocNode> nodeSet=new HashSet<AllocNode>();
      nodeSet.addAll(delta.baseheapedge.keySet());
      nodeSet.addAll(delta.heapedgeadd.keySet());
      nodeSet.addAll(delta.heapedgeremove.keySet());
      for(AllocNode node:nodeSet) {
	/* Start with the new incoming edges */
	MySet<Edge> newheapedge=(MySet<Edge>) delta.baseheapedge.get(node).clone();
	/* Remove the remove set */
	newheapedge.removeAll(delta.heapedgeremove.get(node));
	/* Add in the add set */
	newheapedge.addAll(delta.heapedgeadd.get(node));
	newDelta.heapedgeadd.put(node, newheapedge);

	/* Also need to subtract off some edges */
	MySet<Edge> removeset=delta.heapedgeremove.get(node);

	/* Remove the newly created edges..no need to propagate a diff for those */
	removeset.removeAll(delta.baseheapedge.get(node));
	newDelta.heapedgeremove.put(node, removeset);
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
      Vector<BBlock> blockvector=bblock.next();
      for(int i=0;i<blockvector.size();i++) {
	if (i==0) {
	  newDelta.setBlock(new PPoint(blockvector.get(i)));
	  toprocess.add(newDelta);
	} else {
	  toprocess.add(newDelta.diffBlock(new PPoint(blockvector.get(i))));
	}
      }
    }
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
    case FKind.FlatMethod:
    case FKind.FlatExit:
      return processFlatNop(node, delta, newgraph);
    case FKind.FlatCall:
      return processFlatCall(bblock, index, (FlatCall) node, delta, newgraph);
    case FKind.FlatSESEEnterNode:
    case FKind.FlatSESEExitNode:
      throw new Error("Unimplemented node:"+node);
    default:
      throw new Error("Unrecognized node:"+node);
    }
  }


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

  void computeReachableNodes(Graph graph, Delta delta, Delta newDelta, HashSet<AllocNode> nodeset, Stack<AllocNode> tovisit, MySet<Edge> edgeset, HashSet<AllocNode> oldnodeset) {
      while(!tovisit.isEmpty()) {
	AllocNode node=tovisit.pop();
	MySet<Edge> edges=GraphManip.getEdges(graph, delta, node);
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
	targets.add(cd.getCalledMethod(md));
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
	    buildInitDelta(bbgraphMap.get(block.getExit()), returnDelta);
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
	toprocess.add(d);
      } else if (newmethod) {
	if (basedelta==null) {
	  basedelta=newDelta.buildBase(oldedgeset);
	}
	//Build and enqueue delta
	Delta d=basedelta.changeParams(tmpMap, new PPoint(block.getStart()));
	toprocess.add(d);
      } else  {
	//Build and enqueue delta
	Delta d=newDelta.changeParams(tmpMap, new PPoint(block.getStart()));
	toprocess.add(d);
      }
    }
  }


  /* This function computes all edges that start outside of the callee context and go into the callee context */

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

    //Do heap edges first
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
  
  void removeEdges(Delta delta, HashSet<AllocNode> nodeset, MySet<Edge> edgeset, MySet<Edge> externaledgeset) {
    //Want to remove the set of internal edges
    for(Edge e:edgeset) {
      if (e.src!=null) {
	if (delta.heapedgeadd.containsKey(e.src)&&delta.heapedgeadd.get(e.src).contains(e)) {
	  //remove edge if it is in the add set
	  delta.heapedgeadd.get(e.src).remove(e);
	} else {
	  //add edge to to the remove set
	  if (!delta.heapedgeremove.containsKey(e.src))
	    delta.heapedgeremove.put(e.src, new MySet<Edge>());
	  delta.heapedgeremove.get(e.src).add(e);
	}
      }
    }

    //Want to remove the set of external edges
    for(Edge e:externaledgeset) {
      //want to remove the set of internal edges
      if (e.src!=null) {
	if (delta.heapedgeadd.containsKey(e.src)&&delta.heapedgeadd.get(e.src).contains(e)) {
	  //remove edge if it is in the add set
	  delta.heapedgeadd.get(e.src).remove(e);
	} else {
	  //add edge to to the remove set
	  if (!delta.heapedgeremove.containsKey(e.src))
	    delta.heapedgeremove.put(e.src, new MySet<Edge>());
	  delta.heapedgeremove.get(e.src).add(e);
	}
      } else {
	if (delta.varedgeadd.containsKey(e.srcvar)&&delta.varedgeadd.get(e.srcvar).contains(e)) {
	  //remove edge if it is in the add set
	  delta.varedgeadd.get(e.srcvar).remove(e);
	} else {
	  //add edge to to the remove set
	  if (!delta.varedgeremove.containsKey(e.srcvar))
	    delta.varedgeremove.put(e.srcvar,new MySet<Edge>());
	  delta.varedgeremove.get(e.srcvar).add(e);
	}
      }
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

      //Handle the this temp
      processThisTargets(targetSet, graph, delta, newDelta, nodeset, tovisit, edgeset, tmpthis, null);

      //Go through each temp
      processParams(graph, delta, newDelta, nodeset, tovisit, edgeset, fcall, false);
      
      //Traverse all reachable nodes
      computeReachableNodes(graph, delta, newDelta, nodeset, tovisit, edgeset, null);

      //Compute call targets
      HashSet<MethodDescriptor> targets=computeTargets(fcall, newDelta);

      //Fix mapping
      fixMapping(fcall, targets, null, newDelta, callblock, callindex);

      //Compute edges into region to splice out
      computeExternalEdges(graph, delta, nodeset, null, externaledgeset);

      //Splice out internal edges
      removeEdges(delta, nodeset, edgeset, externaledgeset);

      graph.reachNode=nodeset;
      graph.reachEdge=edgeset;
      
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

      //Handle the this temp
      processThisTargets(targetSet, graph, delta, newDelta, nodeset, tovisit, edgeset, tmpthis, oldnodeset);

      //Go through each temp
      processParams(graph, delta, newDelta, nodeset, tovisit, edgeset, fcall, true);

      //Go through each new heap edge that starts from old node
      MySet<Edge> newedges=GraphManip.getDiffEdges(delta, oldnodeset);
      edgeset.addAll(newedges);
      for(Edge e:newedges) {
	if (!nodeset.contains(e.dst)&&!oldnodeset.contains(e.dst)) {
	  nodeset.add(e.dst);
	  tovisit.add(e.dst);
	}
      }
      
      //Traverse all reachable nodes
      computeReachableNodes(graph, delta, newDelta, nodeset, tovisit, edgeset, oldnodeset);

      //Compute call targets
      HashSet<MethodDescriptor> targets=computeTargets(fcall, newDelta);

      //add in new nodeset and edgeset
      oldnodeset.addAll(nodeset);
      oldedgeset.addAll(edgeset);

      //Fix mapping
      fixMapping(fcall, targets, oldedgeset, newDelta, callblock, callindex);

      //Compute edges into region to splice out
      computeExternalEdges(graph, delta, oldnodeset, nodeset, externaledgeset);

      //Splice out internal edges
      removeEdges(delta, nodeset, edgeset, externaledgeset);

      //Apply diffs to graph
      applyDiffs(graph, delta);
    }
    return delta;
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
	MySet<Edge> newedgeset=Util.setSubtract(parentedges, edgestoremove);
	graph.nodeMap.put(node, newedgeset);
      }
    }

    //Add heap edges
    for(Map.Entry<AllocNode, MySet<Edge>> e: delta.heapedgeadd.entrySet()) {
      AllocNode node=e.getKey();
      MySet<Edge> edgestoadd=e.getValue();
      //If we have not done a subtract, then 
      if (!graph.nodeMap.containsKey(node)) {
	//Copy the parent entry
	graph.nodeMap.put(node, (MySet<Edge>)graph.parent.nodeMap.get(node).clone());
      }
      graph.nodeMap.get(node).addAll(edgestoadd);
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
      } else {
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
    if (delta.getInit()) {
      HashSet<AllocNode> srcNodes=GraphManip.getNodes(graph, delta, src);
      HashSet<AllocNode> dstNodes=GraphManip.getNodes(graph, delta, dst);
      MySet<Edge> edgesToAdd=GraphManip.genEdges(srcNodes, fd, dstNodes);
      MySet<Edge> edgesToRemove=null;
      if (dstNodes.size()==1&&!dstNodes.iterator().next().isSummary()) {
	/* Can do a strong update */
	edgesToRemove=GraphManip.getEdges(graph, delta, dstNodes, fd);
      }
      /* Update diff */
      updateHeapDelta(graph, delta, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    } else {
      /* First look at new sources */
      MySet<Edge> edgesToAdd=new MySet<Edge>();
      HashSet<AllocNode> newSrcNodes=GraphManip.getDiffNodes(delta, src);
      HashSet<AllocNode> dstNodes=GraphManip.getDiffNodes(delta, dst);
      edgesToAdd.addAll(GraphManip.genEdges(newSrcNodes, fd, dstNodes));
      HashSet<AllocNode> newDstNodes=GraphManip.getDiffNodes(delta, dst);
      MySet<Edge> edgesToRemove=null;
      if (newDstNodes.size()!=0) {
	if (dstNodes.size()==1&&!dstNodes.iterator().next().isSummary()) {
	  /* Need to undo strong update */
	  if (graph.strongUpdateSet!=null) {
	    edgesToAdd.addAll(graph.strongUpdateSet);
	    graph.strongUpdateSet.clear();
	  }
	} else if (dstNodes.size()==0&&newDstNodes.size()==1&&!newDstNodes.iterator().next().isSummary()&&graph.strongUpdateSet==null) {
	  edgesToRemove=GraphManip.getEdges(graph, delta, dstNodes, fd);
	}
	HashSet<AllocNode> srcNodes=GraphManip.getDiffNodes(delta, src);
	edgesToAdd.addAll(GraphManip.genEdges(srcNodes, fd, newDstNodes));
      }
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
    } else {
      FlatCastNode fcn=(FlatCastNode) node;
      src=fcn.getSrc();
      dst=fcn.getDst();
    }
    if (delta.getInit()) {
      HashSet<AllocNode> srcnodes=GraphManip.getNodes(graph, delta, src);
      MySet<Edge> edgesToAdd=GraphManip.genEdges(src, srcnodes);
      MySet<Edge> edgesToRemove=GraphManip.getEdges(graph, delta, dst);
      updateVarDelta(graph, delta, dst, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    } else {
      /* First compute new src nodes */
      HashSet<AllocNode> newSrcNodes=GraphManip.getDiffNodes(delta, src);

      /* Compute the union, and then the set of edges */
      MySet<Edge> edgesToAdd=GraphManip.genEdges(src, newSrcNodes);
      
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
    if (delta.getInit()) {
      HashSet<AllocNode> srcnodes=GraphManip.getNodes(graph, delta, src);
      HashSet<AllocNode> fdnodes=GraphManip.getNodes(graph, delta, srcnodes, fd);
      MySet<Edge> edgesToAdd=GraphManip.genEdges(src, fdnodes);
      MySet<Edge> edgesToRemove=GraphManip.getEdges(graph, delta, dst);
      updateVarDelta(graph, delta, dst, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    } else {
      /* First compute new objects we read fields of */
      HashSet<AllocNode> allsrcnodes=GraphManip.getNodes(graph, delta, src);
      HashSet<AllocNode> difffdnodes=GraphManip.getDiffNodes(delta, allsrcnodes, fd);     
      /* Next compute new targets of fields */
      HashSet<AllocNode> newsrcnodes=GraphManip.getDiffNodes(delta, src);
      HashSet<AllocNode> newfdnodes=GraphManip.getNodes(graph, delta, newsrcnodes, fd);
      /* Compute the union, and then the set of edges */
      HashSet<AllocNode> newTargets=new HashSet<AllocNode>();
      newTargets.addAll(newfdnodes);
      newTargets.addAll(difffdnodes);
      MySet<Edge> edgesToAdd=GraphManip.genEdges(src, newTargets);      
      
      /* Compute set of edges to remove */
      MySet<Edge> edgesToRemove=GraphManip.getDiffEdges(delta, dst);      

      /* Update diff */
      updateVarDelta(graph, delta, dst, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    }
    return delta;
  }

  static void updateVarDelta(Graph graph, Delta delta, TempDescriptor tmp, MySet<Edge> edgestoAdd, MySet<Edge> edgestoRemove) {
    MySet<Edge> edgeAdd=delta.varedgeadd.get(tmp);
    MySet<Edge> edgeRemove=delta.varedgeremove.get(tmp);
    MySet<Edge> existingEdges=graph.getEdges(tmp);
    for(Edge e: edgestoRemove) {
      //remove edge from delta
      edgeAdd.remove(e);
      //if the edge is already in the graph, add an explicit remove to the delta
      if (existingEdges.contains(e))
	edgeRemove.add(e);
    }
    for(Edge e: edgestoAdd) {
      //Remove the edge from the remove set
      edgeRemove.remove(e);
      //Explicitly add it to the add set unless it is already in the graph
      if (!existingEdges.contains(e))
	edgeAdd.add(e);
    }
  }

  static void updateHeapDelta(Graph graph, Delta delta, MySet<Edge> edgestoAdd, MySet<Edge> edgestoRemove) {
    for(Edge e: edgestoRemove) {
      AllocNode src=e.src;
      MySet<Edge> edgeAdd=delta.heapedgeadd.get(src);
      MySet<Edge> existingEdges=graph.getEdges(src);
      //remove edge from delta
      edgeAdd.remove(e);
      //if the edge is already in the graph, add an explicit remove to the delta
      if (existingEdges.contains(e)) {
	MySet<Edge> edgeRemove=delta.heapedgeremove.get(src);
	edgeRemove.add(e);
      }
    }
    for(Edge e: edgestoAdd) {
      AllocNode src=e.src;
      MySet<Edge> edgeRemove=delta.heapedgeremove.get(src);
      MySet<Edge> existingEdges=graph.getEdges(src);
      //Remove the edge from the remove set
      edgeRemove.remove(e);
      //Explicitly add it to the add set unless it is already in the graph
      if (!existingEdges.contains(e)) {
	MySet<Edge> edgeAdd=delta.heapedgeadd.get(src);
	edgeAdd.add(e);
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
      delta.varedgeremove.put(tmp, graph.getEdges(tmp));
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

	removeSet.add(e.copy());
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
    Delta newdelta;
    Graph graph=bbgraphMap.get(block);

    if (newGraph) {
      newdelta=new Delta(null, true);
      //Add in heap edges and throw away original diff
      graph.nodeMap.putAll(delta.heapedgeadd);
      //Add in var edges and throw away original diff
      graph.varMap.putAll(delta.varedgeadd);
      //Record that this is initial set...
      graph.nodeAges.addAll(delta.addNodeAges);
      //Add old nodes
      for(Map.Entry<AllocNode, Boolean> oldentry:delta.addOldNodes.entrySet()) {
	if (oldentry.getValue().booleanValue()) {
	  graph.oldNodes.put(oldentry.getKey(), Boolean.TRUE);
	}
      }
    } else {
      newdelta=new Delta(null, false);
      //merge in heap edges and variables
      mergeHeapEdges(graph, delta, newdelta);
      mergeVarEdges(graph, delta, newdelta);
      mergeAges(graph, delta, newdelta);
    }
    return newdelta;
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

  void mergeVarEdges(Graph graph, Delta delta, Delta newdelta) {
    //Merge in edges
    for(Map.Entry<TempDescriptor, MySet<Edge>> varedge:delta.varedgeadd.entrySet()) {
      TempDescriptor tmpsrc=varedge.getKey();
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
	}
      }
      //Done with edge set...
      if (diffedges.size()>=0) {
	//completely new
	newdelta.basevaredge.put(tmpsrc,diffedges);
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