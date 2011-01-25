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
    delta.addHeapEdge(allocFactory.StringArray, new Edge(allocFactory.StringArray, null, allocFactory.Strings));
    delta.addVarEdge(fm.getParameter(0), new Edge(fm.getParameter(0), allocFactory.StringArray));
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

      Graph prevGraph=graph;
      //Compute delta at exit of each node
      for(int i=0; i<nodes.size();i++) {
	FlatNode currNode=nodes.get(i);
	if (!graphMap.containsKey(currNode)) {
	  graphMap.put(currNode, new Graph(graph));
	}
	Graph nodeGraph=graphMap.get(currNode);
	delta=processNode(currNode, delta, prevGraph, nodeGraph);
	prevgraph=nodeGraph;
      }
      //XXXX: Need to generate new delta
    }    
  }

  Delta processNode(FlatNode node, Delta delta, Graph newgraph) {
    switch(node.kind()) {
    case FKind.FlatNew:
      return processNewNode(node, delta, newgraph);
      break;
    case FKind.FlatCall:
    case FKind.FlatFieldNode:
    case FKind.FlatSetFieldNode:
    case FKind.FlatReturnNode:
    case FKind.FlatElementNode:
    case FKind.FlatSetElementNode:
    case FKind.FlatMethod:
    case FKind.FlatExit:
    case FKind.FlatSESEEnterNode:
    case FKind.FlatSESEExitNode:
    case FKind.FlatCastNode:
    case FKind.FlatOpNode:
      throw new Error("Unimplemented node:"+node);
      break;
    default:
      throw new Error("Unrecognized node:"+node);
    }
  }

  void applyDiffs(Graph graph, Delta delta) {
    //Add hidden base edges
    for(Map.Entry<AllocNode, HashSet<Edge>> e: delta.baseheapedge) {
      AllocNode node=e.getKey();
      HashSet<Edge> edges=e.getValue();
      if (graph.nodeMap.containsKey(node)) {
	HashSet<Edge> nodeEdges=graph.nodeMap.get(node);
	nodeEdges.addAll(edges);
      }
    }

    //Remove heap edges
    for(Map.Entry<AllocNode, HashSet<Edge>> e: delta.heapedgeremove) {
      AllocNode node=e.getKey();
      HashSet<Edge> edgestoremove=e.getValue();
      if (graph.nodeMap.containsKey(node)) {
	//Just apply diff to current map
	graph.nodeMap.get(node).removeAll(edgestoremove);
      } else {
	//Generate diff from parent graph
	HashSet<Edge> parentedges=graph.parent.nodeMap.get(node);
	HashSet<Edge> newedgeset=Util.setSubstract(parentedges, edgestoremove);
	graph.nodeMap.put(node, newedgeset);
      }
    }

    //Add heap edges
    for(Map.Entry<AllocNode, HashSet<Edge>> e: delta.heapedgeadd) {
      AllocNode node=e.getKey();
      HashSet<Edge> edgestoadd=e.getValue();
      //If we have not done a subtract, then 
      if (!graph.nodeMap.containsKey(node)) {
	//Copy the parent entry
	graph.nodeMap.put(node, graph.parent.nodeMap.get(node).clone());
      }
      graph.nodeMap.get(node).addAll(edgestoadd);
    }

    //Remove var edges
    for(Map.Entry<TempDescriptor, HashSet<Edge>> e: delta.varedgeremove) {
      TempDescriptor tmp=e.getKey();
      HashSet<Edge> edgestoremove=e.getValue();

      if (graph.varMap.containsKey(tmp)) {
	//Just apply diff to current map
	graph.varMap.get(tmp).removeAll(edgestoremove);
      } else {
	//Generate diff from parent graph
	HashSet<Edge> parentedges=graph.parent.varMap.get(node);
	HashSet<Edge> newedgeset=Util.setSubstract(parentedges, edgestoremove);
	graph.varMap.put(tmp, newedgeset);
      }
    }

    //Add var edges
    for(Map.Entry<TempDescriptor, HashSet<Edge>> e: delta.varedgeadd) {
      TempDescriptor tmp=e.getKey();
      HashSet<Edge> edgestoadd=e.getValue();
      graph.varmap.put(tmp, edgestoadd.clone());
    }
  }

  Delta processNewNode(FlatNew node, Delta delta, Graph newgraph) {
    AllocNode summary=allocFactory.getAllocNode(node, true);
    AllocNode single=allocFactory.getAllocNode(node, false);
    TempDescriptor tmp=node.getDst();
      
    if (delta.getInit()) {
      //Apply incoming diffs to graph
      applyDiffs(graph, delta);
      //Build new Edge
      Edge e=new Edge(tmp, single);
      //Build new Edge set
      HashSet<Edge> newedges=new HashSet<Edge>();
      newedges.add(e);
      //Add it into the graph
      graph.varMap.put(tmp, newedges);
      //Add it into the diffs
      delta.varedgeadd.put(tmp, newedges);
    } else {
      //Filter var edge additions
      for(Iterator<Map.Entry<TempDescriptor, HashSet<Edge>>> entryIt=delta.varedgeadd;entryIt.hasNext();) {
	Map.Entry<TempDescriptor, HashSet<Edge>> entry=entryIt.next();
	//Check first if this is the tmp we overwrite
	//Check second if the edge is changed...
	if (entry.getKey()==tmp)
	  entryIt.remove();
	else for(Edge edge:entryIt.getValue()) {
	    if (edge.dst==single)
	      edge.dst=summary;
	  }
      }

      //Filter heap edge additions
      for(Iterator<Map.Entry<AllocNode, HashSet<Edge>>> entryIt=delta.heapedgeadd;entryIt.hasNext();) {
	Map.Entry<AllocNode, HashSet<Edge>> entry=entryIt.next();
	
      }

      //Get relevant changes
      HashSet<Edge> edgesadd=delta.heapedgeadd.get(single);
      HashSet<Edge> edgesremove=delta.heapedgeremove.get(single);
      HashSet<Edge> baseedges=delta.baseheapedge.get(single);
      HashSet<Edge> basebackedges=delta.baseheapedge.get(single);

      //Get summary node edges
      HashSet<Edge> summarynewedgesadd;
      if (!delta.heapedgeadd.containsKey(summary)) {
      	summarynewedgesadd=new HashSet<Edge>();
	delta.heapedgeadd.put(summary, summarynewedgesadd);
      } else
	summarynewedgesadd=delta.heapedgeadd.get(summary);

      

      //Apply diffs
      delta.heapedgeremove.put(single, baseedges.clone());
      delta.heapedgeadd.remove(single);
      delta.baseheapedge.remove(single);

      //Apply incoming diffs to graph
      applyDiffs(graph, delta);      
    }
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
	newdelta.baseheap.put(nsrc, diffedges);
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
      if (!graph.nodeMap.containsKey(tmpsrc)) {
	graph.nodeMap.put(tmpsrc, new HashSet<Edge>());
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