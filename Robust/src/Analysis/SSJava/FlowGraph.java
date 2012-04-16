package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import IR.Descriptor;
import IR.MethodDescriptor;

public class FlowGraph {

  MethodDescriptor md;

  Set<FlowNode> nodeSet;
  FlowNode thisVarNode;

  // maps the composite representation of field/var descriptors to infer nodes
  Map<NTuple<Descriptor>, FlowNode> mapDescTupleToInferNode;

  // maps an infer node to the set of neighbors which is pointed by the node
  Map<NTuple<Descriptor>, Set<FlowNode>> mapNodeToNeighborSet;

  boolean debug = true;

  public FlowGraph(MethodDescriptor md) {
    this.md = md;
    nodeSet = new HashSet<FlowNode>();
    mapDescTupleToInferNode = new HashMap<NTuple<Descriptor>, FlowNode>();
    mapNodeToNeighborSet = new HashMap<NTuple<Descriptor>, Set<FlowNode>>();

    // create a node for 'this' varialbe
    FlowNode thisNode = new FlowNode(null, md.getThis());
    NTuple<Descriptor> thisVarTuple = new NTuple<Descriptor>();
    thisVarTuple.add(md.getThis());
    mapDescTupleToInferNode.put(thisVarTuple, thisNode);
    thisVarNode = thisNode;

  }

  public void addNeighbor(FlowNode node, FlowNode neighbor) {
    Set<FlowNode> set = mapNodeToNeighborSet.get(node);
    if (set == null) {
      set = new HashSet<FlowNode>();
    }
    set.add(neighbor);

    System.out.println("add a new neighbor " + neighbor + " to " + node);
  }

  public void addValueFlowEdge(NTuple<Descriptor> fromDescTuple, NTuple<Descriptor> toDescTuple) {

    FlowNode fromNode = mapDescTupleToInferNode.get(fromDescTuple);
    FlowNode toNode = mapDescTupleToInferNode.get(toDescTuple);

    System.out.println("create an edge from " + fromNode + " to " + toNode);

    int fromTupleSize = fromDescTuple.size();
    NTuple<Descriptor> curTuple = new NTuple<Descriptor>();
    for (int i = 0; i < fromTupleSize; i++) {
      Descriptor desc = fromDescTuple.get(i);
      curTuple.add(desc);
      addNeighbor(getInferNode(curTuple), toNode);
    }

    int toTupleSize = toDescTuple.size();
    curTuple = new NTuple<Descriptor>();
    for (int i = 0; i < toTupleSize; i++) {
      Descriptor desc = toDescTuple.get(i);
      curTuple.add(desc);
      addNeighbor(fromNode, getInferNode(curTuple));
    }

  }

  public FlowNode getInferNode(NTuple<Descriptor> descTuple) {
    if (mapDescTupleToInferNode.containsKey(descTuple)) {
      return mapDescTupleToInferNode.get(descTuple);
    }
    return null;
  }

  public FlowNode getThisVarNode() {
    return thisVarNode;
  }

  public void createNewFlowNode(NTuple<Descriptor> base) {

    FlowNode node = new FlowNode(base);
    mapDescTupleToInferNode.put(base, node);

    System.out.println("Creating new node=" + node);

  }
  

}