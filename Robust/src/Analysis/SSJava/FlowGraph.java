package Analysis.SSJava;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import Analysis.OoOJava.ConflictEdge;
import Analysis.OoOJava.ConflictNode;
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
    NTuple<Descriptor> thisDescTuple = new NTuple<Descriptor>();
    thisDescTuple.add(md.getThis());
    FlowNode thisNode = new FlowNode(thisDescTuple);
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
      addFlowEdge(getFlowNode(curTuple), toNode, fromDescTuple, toDescTuple);
    }

    int toTupleSize = toDescTuple.size();
    curTuple = new NTuple<Descriptor>();
    for (int i = 0; i < toTupleSize; i++) {
      Descriptor desc = toDescTuple.get(i);
      curTuple.add(desc);
      addFlowEdge(fromNode, getFlowNode(curTuple), fromDescTuple, toDescTuple);
    }

  }

  private void addFlowEdge(FlowNode fromNode, FlowNode toNode, NTuple<Descriptor> initTuple,
      NTuple<Descriptor> endTuple) {

    FlowEdge edge = new FlowEdge(fromNode, toNode, initTuple, endTuple);

    fromNode.addOutEdge(edge);

    System.out.println("add a new edge=" + edge);

  }

  public FlowNode getFlowNode(NTuple<Descriptor> descTuple) {
    if (mapDescTupleToInferNode.containsKey(descTuple)) {
      return mapDescTupleToInferNode.get(descTuple);
    } else {
      FlowNode node = new FlowNode(descTuple);
      mapDescTupleToInferNode.put(descTuple, node);
      return node;
    }
  }

  public FlowNode getThisVarNode() {
    return thisVarNode;
  }

  public void createNewFlowNode(NTuple<Descriptor> tuple) {

    if (!mapDescTupleToInferNode.containsKey(tuple)) {
      FlowNode node = new FlowNode(tuple);
      mapDescTupleToInferNode.put(tuple, node);
      nodeSet.add(node);

      if (tuple.size() > 1) {
        NTuple<Descriptor> baseTuple = tuple.subList(0, tuple.size() - 1);
        getFlowNode(baseTuple).addFieldNode(node);
      }

      System.out.println("Creating new node=" + node);
    }

  }

  public void writeGraph() throws java.io.IOException {

    String graphName = md.toString();
    graphName = graphName.replaceAll("[\\W]", "");

    BufferedWriter bw = new BufferedWriter(new FileWriter(graphName + ".dot"));
    bw.write("digraph " + graphName + " {\n");
    bw.write("compound=true;\n");

    // then visit every flow node

    Iterator<FlowNode> iter = nodeSet.iterator();

    Set<FlowEdge> addedSet = new HashSet<FlowEdge>();

    while (iter.hasNext()) {
      FlowNode node = iter.next();

      if (node.getFieldNodeSet().size() > 0) {
        drawSubgraph(node, bw);
      }

      String attributes = " [";

      attributes += "label=\"" + node.getID() + "\"]";

      bw.write(node.getID() + attributes + ";\n");

      Set<FlowEdge> edgeSet = node.getOutEdgeSet();

      for (Iterator<FlowEdge> iterator = edgeSet.iterator(); iterator.hasNext();) {
        FlowEdge flowEdge = iterator.next();

        FlowNode u = flowEdge.getSrc();
        FlowNode v = flowEdge.getDst();

        if (!addedSet.contains(flowEdge)) {
          bw.write("" + u.getID() + " -> " + v.getID() + ";\n");
          addedSet.add(flowEdge);
        }

      }
    }

    bw.write("graphTitle[label=\"" + graphName + "\",shape=box];\n");

    bw.write("}\n");
    bw.close();

  }

  private void drawSubgraph(FlowNode node, BufferedWriter bw) throws IOException {

    bw.write("  subgraph sg" + node.getID() + "{\n");
    // bw.write("  color=gray;\n");
    bw.write("  label=\"" + node.getID() + "\";\n");

    Set<FlowNode> fieldNodeSet = node.getFieldNodeSet();
    for (Iterator iterator = fieldNodeSet.iterator(); iterator.hasNext();) {
      FlowNode fieldNode = (FlowNode) iterator.next();
      String attribute = fieldNode.getID() + ";\n";
      bw.write("  " + attribute);
    }

    bw.write("  }\n");
  }
}