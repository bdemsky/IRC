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
import IR.VarDescriptor;

public class FlowGraph {

  MethodDescriptor md;

  Set<FlowNode> returnNodeSet;
  FlowNode thisVarNode;

  Map<FlowNode, Set<FlowEdge>> mapFlowNodeToInEdgeSet;
  Map<FlowNode, Set<FlowEdge>> mapFlowNodeToOutEdgeSet;

  Map<NTuple<Location>, FlowNode> mapLocTupleToFlowNode;
  Map<FlowNode, NTuple<Location>> mapFlowNodeToLocTuple;

  // maps the composite representation of field/var descriptors to infer nodes
  Map<NTuple<Descriptor>, FlowNode> mapDescTupleToInferNode;

  // maps a paramter descriptor to its index
  Map<Descriptor, Integer> mapParamDescToIdx;

  // DS for the lattice generation
  Map<Integer, FlowNode> mapIdxToFlowNode;

  public static int interseed = 0;

  boolean debug = true;

  public FlowGraph(MethodDescriptor md, Map<Descriptor, Integer> mapParamDescToIdx) {
    this.md = md;
    this.mapFlowNodeToLocTuple = new HashMap<FlowNode, NTuple<Location>>();
    this.mapLocTupleToFlowNode = new HashMap<NTuple<Location>, FlowNode>();
    this.mapDescTupleToInferNode = new HashMap<NTuple<Descriptor>, FlowNode>();
    this.mapParamDescToIdx = new HashMap<Descriptor, Integer>();
    this.mapParamDescToIdx.putAll(mapParamDescToIdx);
    this.returnNodeSet = new HashSet<FlowNode>();
    this.mapIdxToFlowNode = new HashMap<Integer, FlowNode>();
    this.mapFlowNodeToOutEdgeSet = new HashMap<FlowNode, Set<FlowEdge>>();
    this.mapFlowNodeToInEdgeSet = new HashMap<FlowNode, Set<FlowEdge>>();

    if (!md.isStatic()) {
      // create a node for 'this' varialbe
      // NTuple<Descriptor> thisDescTuple = new NTuple<Descriptor>();
      // thisDescTuple.add(md.getThis());

      NTuple<Descriptor> thisVarTuple = new NTuple<Descriptor>();
      thisVarTuple.add(md.getThis());
      FlowNode thisNode = createNewFlowNode(thisVarTuple);
      thisNode.setSkeleton(true);
      thisVarNode = thisNode;
    }

    setupMapIdxToDesc();

  }

  public Map<NTuple<Descriptor>, FlowNode> getMapDescTupleToInferNode() {
    return mapDescTupleToInferNode;
  }

  public void setMapDescTupleToInferNode(Map<NTuple<Descriptor>, FlowNode> in) {
    this.mapDescTupleToInferNode.putAll(in);
  }

  public Map<NTuple<Location>, FlowNode> getMapLocTupleToFlowNode() {
    return mapLocTupleToFlowNode;
  }

  public void setMapLocTupleToFlowNode(Map<NTuple<Location>, FlowNode> in) {
    this.mapLocTupleToFlowNode.putAll(in);
  }

  public void setReturnNodeSet(Set<FlowNode> in) {
    this.returnNodeSet.addAll(in);
  }

  public void setThisVarNode(FlowNode thisVarNode) {
    this.thisVarNode = thisVarNode;
  }

  public Map<Descriptor, Integer> getMapParamDescToIdx() {
    return mapParamDescToIdx;
  }

  public FlowNode createIntermediateNode() {
    NTuple<Descriptor> tuple = new NTuple<Descriptor>();
    Descriptor interDesc = new InterDescriptor(LocationInference.INTERLOC + interseed);
    tuple.add(interDesc);
    interseed++;

    FlowNode newNode = new FlowNode(tuple);
    newNode.setIntermediate(true);

    mapDescTupleToInferNode.put(tuple, newNode);
    // nodeSet.add(newNode);

     System.out.println("create new intermediate node= " + newNode);

    return newNode;
  }

