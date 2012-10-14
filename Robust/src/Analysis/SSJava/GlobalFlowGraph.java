package Analysis.SSJava;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import IR.Descriptor;
import IR.MethodDescriptor;
import IR.Tree.MethodInvokeNode;

public class GlobalFlowGraph {

  MethodDescriptor md;

  Map<NTuple<Location>, GlobalFlowNode> mapLocTupleToNode;
  Map<GlobalFlowNode, Set<GlobalFlowNode>> mapFlowNodeToOutNodeSet;
  Map<GlobalFlowNode, Set<GlobalFlowNode>> mapFlowNodeToInNodeSet;

  Map<Location, CompositeLocation> mapLocationToInferCompositeLocation;

  public GlobalFlowGraph(MethodDescriptor md) {
    this.md = md;
    this.mapLocTupleToNode = new HashMap<NTuple<Location>, GlobalFlowNode>();
    this.mapFlowNodeToOutNodeSet = new HashMap<GlobalFlowNode, Set<GlobalFlowNode>>();
    this.mapFlowNodeToInNodeSet = new HashMap<GlobalFlowNode, Set<GlobalFlowNode>>();

    this.mapLocationToInferCompositeLocation = new HashMap<Location, CompositeLocation>();
  }

  public MethodDescriptor getMethodDescriptor() {
    return md;
  }

  public Map<Location, CompositeLocation> getMapLocationToInferCompositeLocation() {
    return mapLocationToInferCompositeLocation;
  }

  public GlobalFlowNode getFlowNode(NTuple<Location> locTuple) {
    if (!mapLocTupleToNode.containsKey(locTuple)) {
      GlobalFlowNode node = createNewGlobalFlowNode(locTuple);
      mapLocTupleToNode.put(locTuple, node);
    }
    return mapLocTupleToNode.get(locTuple);
  }

  private GlobalFlowNode createNewGlobalFlowNode(NTuple<Location> locTuple) {
    GlobalFlowNode node = new GlobalFlowNode(locTuple);
    return node;
  }

  public void addMapLocationToInferCompositeLocation(Location loc, CompositeLocation newCompLoc) {
    if (mapLocationToInferCompositeLocation.containsKey(loc)) {
      // need to do a sanity check
      // if the old composite location has the same prefix of the new composite location,
      // replace old one with new one
      // if both old and new do not share the prefix, throw error
      CompositeLocation oldCompLoc = mapLocationToInferCompositeLocation.get(loc);

      if (newCompLoc.getSize() == oldCompLoc.getSize()) {
        for (int i = 0; i < oldCompLoc.getSize() - 1; i++) {
          Location oldLocElement = oldCompLoc.get(i);
          Location newLocElement = newCompLoc.get(i);

          if (!oldLocElement.equals(newLocElement)) {
            throw new Error("Failed to generate a composite location. The old composite location="
                + oldCompLoc + " and the new composite location=" + newCompLoc);
          }
        }
      } else if (newCompLoc.getSize() > oldCompLoc.getSize()) {
        mapLocationToInferCompositeLocation.put(loc, newCompLoc);
      }

    } else {
      mapLocationToInferCompositeLocation.put(loc, newCompLoc);
    }
  }

  public CompositeLocation getCompositeLocation(Location loc) {
    if (!mapLocationToInferCompositeLocation.containsKey(loc)) {
      CompositeLocation compLoc = new CompositeLocation();
      compLoc.addLocation(loc);
      mapLocationToInferCompositeLocation.put(loc, compLoc);
    }
    return mapLocationToInferCompositeLocation.get(loc);
  }

  public void addValueFlowEdge(NTuple<Location> fromLocTuple, NTuple<Location> toLocTuple) {

    GlobalFlowNode fromNode = getFlowNode(fromLocTuple);
    GlobalFlowNode toNode = getFlowNode(toLocTuple);

    if (!mapFlowNodeToOutNodeSet.containsKey(fromNode)) {
      mapFlowNodeToOutNodeSet.put(fromNode, new HashSet<GlobalFlowNode>());
    }
    mapFlowNodeToOutNodeSet.get(fromNode).add(toNode);

    if (!mapFlowNodeToInNodeSet.containsKey(toNode)) {
      mapFlowNodeToInNodeSet.put(toNode, new HashSet<GlobalFlowNode>());
    }
    mapFlowNodeToInNodeSet.get(toNode).add(fromNode);

    // System.out.println("create a global edge from " + fromNode + " to " + toNode);

  }

