package Analysis.SSJava;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import IR.ClassDescriptor;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.NameDescriptor;
import IR.VarDescriptor;

public class FlowGraph {

  MethodDescriptor md;

  Set<FlowNode> nodeSet;
  Set<FlowNode> returnNodeSet;
  FlowNode thisVarNode;

  Map<NTuple<Location>, FlowNode> mapLocTupleToFlowNode;
  Map<FlowNode, NTuple<Location>> mapFlowNodeToLocTuple;

  // maps the composite representation of field/var descriptors to infer nodes
  Map<NTuple<Descriptor>, FlowNode> mapDescTupleToInferNode;

  // maps an infer node to the set of neighbors which is pointed by the node
  Map<NTuple<Descriptor>, Set<FlowNode>> mapNodeToNeighborSet;

  // maps a paramter descriptor to its index
  Map<Descriptor, Integer> mapParamDescToIdx;

  Map<Integer, FlowNode> mapIdxToFlowNode;

  public static int interseed = 0;

  boolean debug = true;

  public FlowGraph(MethodDescriptor md, Map<Descriptor, Integer> mapParamDescToIdx) {
    this.md = md;
    this.mapFlowNodeToLocTuple = new HashMap<FlowNode, NTuple<Location>>();
    this.mapLocTupleToFlowNode = new HashMap<NTuple<Location>, FlowNode>();
    this.nodeSet = new HashSet<FlowNode>();
    this.mapDescTupleToInferNode = new HashMap<NTuple<Descriptor>, FlowNode>();
    this.mapNodeToNeighborSet = new HashMap<NTuple<Descriptor>, Set<FlowNode>>();
    this.mapParamDescToIdx = new HashMap<Descriptor, Integer>();
    this.mapParamDescToIdx.putAll(mapParamDescToIdx);
    this.returnNodeSet = new HashSet<FlowNode>();
    this.mapIdxToFlowNode = new HashMap<Integer, FlowNode>();

    if (!md.isStatic()) {
      // create a node for 'this' varialbe
      NTuple<Descriptor> thisDescTuple = new NTuple<Descriptor>();
      thisDescTuple.add(md.getThis());
      FlowNode thisNode = new FlowNode(thisDescTuple, true);
      NTuple<Descriptor> thisVarTuple = new NTuple<Descriptor>();
      thisVarTuple.add(md.getThis());
      createNewFlowNode(thisVarTuple);
      thisVarNode = thisNode;
    }

    setupMapIdxToDesc();

  }

  public FlowNode createIntermediateNode() {
    NTuple<Descriptor> tuple = new NTuple<Descriptor>();
    Descriptor interDesc = new InterDescriptor(LocationInference.INTERLOC + interseed);
    tuple.add(interDesc);
    interseed++;
    FlowNode node = createNewFlowNode(tuple, true);
    return node;
  }

