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
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.VarDescriptor;

public class FlowGraph {

  MethodDescriptor md;

  Set<FlowNode> nodeSet;
  Set<FlowNode> returnNodeSet;
  FlowNode thisVarNode;

  // maps the composite representation of field/var descriptors to infer nodes
  Map<NTuple<Descriptor>, FlowNode> mapDescTupleToInferNode;

  // maps an infer node to the set of neighbors which is pointed by the node
  Map<NTuple<Descriptor>, Set<FlowNode>> mapNodeToNeighborSet;

  // maps a paramter descriptor to its index
  Map<Descriptor, Integer> mapParamDescToIdx;
  boolean debug = true;

  public FlowGraph(MethodDescriptor md, Map<Descriptor, Integer> mapParamDescToIdx) {
    this.md = md;
    this.nodeSet = new HashSet<FlowNode>();
    this.mapDescTupleToInferNode = new HashMap<NTuple<Descriptor>, FlowNode>();
    this.mapNodeToNeighborSet = new HashMap<NTuple<Descriptor>, Set<FlowNode>>();
    this.mapParamDescToIdx = new HashMap<Descriptor, Integer>();
    this.mapParamDescToIdx.putAll(mapParamDescToIdx);
    this.returnNodeSet = new HashSet<FlowNode>();

    // create a node for 'this' varialbe
    NTuple<Descriptor> thisDescTuple = new NTuple<Descriptor>();
    thisDescTuple.add(md.getThis());
    FlowNode thisNode = new FlowNode(thisDescTuple, true);
    NTuple<Descriptor> thisVarTuple = new NTuple<Descriptor>();
    thisVarTuple.add(md.getThis());
    createNewFlowNode(thisVarTuple);
    thisVarNode = thisNode;

  }

  public Set<FlowNode> getNodeSet() {
    return nodeSet;
  }