  public Set<GlobalFlowNode> getInNodeSet(GlobalFlowNode node) {
    if (!mapFlowNodeToInNodeSet.containsKey(node)) {
      mapFlowNodeToInNodeSet.put(node, new HashSet<GlobalFlowNode>());
    }
    return mapFlowNodeToInNodeSet.get(node);
  }

  public Set<GlobalFlowNode> getNodeSet() {
    Set<GlobalFlowNode> nodeSet = new HashSet<GlobalFlowNode>();
    nodeSet.addAll(mapLocTupleToNode.values());
    return nodeSet;
  }

  public Set<GlobalFlowNode> getOutNodeSet(GlobalFlowNode node) {

    if (!mapFlowNodeToOutNodeSet.containsKey(node)) {
      mapFlowNodeToOutNodeSet.put(node, new HashSet<GlobalFlowNode>());
    }

    return mapFlowNodeToOutNodeSet.get(node);
  }

  public void writeGraph(String suffix) {

    String graphName = "flowgraph_" + md.toString() + suffix;
    graphName = graphName.replaceAll("[\\W]", "");

    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(graphName + ".dot"));
      bw.write("digraph " + graphName + " {\n");
      bw.write("compound=true;\n");

      // then visit every flow node

      // Iterator<FlowNode> iter = nodeSet.iterator();
      Iterator<GlobalFlowNode> iter = getNodeSet().iterator();

      Set<GlobalFlowNode> addedNodeSet = new HashSet<GlobalFlowNode>();

      while (iter.hasNext()) {
        GlobalFlowNode srcNode = iter.next();

        Set<GlobalFlowNode> outNodeSet = getOutNodeSet(srcNode);
        for (Iterator iterator = outNodeSet.iterator(); iterator.hasNext();) {
          GlobalFlowNode dstNode = (GlobalFlowNode) iterator.next();

          if (!addedNodeSet.contains(srcNode)) {
            drawNode(srcNode, bw);
          }

          if (!addedNodeSet.contains(dstNode)) {
            drawNode(dstNode, bw);
          }

          bw.write("" + srcNode.getID() + " -> " + dstNode.getID() + ";\n");

        }

        // if (node.getDescTuple().size() == 1) {
        // // here, we just care about the local variable
        // if (node.getFieldNodeSet().size() > 0) {
        // drawSubgraph(node, bw, addedEdgeSet);
        // }
        // }
        // drawEdges(node, bw, addedNodeSet, addedEdgeSet);

      }

      bw.write("}\n");
      bw.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void drawNode(GlobalFlowNode node, BufferedWriter bw) throws IOException {
    bw.write(node.getID() + " [label=\"" + node.getPrettyID() + "\"]" + ";\n");
  }

  public Set<GlobalFlowNode> getIncomingNodeSet(GlobalFlowNode node) {

    Set<GlobalFlowNode> incomingNodeSet = new HashSet<GlobalFlowNode>();
    recurIncomingNodeSet(node, incomingNodeSet);

    return incomingNodeSet;
  }

  private void recurIncomingNodeSet(GlobalFlowNode node, Set<GlobalFlowNode> visited) {

    for (Iterator iterator = getNodeSet().iterator(); iterator.hasNext();) {
      GlobalFlowNode curNode = (GlobalFlowNode) iterator.next();

      Set<GlobalFlowNode> outNodeSet = getOutNodeSet(curNode);

      for (Iterator iterator2 = outNodeSet.iterator(); iterator2.hasNext();) {
        GlobalFlowNode outNode = (GlobalFlowNode) iterator2.next();

        if (outNode.equals(node)) {
          if (!visited.contains(curNode)) {
            visited.add(curNode);
            recurIncomingNodeSet(curNode, visited);
          }
        }
      }
    }

  }