  private void setupMapIdxToDesc() {

    Set<Descriptor> descSet = mapParamDescToIdx.keySet();
    for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
      Descriptor paramDesc = (Descriptor) iterator.next();
      int idx = mapParamDescToIdx.get(paramDesc);
      NTuple<Descriptor> descTuple = new NTuple<Descriptor>();
      descTuple.add(paramDesc);
      FlowNode paramNode = getFlowNode(descTuple);
      mapIdxToFlowNode.put(idx, paramNode);
      paramNode.setSkeleton(true);
    }

  }

  public int getNumParameters() {
    return mapIdxToFlowNode.keySet().size();
  }

  public FlowNode getParamFlowNode(int idx) {
    return mapIdxToFlowNode.get(idx);
  }

  public Set<FlowEdge> getEdgeSet() {
    Set<FlowEdge> edgeSet = new HashSet<FlowEdge>();

    Set<FlowNode> nodeSet = getNodeSet();
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      FlowNode flowNode = (FlowNode) iterator.next();
      edgeSet.addAll(getOutEdgeSet(flowNode));
    }

    return edgeSet;
  }

  public Set<FlowNode> getNodeSet() {
    Set<FlowNode> set = new HashSet<FlowNode>();
    set.addAll(mapDescTupleToInferNode.values());
    return set;
  }

  public MethodDescriptor getMethodDescriptor() {
    return md;
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

    Set<FlowEdge> fromNodeOutEdgeSet = getOutEdgeSet(fromNode);
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

  public Set<FlowEdge> getOutEdgeSetStartingFrom(FlowNode startNode) {

    Descriptor prefixDesc = startNode.getCurrentDescTuple().get(0);

    // returns the set of edges that share the same prefix of startNode
    Set<FlowEdge> edgeSet = new HashSet<FlowEdge>();

    for (Iterator<Set<FlowEdge>> iter = mapFlowNodeToOutEdgeSet.values().iterator(); iter.hasNext();) {
      Set<FlowEdge> nodeEdgeSet = iter.next();
      for (Iterator<FlowEdge> iter2 = nodeEdgeSet.iterator(); iter2.hasNext();) {
        FlowEdge edge = iter2.next();
        if (edge.getInitTuple().get(0).equals(prefixDesc)) {
          edgeSet.add(edge);
        }
      }
    }

    return edgeSet;
  }

  public Set<FlowEdge> getOutEdgeSet(FlowNode node) {
    if (!mapFlowNodeToOutEdgeSet.containsKey(node)) {
      mapFlowNodeToOutEdgeSet.put(node, new HashSet<FlowEdge>());
    }
    return mapFlowNodeToOutEdgeSet.get(node);
  }

  public Set<FlowEdge> getInEdgeSet(FlowNode node) {
    if (!mapFlowNodeToInEdgeSet.containsKey(node)) {
      mapFlowNodeToInEdgeSet.put(node, new HashSet<FlowEdge>());
    }
    return mapFlowNodeToInEdgeSet.get(node);
  }

  public void addValueFlowEdge(NTuple<Descriptor> fromDescTuple, NTuple<Descriptor> toDescTuple) {

    FlowNode fromNode = getFlowNode(fromDescTuple);
    FlowNode toNode = getFlowNode(toDescTuple);

    if (toNode.getDescTuple().get(0).equals(LocationInference.LITERALDESC)) {
      return;
    }

    System.out.println("create an edge from " + fromNode + " to " + toNode);

    int fromTupleSize = fromDescTuple.size();
    NTuple<Descriptor> curFromTuple = new NTuple<Descriptor>();
    for (int i = 0; i < fromTupleSize; i++) {
      Descriptor desc = fromDescTuple.get(i);
      curFromTuple.add(desc);
      int toTupleSize = toDescTuple.size();
      NTuple<Descriptor> curToTuple = new NTuple<Descriptor>();
      for (int k = 0; k < toTupleSize; k++) {
        Descriptor toDesc = toDescTuple.get(k);
        curToTuple.add(toDesc);
        addFlowEdge(getFlowNode(curFromTuple), getFlowNode(curToTuple), fromDescTuple, toDescTuple);
      }
    }

    // int fromTupleSize = fromDescTuple.size();
    // NTuple<Descriptor> curTuple = new NTuple<Descriptor>();
    // for (int i = 0; i < fromTupleSize; i++) {
    // Descriptor desc = fromDescTuple.get(i);
    // curTuple.add(desc);
    // addFlowEdge(getFlowNode(curTuple), toNode, fromDescTuple, toDescTuple);
    // }
    //
    // int toTupleSize = toDescTuple.size();
    // curTuple = new NTuple<Descriptor>();
    // for (int i = 0; i < toTupleSize; i++) {
    // Descriptor desc = toDescTuple.get(i);
    // curTuple.add(desc);
    // addFlowEdge(fromNode, getFlowNode(curTuple), fromDescTuple, toDescTuple);
    // }

  }

  private void addFlowEdge(FlowNode fromNode, FlowNode toNode, NTuple<Descriptor> initTuple,
      NTuple<Descriptor> endTuple) {

    FlowEdge edge = new FlowEdge(fromNode, toNode, initTuple, endTuple);
    addOutEdge(fromNode, edge);
    addInEdge(toNode, edge);

    // System.out.println("add a new edge=" + edge);
  }

  private void addInEdge(FlowNode toNode, FlowEdge edge) {
    getInEdgeSet(toNode).add(edge);
  }

  private void addOutEdge(FlowNode fromNode, FlowEdge edge) {
    if (!mapFlowNodeToOutEdgeSet.containsKey(fromNode)) {
      mapFlowNodeToOutEdgeSet.put(fromNode, new HashSet<FlowEdge>());
    }
    mapFlowNodeToOutEdgeSet.get(fromNode).add(edge);
  }

  public boolean contains(NTuple<Descriptor> descTuple) {
    return mapDescTupleToInferNode.containsKey(descTuple);
  }

  public FlowNode getFlowNode(NTuple<Descriptor> descTuple) {
    if (!mapDescTupleToInferNode.containsKey(descTuple)) {
      FlowNode node = createNewFlowNode(descTuple);
      mapDescTupleToInferNode.put(descTuple, node);
    }
    return mapDescTupleToInferNode.get(descTuple);
  }

  public FlowNode getThisVarNode() {
    return thisVarNode;
  }

  public FlowNode createNewFlowNode(NTuple<Descriptor> tuple) {

    if (!mapDescTupleToInferNode.containsKey(tuple)) {
      FlowNode node = new FlowNode(tuple);
      mapDescTupleToInferNode.put(tuple, node);
      // nodeSet.add(node);

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
    returnNodeSet.add(node);
  }

  public Set<FlowNode> getReturnNodeSet() {
    return returnNodeSet;
  }

  public Set<FlowNode> getLocalReachFlowNodeSetFrom(FlowNode fn) {
    Set<FlowNode> set = new HashSet<FlowNode>();
    recurLocalReachFlowNodeSet(fn, set);
    return set;
  }

  private void recurLocalReachFlowNodeSet(FlowNode fn, Set<FlowNode> visited) {

    Set<FlowEdge> outEdgeSet = getOutEdgeSet(fn);
    for (Iterator iterator = outEdgeSet.iterator(); iterator.hasNext();) {
      FlowEdge edge = (FlowEdge) iterator.next();
      FlowNode dstNode = edge.getDst();

      if (!visited.contains(dstNode)) {
        visited.add(dstNode);
        recurLocalReachFlowNodeSet(dstNode, visited);
      }
    }

  }

  private void getReachFlowNodeSetFrom(FlowNode fn, Set<FlowNode> visited) {

    Set<FlowEdge> outEdgeSet = getOutEdgeSet(fn);
    for (Iterator iterator = outEdgeSet.iterator(); iterator.hasNext();) {
      FlowEdge edge = (FlowEdge) iterator.next();

      if (fn.equals(getFlowNode(edge.getInitTuple()))) {
        FlowNode dstNode = getFlowNode(edge.getEndTuple());
        if (!visited.contains(dstNode)) {
          visited.add(dstNode);
          getReachFlowNodeSetFrom(dstNode, visited);
        }
      }
    }

  }

  public Set<FlowNode> getReachFlowNodeSetFrom(FlowNode fn) {
    Set<FlowNode> set = new HashSet<FlowNode>();
    getReachFlowNodeSetFrom(fn, set);
    return set;
  }

  public Set<FlowNode> getReachableSetFrom(NTuple<Descriptor> prefix) {
    Set<FlowNode> reachableSet = new HashSet<FlowNode>();

    Set<FlowNode> nodeSet = getNodeSet();
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      FlowNode flowNode = (FlowNode) iterator.next();
      if (flowNode.getCurrentDescTuple().startsWith(prefix)) {
        recurReachableSetFrom(flowNode, reachableSet);
      }
    }

    return reachableSet;
  }

  // private void getReachFlowNodeSetFrom(FlowNode fn, Set<FlowNode> visited) {
  //
  // for (Iterator iterator = fn.getOutEdgeSet().iterator();
  // iterator.hasNext();) {
  // FlowEdge edge = (FlowEdge) iterator.next();
  //
  // if (fn.equals(getFlowNode(edge.getInitTuple()))) {
  //
  // FlowNode dstNode = getFlowNode(edge.getEndTuple());
  //
  // if (!visited.contains(dstNode)) {
  // visited.add(dstNode);
  // getReachFlowNodeSetFrom(dstNode, visited);
  // }
  // }
  // }
  //
  // }

  private void recurReachableSetFrom(FlowNode curNode, Set<FlowNode> reachableSet) {

    Set<FlowEdge> edgeSet = getOutEdgeSet(curNode);
    for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
      FlowEdge edge = (FlowEdge) iterator.next();
      FlowNode dstNode = getFlowNode(edge.getEndTuple());
      if (!reachableSet.contains(dstNode)) {
        reachableSet.add(dstNode);
        recurReachableSetFrom(dstNode, reachableSet);
      }
    }

  }

  public Set<NTuple<Location>> getReachableFlowTupleSet(Set<NTuple<Location>> visited, FlowNode fn) {

    Set<FlowEdge> outEdgeSet = getOutEdgeSet(fn);

    for (Iterator iterator = outEdgeSet.iterator(); iterator.hasNext();) {
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
      } else if (localDesc.getSymbol().equals(SSJavaAnalysis.TOP)) {
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
            if (curDesc instanceof VarDescriptor) {
              cd = ((VarDescriptor) curDesc).getType().getClassDesc();
            } else {
              // otherwise it should be the last element
              cd = null;
            }
          } else {
            loc = new Location(cd, curDesc.getSymbol());
            loc.setLocDescriptor(curDesc);

            if (curDesc instanceof FieldDescriptor) {
              cd = ((FieldDescriptor) curDesc).getType().getClassDesc();
            } else if (curDesc instanceof LocationDescriptor) {
              cd = ((LocationDescriptor) curDesc).getEnclosingClassDesc();
            } else {
              // otherwise it should be the last element of the tuple
              cd = null;
            }

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

    for (Iterator iterator = getNodeSet().iterator(); iterator.hasNext();) {
      FlowNode curNode = (FlowNode) iterator.next();
      Set<FlowEdge> edgeSet = getOutEdgeSet(curNode);

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

    for (Iterator iterator = getNodeSet().iterator(); iterator.hasNext();) {
      FlowNode node = (FlowNode) iterator.next();

      Set<FlowEdge> edgeSet = getOutEdgeSet(node);
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
    Descriptor firstIdxDesc = tuple.get(0);
    return mapParamDescToIdx.get(firstIdxDesc);
  }

  public FlowGraph clone() {
    FlowGraph clone = new FlowGraph(md, mapParamDescToIdx);

    // clone.setNodeSet(getNodeSet());
    clone.setMapLocTupleToFlowNode(getMapLocTupleToFlowNode());
    clone.setMapFlowNodeToLocTuple(getMapFlowNodeToLocTuple());
    clone.setMapDescTupleToInferNode(getMapDescTupleToInferNode());

    clone.setMapFlowNodeToOutEdgeSet(getMapFlowNodeToOutEdgeSet());
    clone.setReturnNodeSet(getReturnNodeSet());
    clone.setThisVarNode(getThisVarNode());

    return clone;
  }

  public Map<FlowNode, NTuple<Location>> getMapFlowNodeToLocTuple() {
    return mapFlowNodeToLocTuple;
  }

  public void setMapFlowNodeToLocTuple(Map<FlowNode, NTuple<Location>> in) {
    this.mapFlowNodeToLocTuple.putAll(in);
  }

  public Map<FlowNode, Set<FlowEdge>> getMapFlowNodeToOutEdgeSet() {
    return mapFlowNodeToOutEdgeSet;
  }

  public Set<FlowNode> getIncomingNodeSetByPrefix(Descriptor prefix) {

    Set<FlowNode> incomingNodeSet = new HashSet<FlowNode>();

    for (Iterator iterator = getNodeSet().iterator(); iterator.hasNext();) {
      FlowNode curNode = (FlowNode) iterator.next();
      Set<FlowEdge> outEdgeSet = getOutEdgeSet(curNode);

      for (Iterator iterator2 = outEdgeSet.iterator(); iterator2.hasNext();) {
        FlowEdge outEdge = (FlowEdge) iterator2.next();

        if (outEdge.getEndTuple().startsWith(prefix)) {
          incomingNodeSet.add(curNode);
          recurIncomingNodeSetByPrefix(prefix, curNode, incomingNodeSet);

        }

      }
    }

    return incomingNodeSet;

  }

  private void recurIncomingNodeSetByPrefix(Descriptor prefix, FlowNode node, Set<FlowNode> visited) {

    Set<FlowEdge> inEdgeSet = getInEdgeSet(node);

    for (Iterator iterator = inEdgeSet.iterator(); iterator.hasNext();) {
      FlowEdge inEdge = (FlowEdge) iterator.next();

      FlowNode inNode = getFlowNode(inEdge.getInitTuple());
      if (!inEdge.getInitTuple().startsWith(prefix) && !visited.contains(inNode)) {
        visited.add(inNode);
        recurIncomingNodeSetByPrefix(prefix, inNode, visited);
      }
    }

  }

  public void setMapFlowNodeToOutEdgeSet(Map<FlowNode, Set<FlowEdge>> inMap) {
    Set<FlowNode> keySet = inMap.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      FlowNode key = (FlowNode) iterator.next();
      Set<FlowEdge> newEdgeSet = new HashSet<FlowEdge>();
      newEdgeSet.addAll(inMap.get(key));
      mapFlowNodeToOutEdgeSet.put(key, newEdgeSet);
    }
  }

  private void drawEdges(FlowNode node, BufferedWriter bw, Set<FlowNode> addedNodeSet,
      Set<FlowEdge> addedEdgeSet) throws IOException {

    Set<FlowEdge> edgeSet = getOutEdgeSet(node);

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
    writeGraph("");
  }

  public void writeGraph(String suffix) throws java.io.IOException {

    String graphName = "flowgraph_" + md.toString() + suffix;
    graphName = graphName.replaceAll("[\\W]", "");

    BufferedWriter bw = new BufferedWriter(new FileWriter(graphName + ".dot"));
    bw.write("digraph " + graphName + " {\n");
    bw.write("compound=true;\n");

    // then visit every flow node

    // Iterator<FlowNode> iter = nodeSet.iterator();
    Iterator<FlowNode> iter = getNodeSet().iterator();

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
    return getNodeSet().contains(node);
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