  public Set<FlowNode> getParameterNodeSet() {
    Set<FlowNode> paramNodeSet = new HashSet<FlowNode>();
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      FlowNode fn = (FlowNode) iterator.next();
      if (fn.isParameter()) {
        paramNodeSet.add(fn);
      }
    }
    return paramNodeSet;
  }

  public void addNeighbor(FlowNode node, FlowNode neighbor) {
    Set<FlowNode> set = mapNodeToNeighborSet.get(node);
    if (set == null) {
      set = new HashSet<FlowNode>();
    }
    set.add(neighbor);

    System.out.println("add a new neighbor " + neighbor + " to " + node);
  }

  public boolean hasEdge(NTuple<Descriptor> fromDescTuple, NTuple<Descriptor> toDescTuple) {

    FlowNode fromNode = mapDescTupleToInferNode.get(fromDescTuple);
    FlowNode toNode = mapDescTupleToInferNode.get(toDescTuple);

    Set<FlowEdge> fromNodeOutEdgeSet = fromNode.getOutEdgeSet();
    for (Iterator iterator = fromNodeOutEdgeSet.iterator(); iterator.hasNext();) {
      FlowEdge flowEdge = (FlowEdge) iterator.next();
      if (flowEdge.getDst().equals(toNode)) {
        return true;
      } else {
        if (hasEdge(flowEdge.getDst().getDescTuple(), toDescTuple)) {
          return true;
        }
      }
    }

    return false;
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
      FlowNode node = createNewFlowNode(descTuple);
      return node;
    }
  }

  public FlowNode getThisVarNode() {
    return thisVarNode;
  }

  public FlowNode createNewFlowNode(NTuple<Descriptor> tuple) {

    if (!mapDescTupleToInferNode.containsKey(tuple)) {

      FlowNode node = new FlowNode(tuple, isParamter(tuple));
      mapDescTupleToInferNode.put(tuple, node);
      nodeSet.add(node);

      if (tuple.size() > 1) {
        NTuple<Descriptor> baseTuple = tuple.subList(0, tuple.size() - 1);
        getFlowNode(baseTuple).addFieldNode(node);
      }

      System.out.println("Creating new node=" + node);
      return node;
    } else {
      return mapDescTupleToInferNode.get(tuple);
    }

  }

  public void setReturnFlowNode(NTuple<Descriptor> tuple) {

    if (!mapDescTupleToInferNode.containsKey(tuple)) {
      createNewFlowNode(tuple);
    }

    FlowNode node = mapDescTupleToInferNode.get(tuple);
    node.setReturn(true);

    returnNodeSet.add(node);
  }

  public Set<FlowNode> getReturnNodeSet() {
    return returnNodeSet;
  }

  public boolean isParamter(NTuple<Descriptor> tuple) {
    // return true if a descriptor tuple is started with a parameter descriptor
    Descriptor firstIdxDesc = tuple.get(0);
    return mapParamDescToIdx.containsKey(firstIdxDesc);
  }

  public int getParamIdx(NTuple<Descriptor> tuple) {
    Descriptor firstDesc = tuple.get(0);
    return mapParamDescToIdx.get(firstDesc).intValue();
  }

  private void drawEdges(FlowNode node, BufferedWriter bw, Set<FlowNode> addedNodeSet,
      Set<FlowEdge> addedEdgeSet) throws IOException {

    Set<FlowEdge> edgeSet = node.getOutEdgeSet();

    for (Iterator<FlowEdge> iterator = edgeSet.iterator(); iterator.hasNext();) {
      FlowEdge flowEdge = iterator.next();

      FlowNode u = flowEdge.getSrc();
      FlowNode v = flowEdge.getDst();

      if (u.getDescTuple().equals(flowEdge.getInitTuple())
          && v.getDescTuple().equals(flowEdge.getEndTuple())) {
        // only draw an edge of the actual value flow

        if (!addedEdgeSet.contains(flowEdge)) {

          if (!addedNodeSet.contains(u)) {
            drawNode(u, bw);
            addedNodeSet.add(u);
          }
          if (!addedNodeSet.contains(v)) {
            drawNode(v, bw);
            addedNodeSet.add(v);
          }

          bw.write("" + u.getID() + " -> " + v.getID() + ";\n");
          addedEdgeSet.add(flowEdge);
        }
      }

    }

  }

  private void drawNode(FlowNode node, BufferedWriter bw) throws IOException {
    bw.write(node.getID() + " [label=\"" + node.getPrettyID() + "\"]" + ";\n");
  }

  public void writeGraph() throws java.io.IOException {

    String graphName = "flowgraph_" + md.toString();
    graphName = graphName.replaceAll("[\\W]", "");

    BufferedWriter bw = new BufferedWriter(new FileWriter(graphName + ".dot"));
    bw.write("digraph " + graphName + " {\n");
    bw.write("compound=true;\n");

    // then visit every flow node

    Iterator<FlowNode> iter = nodeSet.iterator();

    Set<FlowEdge> addedEdgeSet = new HashSet<FlowEdge>();
    Set<FlowNode> addedNodeSet = new HashSet<FlowNode>();

    while (iter.hasNext()) {
      FlowNode node = iter.next();

      if (node.getDescTuple().size() == 1) {
        // here, we just care about the local variable
        if (node.getFieldNodeSet().size() > 0) {
          drawSubgraph(node, bw, addedEdgeSet);
        }
      }
      drawEdges(node, bw, addedNodeSet, addedEdgeSet);

    }

    bw.write("}\n");
    bw.close();

  }

  public boolean constainsNode(FlowNode node) {
    return nodeSet.contains(node);
  }

  private void drawSubgraph(FlowNode node, BufferedWriter bw, Set<FlowEdge> addedSet)
      throws IOException {

    bw.write("subgraph cluster_" + node.getID() + "{\n");
    bw.write("label=\"" + node.getPrettyID() + "\";\n");

    Set<FlowNode> fieldNodeSet = node.getFieldNodeSet();
    for (Iterator iterator = fieldNodeSet.iterator(); iterator.hasNext();) {
      FlowNode fieldNode = (FlowNode) iterator.next();
      if (fieldNode.getFieldNodeSet().size() > 0) {
        drawSubgraph(fieldNode, bw, addedSet);
      } else {
        Descriptor desc = fieldNode.getDescTuple().getLastElement();
        if (desc instanceof VarDescriptor) {
          VarDescriptor varDesc = (VarDescriptor) desc;
          if (varDesc.getType().isPrimitive()) {
            bw.write(fieldNode.getID() + " [label=\"" + fieldNode.getPrettyID() + "\"];\n");
          }
        } else if (desc instanceof FieldDescriptor) {
          FieldDescriptor fieldDesc = (FieldDescriptor) desc;
          if (fieldDesc.getType().isPrimitive()) {
            bw.write(fieldNode.getID() + " [label=\"" + fieldNode.getPrettyID() + "\"];\n");
          }
        }
      }
    }

    bw.write("}\n");
  }
}