  public Set<GlobalFlowNode> getIncomingNodeSetByPrefix(Location prefix) {

    Set<GlobalFlowNode> incomingNodeSet = new HashSet<GlobalFlowNode>();

    for (Iterator iterator = getNodeSet().iterator(); iterator.hasNext();) {
      GlobalFlowNode curNode = (GlobalFlowNode) iterator.next();
      Set<GlobalFlowNode> outNodeSet = getOutNodeSet(curNode);

      for (Iterator iterator2 = outNodeSet.iterator(); iterator2.hasNext();) {
        GlobalFlowNode outNode = (GlobalFlowNode) iterator2.next();

        if (outNode.getLocTuple().startsWith(prefix)) {
          incomingNodeSet.add(curNode);
          recurIncomingNodeSetByPrefix(prefix, curNode, incomingNodeSet);
        }

      }
    }

    return incomingNodeSet;

  }

  private void recurIncomingNodeSetByPrefix(Location prefix, GlobalFlowNode node,
      Set<GlobalFlowNode> visited) {

    Set<GlobalFlowNode> inNodeSet = getInNodeSet(node);

    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      GlobalFlowNode curNode = (GlobalFlowNode) iterator.next();

      if (!curNode.getLocTuple().startsWith(prefix) && !visited.contains(curNode)) {
        visited.add(curNode);
        recurIncomingNodeSetByPrefix(prefix, curNode, visited);
      }
    }

  }

  public Set<GlobalFlowNode> getReachableNodeSetByPrefix(Location prefix) {

    Set<GlobalFlowNode> reachableNodeSet = new HashSet<GlobalFlowNode>();

    for (Iterator iterator = getNodeSet().iterator(); iterator.hasNext();) {
      GlobalFlowNode curNode = (GlobalFlowNode) iterator.next();

      if (curNode.getLocTuple().startsWith(prefix)) {
        Set<GlobalFlowNode> outNodeSet = getOutNodeSet(curNode);
        for (Iterator iterator2 = outNodeSet.iterator(); iterator2.hasNext();) {
          GlobalFlowNode outNode = (GlobalFlowNode) iterator2.next();
          if (!outNode.getLocTuple().startsWith(prefix) && !reachableNodeSet.contains(outNode)) {
            reachableNodeSet.add(outNode);
            recurReachableNodeSetByPrefix(prefix, outNode, reachableNodeSet);
          }

        }
      }
    }

    return reachableNodeSet;
  }

  private void recurReachableNodeSetByPrefix(Location prefix, GlobalFlowNode node,
      Set<GlobalFlowNode> reachableNodeSet) {
    Set<GlobalFlowNode> outNodeSet = getOutNodeSet(node);
    for (Iterator iterator = outNodeSet.iterator(); iterator.hasNext();) {
      GlobalFlowNode outNode = (GlobalFlowNode) iterator.next();
      if (!outNode.getLocTuple().startsWith(prefix) && !reachableNodeSet.contains(outNode)) {
        reachableNodeSet.add(outNode);
        recurReachableNodeSetByPrefix(prefix, outNode, reachableNodeSet);
      }
    }
  }

  public Set<GlobalFlowNode> getReachableNodeSetFrom(GlobalFlowNode node) {

    Set<GlobalFlowNode> reachableNodeSet = new HashSet<GlobalFlowNode>();
    recurReachableNodeSet(node, reachableNodeSet);

    return reachableNodeSet;
  }

  private void recurReachableNodeSet(GlobalFlowNode node, Set<GlobalFlowNode> reachableNodeSet) {
    Set<GlobalFlowNode> outNodeSet = getOutNodeSet(node);
    for (Iterator iterator = outNodeSet.iterator(); iterator.hasNext();) {
      GlobalFlowNode outNode = (GlobalFlowNode) iterator.next();
      if (!reachableNodeSet.contains(outNode)) {
        reachableNodeSet.add(outNode);
        recurReachableNodeSet(outNode, reachableNodeSet);
      }
    }
  }

}
