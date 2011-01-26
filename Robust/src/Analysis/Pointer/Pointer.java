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
  State state;
  TypeUtil typeUtil;
  AllocFactory allocFactory;
  LinkedList<Delta> toprocess;

  public Pointer(State state, TypeUtil typeUtil) {
    this.state=state;
    this.blockMap=new HashMap<FlatMethod, BasicBlock>();
    this.bbgraphMap=new HashMap<BBlock, Graph>();
    this.graphMap=new HashMap<FlatNode, Graph>();
    this.typeUtil=typeUtil;
    this.allocFactory=new AllocFactory(state, typeUtil);
    this.toprocess=new LinkedList<Delta>();
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
    Delta delta=new Delta(start, true);
    HashSet<Edge> arrayset=new HashSet<Edge>();
    HashSet<Edge> varset=new HashSet<Edge>();
    arrayset.add(new Edge(allocFactory.StringArray, null, allocFactory.Strings));
    varset.add(new Edge(fm.getParameter(0), allocFactory.StringArray));
    delta.heapedgeadd.put(allocFactory.StringArray, arrayset);
    delta.varedgeadd.put(fm.getParameter(0), varset);
    return delta;
  }

  void doAnalysis() {
    toprocess.add(buildInitialContext());

    while(!toprocess.isEmpty()) {
      Delta delta=toprocess.remove();
      BBlock bblock=delta.getBlock();
      Vector<FlatNode> nodes=bblock.nodes();

      //Build base graph for entrance to this basic block
      delta=applyInitDelta(delta, bblock);
      Graph graph=bbgraphMap.get(bblock);

      //Compute delta at exit of each node
      for(int i=0; i<nodes.size();i++) {
	FlatNode currNode=nodes.get(i);
	if (!graphMap.containsKey(currNode)) {
	  graphMap.put(currNode, new Graph(graph));
	}
	Graph nodeGraph=graphMap.get(currNode);
	delta=processNode(currNode, delta, nodeGraph);
      }
      //XXXX: Need to generate new delta
    }    
  }

  Delta processNode(FlatNode node, Delta delta, Graph newgraph) {
    switch(node.kind()) {
    case FKind.FlatNew:
      return processNewNode((FlatNew)node, delta, newgraph);
    case FKind.FlatFieldNode:
    case FKind.FlatElementNode:
      return processFieldElementNode(node, delta, newgraph);
    case FKind.FlatCastNode:
    case FKind.FlatOpNode:
      return processCopyNode(node, delta, newgraph);
    case FKind.FlatSetFieldNode:
      return processSetFieldElementNode(node, delta, newgraph);
    case FKind.FlatMethod:
    case FKind.FlatCall:
    case FKind.FlatReturnNode:
    case FKind.FlatSetElementNode:
    case FKind.FlatExit:
    case FKind.FlatSESEEnterNode:
    case FKind.FlatSESEExitNode:

      throw new Error("Unimplemented node:"+node);
    default:
      throw new Error("Unrecognized node:"+node);
    }
  }

  void applyDiffs(Graph graph, Delta delta) {
    //Add hidden base edges
    for(Map.Entry<AllocNode, HashSet<Edge>> e: delta.baseheapedge.entrySet()) {
      AllocNode node=e.getKey();
      HashSet<Edge> edges=e.getValue();
      if (graph.nodeMap.containsKey(node)) {
	HashSet<Edge> nodeEdges=graph.nodeMap.get(node);
	nodeEdges.addAll(edges);
      }
    }

    //Remove heap edges
    for(Map.Entry<AllocNode, HashSet<Edge>> e: delta.heapedgeremove.entrySet()) {
      AllocNode node=e.getKey();
      HashSet<Edge> edgestoremove=e.getValue();
      if (graph.nodeMap.containsKey(node)) {
	//Just apply diff to current map
	graph.nodeMap.get(node).removeAll(edgestoremove);
      } else {
	//Generate diff from parent graph
	HashSet<Edge> parentedges=graph.parent.nodeMap.get(node);
	HashSet<Edge> newedgeset=Util.setSubtract(parentedges, edgestoremove);
	graph.nodeMap.put(node, newedgeset);
      }
    }

    //Add heap edges
    for(Map.Entry<AllocNode, HashSet<Edge>> e: delta.heapedgeadd.entrySet()) {
      AllocNode node=e.getKey();
      HashSet<Edge> edgestoadd=e.getValue();
      //If we have not done a subtract, then 
      if (!graph.nodeMap.containsKey(node)) {
	//Copy the parent entry
	graph.nodeMap.put(node, (HashSet<Edge>)graph.parent.nodeMap.get(node).clone());
      }
      graph.nodeMap.get(node).addAll(edgestoadd);
    }

    //Remove var edges
    for(Map.Entry<TempDescriptor, HashSet<Edge>> e: delta.varedgeremove.entrySet()) {
      TempDescriptor tmp=e.getKey();
      HashSet<Edge> edgestoremove=e.getValue();

      if (graph.varMap.containsKey(tmp)) {
	//Just apply diff to current map
	graph.varMap.get(tmp).removeAll(edgestoremove);
      } else {
	//Generate diff from parent graph
	HashSet<Edge> parentedges=graph.parent.varMap.get(tmp);
	HashSet<Edge> newedgeset=Util.setSubtract(parentedges, edgestoremove);
	graph.varMap.put(tmp, newedgeset);
      }
    }

    //Add var edges
    for(Map.Entry<TempDescriptor, HashSet<Edge>> e: delta.varedgeadd.entrySet()) {
      TempDescriptor tmp=e.getKey();
      HashSet<Edge> edgestoadd=e.getValue();
      graph.varMap.put(tmp, (HashSet<Edge>) edgestoadd.clone());
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
      HashSet<Edges> edgesToAdd=GraphManip.genEdges(srcNodes, fd, dstNodes);
      if (dstNodes.size()==1&&!dstNodes.iterator().next().isSummary()) {
	/* Can do a strong update */
	

      }
    } else {
      
      
    }
  }

  Delta processCopyNode(FlatNode node, Delta delta, Graph graph) {
    TempDescriptor src;
    TempDescriptor dst;
    if (node.kind()==FKind.FlatOpNode) {
      FlatOpNode fon=(FlatOpNode) node;
      src=fcn.getLeft();
      dst=fcn.getDst();
    } else {
      FlatCastNode fcn=(FlatCastNode) node;
      src=fcn.getSrc();
      dst=fcn.getDst();
    }
    if (delta.getInit()) {
      HashSet<AllocNode> srcnodes=GraphManip.getNodes(graph, delta, src);
      HashSet<Edge> edgesToAdd=GraphManip.genEdges(src, srcnodes);
      HashSet<Edge> edgesToRemove=GraphManip.getEdges(graph, delta, dst);
      updateVarDelta(graph, delta, dst, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    } else {
      /* First compute new src nodes */
      HashSet<AllocNode> newSrcNodes=GraphManip.getDiffNodes(delta, src);

      /* Compute the union, and then the set of edges */
      HashSet<Edge> edgesToAdd=GraphManip.genEdges(src, newSrcNodes);
      
      /* Compute set of edges to remove */
      HashSet<Edge> edgesToRemove=GraphManip.getDiffEdges(delta, dst);      

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
      HashSet<Edge> edgesToAdd=GraphManip.genEdges(src, fdnodes);
      HashSet<Edge> edgesToRemove=GraphManip.getEdges(graph, delta, dst);
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
      HashSet<Edge> edgesToAdd=GraphManip.genEdges(src, newTargets);      
      
      /* Compute set of edges to remove */
      HashSet<Edge> edgesToRemove=GraphManip.getDiffEdges(delta, dst);      

      /* Update diff */
      updateVarDelta(graph, delta, dst, edgesToAdd, edgesToRemove);
      applyDiffs(graph, delta);
    }
    return delta;
  }

  static void updateVarDelta(Graph graph, Delta delta, TempDescriptor tmp, HashSet<Edge> edgestoAdd, HashSet<Edge> edgestoRemove) {
    HashSet<Edge> edgeAdd=delta.varedgeadd.get(tmp);
    HashSet<Edge> edgeRemove=delta.varedgeremove.get(tmp);
    HashSet<Edge> existingEdges=graph.getEdges(tmp);
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

  static void updateHeapDelta(Graph graph, Delta delta, HashSet<Edge> edgestoAdd, HashSet<Edge> edgestoRemove) {
    /* Fix all of this */
    HashSet<Edge> edgeAdd=delta.varedgeadd.get(tmp);
    HashSet<Edge> edgeRemove=delta.varedgeremove.get(tmp);
    HashSet<Edge> existingEdges=graph.getEdges(tmp);
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

  Delta processNewNode(FlatNew node, Delta delta, Graph graph) {
    AllocNode summary=allocFactory.getAllocNode(node, true);
    AllocNode single=allocFactory.getAllocNode(node, false);
    TempDescriptor tmp=node.getDst();
      
    if (delta.getInit()) {
      //Build new Edge
      Edge e=new Edge(tmp, single);
      //Build new Edge set
      HashSet<Edge> newedges=new HashSet<Edge>();
      newedges.add(e);
      //Add it into the diffs
      delta.varedgeadd.put(tmp, newedges);
      //Remove the old edges
      delta.varedgeremove.put(tmp, graph.getEdges(tmp));
      //Apply incoming diffs to graph
      applyDiffs(graph, delta);
    } else {
      /* 1. Fix up the variable edge additions */

      for(Iterator<Map.Entry<TempDescriptor, HashSet<Edge>>> entryIt=delta.varedgeadd.entrySet().iterator();entryIt.hasNext();) {
	Map.Entry<TempDescriptor, HashSet<Edge>> entry=entryIt.next();

	if (entry.getKey()==tmp) {
	  /* Check if this is the tmp we overwrite */
	  entryIt.remove();
	} else {
	  /* Otherwise, check if the target of the edge is changed... */
	  rewriteSet(entry.getValue(), graph.varMap.get(entry.getKey()), single, summary);
	}
      }
      
      /* 2. Fix up the base variable edges */

      for(Iterator<Map.Entry<TempDescriptor, HashSet<Edge>>> entryIt=delta.basevaredge.entrySet().iterator();entryIt.hasNext();) {
	Map.Entry<TempDescriptor, HashSet<Edge>> entry=entryIt.next();
	TempDescriptor entrytmp=entry.getKey();
	if (entrytmp==tmp) {
	  /* Check is this is the tmp we overwrite, if so add to remove set */
	  Util.relationUpdate(delta.varedgeremove, tmp, null, entry.getValue());
	} else {
	  /* Check if the target of the edge is changed */ 
	  HashSet<Edge> newset=(HashSet<Edge>)entry.getValue().clone();
	  HashSet<Edge> removeset=shrinkSet(newset, graph.varMap.get(entrytmp), single, summary);
	  Util.relationUpdate(delta.varedgeremove, entrytmp, newset, removeset);
	  Util.relationUpdate(delta.varedgeadd, entrytmp, null, newset);
	}
      }


      /* 3. Fix up heap edge additions */

      HashMap<AllocNode, HashSet<Edge>> addheapedge=new HashMap<AllocNode, HashSet<Edge>>();
      for(Iterator<Map.Entry<AllocNode, HashSet<Edge>>> entryIt=delta.heapedgeadd.entrySet().iterator();entryIt.hasNext();) {
	Map.Entry<AllocNode, HashSet<Edge>> entry=entryIt.next();
	HashSet<Edge> edgeset=entry.getValue();
	AllocNode allocnode=entry.getKey();
	if (allocnode==single) {
	  entryIt.remove();
	  rewriteSet(edgeset, graph.nodeMap.get(summary), single, summary);
	  addheapedge.put(summary, edgeset);
	} else {
	  rewriteSet(edgeset, graph.nodeMap.get(allocnode), single, summary);
	}
      }
      
      /* Merge in diffs */

      for(Map.Entry<AllocNode, HashSet<Edge>> entry:addheapedge.entrySet()) {
	AllocNode allocnode=entry.getKey();
	Util.relationUpdate(delta.heapedgeadd, allocnode, null. entry.getValue());
      }

      /* 4. Fix up the base heap edges */

      for(Iterator<Map.Entry<AllocNode, HashSet<Edge>>> entryIt=delta.baseheapedge.entrySet().iterator();entryIt.hasNext();) {
	Map.Entry<AllocNode, HashSet<Edge>> entry=entryIt.next();
	HashSet<Edge> edgeset=entry.getValue();
	AllocNode allocnode=entry.getKey();
	if (allocnode==single) {
	  entryIt.remove();
	}
	AllocNode addnode=(allocnode==single)?summary:allocnode;

	HashSet<Edge> newset=(HashSet<Edge>) edgeset.clone();
	HashSet<Edge> removeset=shrinkSet(newset, graph.nodeMap.get(addnode), single, summary);
	Util.relationUpdate(delta.heapedgeadd, addnode, null, newset);
	Util.relationUpdate(delta.heapedgeremove, allocnode, null, removeset);
      }
      
      //Apply incoming diffs to graph
      applyDiffs(graph, delta);      
    }
    return delta;
  }

  void rewriteSet(HashSet<Edge> edgeset, HashSet<Edge> oldedgeset, AllocNode oldnode, AllocNode newnode) {
    HashSet<Edge> newSet=null;
    for(Iterator<Edge> edgeit=edgeset.iterator();edgeit.hasNext();) {
      Edge e=edgeit.next();
      if (e.dst==oldnode||e.src==oldnode) {
	if (newSet==null) {
	  newSet=new HashSet<Edge>();
	}
	edgeit.remove();
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
  }

  /* Shrinks the incoming set to just include rewritten values.
   * Returns a set of the original rewritten values */

  HashSet<Edge> shrinkSet(HashSet<Edge> edgeset, HashSet<Edge> oldedgeset, AllocNode oldnode, AllocNode newnode) {
    HashSet<Edge> newSet=null;
    HashSet<Edge> removeSet=null;
    for(Iterator<Edge> edgeit=edgeset.iterator();edgeit.hasNext();) {
      Edge e=edgeit.next();
      edgeit.remove();
      if (e.dst==oldnode||e.src==oldnode) {
	if (newSet==null) {
	  newSet=new HashSet<Edge>();
	  removeSet=new HashSet<Edge>();
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
    } else {
      newdelta=new Delta(null, false);
      //merge in heap edges and variables
      mergeHeapEdges(graph, delta, newdelta);
      mergeVarEdges(graph, delta, newdelta);
      //Record that this is a diff
      newdelta.setInit(false);
    }
    return newdelta;
  }

  /* This function merges in the heap edges.  It updates delta to be
   * the difference */

  void mergeHeapEdges(Graph graph, Delta delta, Delta newdelta) {
    //Merge in edges
    for(Map.Entry<AllocNode, HashSet<Edge>> heapedge:delta.heapedgeadd.entrySet()) {
      AllocNode nsrc=heapedge.getKey();
      HashSet<Edge> edges=heapedge.getValue();
      if (!graph.nodeMap.containsKey(nsrc)) {
	graph.nodeMap.put(nsrc, new HashSet<Edge>());
      }
      HashSet<Edge> dstedges=graph.nodeMap.get(nsrc);
      HashSet<Edge> diffedges=new HashSet<Edge>();
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
    for(Map.Entry<TempDescriptor, HashSet<Edge>> varedge:delta.varedgeadd.entrySet()) {
      TempDescriptor tmpsrc=varedge.getKey();
      HashSet<Edge> edges=varedge.getValue();
      if (!graph.varMap.containsKey(tmpsrc)) {
	graph.varMap.put(tmpsrc, new HashSet<Edge>());
      }
      HashSet<Edge> dstedges=graph.varMap.get(tmpsrc);
      HashSet<Edge> diffedges=new HashSet<Edge>();
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
}