  private void setupMapIdxToDesc() {

    Set<Descriptor> descSet = mapParamDescToIdx.keySet();
    for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
      Descriptor paramDesc = (Descriptor) iterator.next();
      int idx = mapParamDescToIdx.get(paramDesc);
      NTuple<Descriptor> descTuple = new NTuple<Descriptor>();
      descTuple.add(paramDesc);
      mapIdxToFlowNode.put(idx, getFlowNode(descTuple));
    }

  }

  public FlowNode getParamFlowNode(int idx) {
    return mapIdxToFlowNode.get(idx);
  }

  public Set<FlowNode> getNodeSet() {
    return nodeSet;
  }

  public MethodDescriptor getMethodDescriptor() {
    return md;
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

    // System.out.println("add a new neighbor " + neighbor + " to " + node);
  }

  public boolean isParamDesc(Descriptor desc) {

    if (mapParamDescToIdx.containsKey(desc)) {
      int idx = mapParamDescToIdx.get(desc);
      if (!md.isStatic() && idx == 0) {
        return false;
      }
      return true;
    }

    return false;
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

    FlowNode fromNode = getFlowNode(fromDescTuple);
    FlowNode toNode = getFlowNode(toDescTuple);

    // System.out.println("create an edge from " + fromNode + " to " + toNode);

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

    // System.out.println("add a new edge=" + edge);

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
    return createNewFlowNode(tuple, false);
  }

  public FlowNode createNewFlowNode(NTuple<Descriptor> tuple, boolean isIntermediate) {

    if (!mapDescTupleToInferNode.containsKey(tuple)) {
      FlowNode node = new FlowNode(tuple, isParameter(tuple));
      node.setIntermediate(isIntermediate);
      mapDescTupleToInferNode.put(tuple, node);
      nodeSet.add(node);

      mapLocTupleToFlowNode.put(getLocationTuple(node), node);

      if (tuple.size() > 1) {
        NTuple<Descriptor> baseTuple = tuple.subList(0, tuple.size() - 1);
        getFlowNode(baseTuple).addFieldNode(node);
      }

      // System.out.println("Creating new node=" + node);
      return node;
    } else {
      return mapDescTupleToInferNode.get(tuple);
    }

  }

  public void addReturnFlowNode(NTuple<Descriptor> tuple) {

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

  public Set<FlowNode> getReachableFlowNodeSet(FlowNode fn) {
    Set<FlowNode> set = new HashSet<FlowNode>();
    getReachableFlowNodeSet(fn, set);
    return set;
  }

  private void getReachableFlowNodeSet(FlowNode fn, Set<FlowNode> visited) {

    for (Iterator iterator = fn.getOutEdgeSet().iterator(); iterator.hasNext();) {
      FlowEdge edge = (FlowEdge) iterator.next();

      if (fn.equals(getFlowNode(edge.getInitTuple()))) {

        FlowNode dstNode = getFlowNode(edge.getEndTuple());

        if (!visited.contains(dstNode)) {
          visited.add(dstNode);
          getReachableFlowNodeSet(dstNode, visited);
        }
      }
    }

  }

  public Set<NTuple<Location>> getReachableFlowTupleSet(Set<NTuple<Location>> visited, FlowNode fn) {
    for (Iterator iterator = fn.getOutEdgeSet().iterator(); iterator.hasNext();) {
      FlowEdge edge = (FlowEdge) iterator.next();

      if (fn.getDescTuple().equals(edge.getInitTuple())) {
        FlowNode dstNode = getFlowNode(edge.getEndTuple());
        NTuple<Location> dstTuple = getLocationTuple(dstNode);

        if (!visited.contains(dstTuple)) {
          visited.add(dstTuple);
          visited.addAll(getReachableFlowTupleSet(visited, dstNode));
        }

      }
    }
    return visited;
  }

  public NTuple<Location> getLocationTuple(NTuple<Descriptor> descTuple) {
    return getLocationTuple(getFlowNode(descTuple));
  }

  public NTuple<Location> getLocationTuple(FlowNode fn) {

    if (!mapFlowNodeToLocTuple.containsKey(fn)) {
      NTuple<Descriptor> descTuple = fn.getDescTuple();
      NTuple<Location> locTuple = new NTuple<Location>();
      ClassDescriptor cd = null;

      Descriptor localDesc = fn.getDescTuple().get(0);

      if (fn.isIntermediate()) {
        Location interLoc = new Location(md, localDesc.getSymbol());
        interLoc.setLocDescriptor(localDesc);
        locTuple.add(interLoc);
      } else if (localDesc.getSymbol().equals(LocationInference.TOPLOC)) {
        Location topLoc = new Location(md, Location.TOP);
        topLoc.setLocDescriptor(LocationInference.TOPDESC);
        locTuple.add(topLoc);
      } else if (localDesc.getSymbol().equals(LocationInference.GLOBALLOC)) {
        Location globalLoc = new Location(md, LocationInference.GLOBALLOC);
        globalLoc.setLocDescriptor(LocationInference.GLOBALDESC);
        locTuple.add(globalLoc);
      } else {
        // normal case
        for (int i = 0; i < descTuple.size(); i++) {
          Descriptor curDesc = descTuple.get(i);
          Location loc;
          if (i == 0) {
            loc = new Location(md, curDesc.getSymbol());
            loc.setLocDescriptor(curDesc);
            cd = ((VarDescriptor) curDesc).getType().getClassDesc();
          } else {
            loc = new Location(cd, curDesc.getSymbol());
            loc.setLocDescriptor(curDesc);
            cd = ((FieldDescriptor) curDesc).getType().getClassDesc();
          }
          locTuple.add(loc);
        }
      }

      mapFlowNodeToLocTuple.put(fn, locTuple);
    }
    return mapFlowNodeToLocTuple.get(fn);
  }

  public Set<FlowNode> getIncomingFlowNodeSet(FlowNode node) {
    Set<FlowNode> set = new HashSet<FlowNode>();
    getIncomingFlowNodeSet(node, set);
    return set;
  }

  public void getIncomingFlowNodeSet(FlowNode node, Set<FlowNode> visited) {

    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      FlowNode curNode = (FlowNode) iterator.next();
      Set<FlowEdge> edgeSet = curNode.getOutEdgeSet();

      for (Iterator iterator2 = edgeSet.iterator(); iterator2.hasNext();) {
        FlowEdge flowEdge = (FlowEdge) iterator2.next();

        if (node.equals(getFlowNode(flowEdge.getEndTuple()))) {
          FlowNode incomingNode = getFlowNode(flowEdge.getInitTuple());

          if (!visited.contains(incomingNode)) {
            visited.add(incomingNode);
            getIncomingFlowNodeSet(incomingNode, visited);
          }
        }
      }
    }

  }

  public Set<NTuple<Location>> getIncomingFlowTupleSet(FlowNode fn) {

    NTuple<Descriptor> dstTuple = fn.getDescTuple();

    Set<NTuple<Location>> set = new HashSet<NTuple<Location>>();

    ClassDescriptor cd = null;

    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      FlowNode node = (FlowNode) iterator.next();
      Set<FlowEdge> edgeSet = node.getOutEdgeSet();
      for (Iterator iterator2 = edgeSet.iterator(); iterator2.hasNext();) {
        FlowEdge flowEdge = (FlowEdge) iterator2.next();
        if (dstTuple.equals(flowEdge.getEndTuple())) {
          NTuple<Descriptor> initTuple = flowEdge.getInitTuple();
          NTuple<Location> locTuple = new NTuple<Location>();
          for (int i = 0; i < initTuple.size(); i++) {
            Descriptor d = initTuple.get(i);
            Location loc;
            if (i == 0) {
              loc = new Location(md, d.getSymbol());
              cd = ((VarDescriptor) d).getType().getClassDesc();
            } else {
              loc = new Location(cd, d.getSymbol());
              cd = ((FieldDescriptor) d).getType().getClassDesc();
            }
            locTuple.add(loc);
          }
          set.add(locTuple);
        }
      }
    }
    return set;
  }

  public boolean isParameter(NTuple<Descriptor> tuple) {
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