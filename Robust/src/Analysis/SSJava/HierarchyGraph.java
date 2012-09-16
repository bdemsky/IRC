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
import IR.FieldDescriptor;

public class HierarchyGraph {

  Descriptor desc;

  String name;
  Map<Descriptor, HNode> mapDescToHNode;
  Map<HNode, Set<Descriptor>> mapHNodeToDescSet;
  Map<HNode, Set<HNode>> mapHNodeToIncomingSet;
  Map<HNode, Set<HNode>> mapHNodeToOutgoingSet;
  Map<Set<HNode>, HNode> mapSkeletonNodeSetToCombinationNode;
  Map<HNode, Set<HNode>> mapCombinationNodeToCombineNodeSet;
  Map<Set<HNode>, HNode> mapCombineNodeSetToCombinationNode;
  Map<Set<HNode>, Set<HNode>> mapCombineNodeSetToOutgoingNodeSet;

  Set<HNode> nodeSet;

  public static int seed = 0;

  public HierarchyGraph() {
    mapHNodeToIncomingSet = new HashMap<HNode, Set<HNode>>();
    mapHNodeToOutgoingSet = new HashMap<HNode, Set<HNode>>();
    mapHNodeToDescSet = new HashMap<HNode, Set<Descriptor>>();
    mapDescToHNode = new HashMap<Descriptor, HNode>();
    mapSkeletonNodeSetToCombinationNode = new HashMap<Set<HNode>, HNode>();
    mapCombinationNodeToCombineNodeSet = new HashMap<HNode, Set<HNode>>();
    mapCombineNodeSetToOutgoingNodeSet = new HashMap<Set<HNode>, Set<HNode>>();
    mapCombineNodeSetToCombinationNode = new HashMap<Set<HNode>, HNode>();
    nodeSet = new HashSet<HNode>();
  }

  public Descriptor getDesc() {
    return desc;
  }

  public void setDesc(Descriptor desc) {
    this.desc = desc;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public HierarchyGraph(Descriptor d) {
    this();
    desc = d;
    name = d.toString();
  }

  public Map<HNode, Set<Descriptor>> getMapHNodeToDescSet() {
    return mapHNodeToDescSet;
  }

  public void setMapHNodeToDescSet(Map<HNode, Set<Descriptor>> map) {
    mapHNodeToDescSet.putAll(map);
  }

  public Map<Descriptor, HNode> getMapDescToHNode() {
    return mapDescToHNode;
  }

  public void setMapDescToHNode(Map<Descriptor, HNode> map) {
    mapDescToHNode.putAll(map);
  }

  public Set<HNode> getNodeSet() {
    return nodeSet;
  }

  public void addEdge(HNode srcHNode, HNode dstHNode) {

    if (!nodeSet.contains(srcHNode)) {
      nodeSet.add(srcHNode);
    }

    if (!nodeSet.contains(dstHNode)) {
      nodeSet.add(dstHNode);
    }

    Set<HNode> possibleCycleSet = getPossibleCycleNodes(srcHNode, dstHNode);

    System.out.println("src=" + srcHNode + " dstHNode=" + dstHNode + " possibleCycleSet="
        + possibleCycleSet);

    if (possibleCycleSet.size() > 0) {
      HNode newMergeNode = mergeNodes(possibleCycleSet, false);
      newMergeNode.setSharedNode(true);
      System.out.println("### CYCLIC VALUE FLOW: " + srcHNode + " -> " + dstHNode);
      System.out.println("### INTRODUCE A NEW MERGE NODE: " + newMergeNode);
    } else {
      getIncomingNodeSet(dstHNode).add(srcHNode);
      getOutgoingNodeSet(srcHNode).add(dstHNode);
      System.out.println("add an edge " + srcHNode + " -> " + dstHNode);
    }

  }

  public void addNode(HNode node) {
    nodeSet.add(node);
  }

  public void addEdge(Descriptor src, Descriptor dst) {
    HNode srcHNode = getHNode(src);
    HNode dstHNode = getHNode(dst);

    addEdge(srcHNode, dstHNode);

  }

  public void setParamHNode(Descriptor d) {
    getHNode(d).setSkeleton(true);
  }

  public HNode getHNode(Descriptor d) {
    if (!mapDescToHNode.containsKey(d)) {
      HNode newNode = new HNode(d);
      if (d instanceof FieldDescriptor) {
        newNode.setSkeleton(true);
      }
      mappingDescriptorToHNode(d, newNode);
      nodeSet.add(newNode);
    }
    return mapDescToHNode.get(d);
  }

  private void mappingDescriptorToHNode(Descriptor desc, HNode node) {
    mapDescToHNode.put(desc, node);
    if (!mapHNodeToDescSet.containsKey(node)) {
      mapHNodeToDescSet.put(node, new HashSet<Descriptor>());
    }
    mapHNodeToDescSet.get(node).add(desc);
  }

  public HierarchyGraph generateSkeletonGraph() {

    // compose a skeleton graph that only consists of fields or parameters
    HierarchyGraph skeletonGraph = new HierarchyGraph(desc);
    skeletonGraph.setName(desc + "_SKELETON");

    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode src = (HNode) iterator.next();
      if (src.isSkeleton()) {
        Set<HNode> reachSet = getDirectlyReachSkeletonSet(src);
        if (reachSet.size() > 0) {
          for (Iterator iterator2 = reachSet.iterator(); iterator2.hasNext();) {
            HNode dst = (HNode) iterator2.next();
            skeletonGraph.addEdge(src, dst);
          }
        } else {
          skeletonGraph.addNode(src);
        }
      }
    }

    skeletonGraph.setMapDescToHNode(getMapDescToHNode());
    skeletonGraph.setMapHNodeToDescSet(getMapHNodeToDescSet());

    return skeletonGraph;

  }

  private Set<HNode> getDirectlyReachSkeletonSet(HNode node) {

    Set<HNode> visited = new HashSet<HNode>();
    Set<HNode> connected = new HashSet<HNode>();
    recurReachSkeletonSet(node, connected, visited);

    return connected;
  }

  private void removeRedundantEdges() {

    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode src = (HNode) iterator.next();
      Set<HNode> connectedSet = getOutgoingNodeSet(src);
      Set<HNode> toberemovedSet = new HashSet<HNode>();
      for (Iterator iterator2 = connectedSet.iterator(); iterator2.hasNext();) {
        HNode dst = (HNode) iterator2.next();
        Set<HNode> otherNeighborSet = new HashSet<HNode>();
        otherNeighborSet.addAll(connectedSet);
        otherNeighborSet.remove(dst);
        for (Iterator iterator3 = otherNeighborSet.iterator(); iterator3.hasNext();) {
          HNode neighbor = (HNode) iterator3.next();
          if (reachTo(neighbor, dst, new HashSet<HNode>())) {
            toberemovedSet.add(dst);
          }
        }
      }
      if (toberemovedSet.size() > 0) {
        connectedSet.removeAll(toberemovedSet);

        for (Iterator iterator2 = toberemovedSet.iterator(); iterator2.hasNext();) {
          HNode node = (HNode) iterator2.next();
          getIncomingNodeSet(node).remove(src);
        }

      }
    }

  }

  public void simplifyHierarchyGraph() {
    removeRedundantEdges();
    combineRedundantNodes(false);
  }

  public void simplifySkeletonCombinationHierarchyGraph() {
    removeRedundantEdges();
    combineRedundantNodes(true);
  }

  private void combineRedundantNodes(boolean onlyCombinationNodes) {
    // Combine field/parameter nodes who have the same set of incoming/outgoing edges.
    boolean isUpdated = false;
    do {
      isUpdated = combineTwoRedundatnNodes(onlyCombinationNodes);
    } while (isUpdated);
  }

  private Set<HNode> getIncomingNodeSet(HNode node) {
    if (!mapHNodeToIncomingSet.containsKey(node)) {
      mapHNodeToIncomingSet.put(node, new HashSet<HNode>());
    }
    return mapHNodeToIncomingSet.get(node);
  }

  private Set<HNode> getOutgoingNodeSet(HNode node) {
    if (!mapHNodeToOutgoingSet.containsKey(node)) {
      mapHNodeToOutgoingSet.put(node, new HashSet<HNode>());
    }
    return mapHNodeToOutgoingSet.get(node);
  }

  private boolean combineTwoRedundatnNodes(boolean onlyCombinationNodes) {
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node1 = (HNode) iterator.next();

      if ((onlyCombinationNodes && (!node1.isCombinationNode()))
          || (!onlyCombinationNodes && (!node1.isSkeleton()))) {
        continue;
      }

      Set<HNode> incomingNodeSet1 = getIncomingNodeSet(node1);
      Set<HNode> outgoingNodeSet1 = getOutgoingNodeSet(node1);

      for (Iterator iterator2 = nodeSet.iterator(); iterator2.hasNext();) {
        HNode node2 = (HNode) iterator2.next();

        if ((onlyCombinationNodes && (!node2.isCombinationNode()))
            || (!onlyCombinationNodes && (!node2.isSkeleton()))) {
          continue;
        }

        if (!node1.equals(node2)) {

          Set<HNode> incomingNodeSet2 = getIncomingNodeSet(node2);
          Set<HNode> outgoingNodeSet2 = getOutgoingNodeSet(node2);

          if (incomingNodeSet1.equals(incomingNodeSet2)
              && outgoingNodeSet1.equals(outgoingNodeSet2)) {
            // need to merge node1 and node2

            Set<HNode> mergeSet = new HashSet<HNode>();
            mergeSet.add(node1);
            mergeSet.add(node2);
            mergeNodes(mergeSet, onlyCombinationNodes);
            return true;
          }

        }
      }

    }
    return false;
  }

  private void addEdgeWithNoCycleCheck(HNode srcHNode, HNode dstHNode) {
    getIncomingNodeSet(dstHNode).add(srcHNode);
    getOutgoingNodeSet(srcHNode).add(dstHNode);
    System.out.println("addEdgeWithNoCycleCheck src=" + srcHNode + " -> " + dstHNode);
  }

  private HNode mergeNodes(Set<HNode> set, boolean onlyCombinationNodes) {

    Set<HNode> incomingNodeSet = new HashSet<HNode>();
    Set<HNode> outgoingNodeSet = new HashSet<HNode>();

    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      incomingNodeSet.addAll(getIncomingNodeSet(node));
      outgoingNodeSet.addAll(getOutgoingNodeSet(node));
    }

    String nodeName;
    if (onlyCombinationNodes) {
      nodeName = "Comb" + (seed++);
    } else {
      nodeName = "Node" + (seed++);
    }
    HNode newMergeNode = new HNode(nodeName);

    nodeSet.add(newMergeNode);
    nodeSet.removeAll(set);

    // if the input set contains a skeleton node, need to set a new merge node as skeleton also
    boolean hasSkeleton = false;
    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      if (inNode.isSkeleton()) {
        hasSkeleton = true;
        break;
      }
    }
    newMergeNode.setSkeleton(hasSkeleton);

    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      Set<Descriptor> descSetOfNode = getDescSetOfNode(node);
      for (Iterator iterator2 = descSetOfNode.iterator(); iterator2.hasNext();) {
        Descriptor desc = (Descriptor) iterator2.next();
        mappingDescriptorToHNode(desc, newMergeNode);
      }
    }

    for (Iterator iterator = incomingNodeSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      Set<HNode> outSet = getOutgoingNodeSet(inNode);
      outSet.removeAll(set);
      if (!set.contains(inNode)) {
        addEdgeWithNoCycleCheck(inNode, newMergeNode);
      }
    }

    for (Iterator iterator = outgoingNodeSet.iterator(); iterator.hasNext();) {
      HNode outNode = (HNode) iterator.next();
      Set<HNode> inSet = getIncomingNodeSet(outNode);
      inSet.removeAll(set);
      if (!set.contains(outNode)) {
        addEdgeWithNoCycleCheck(newMergeNode, outNode);
      }
    }

    System.out.println("#MERGING NODE=" + set + " new node=" + newMergeNode);
    return newMergeNode;
  }

  private Set<Descriptor> getDescSetOfNode(HNode node) {
    if (!mapHNodeToDescSet.containsKey(node)) {
      mapHNodeToDescSet.put(node, new HashSet<Descriptor>());
    }
    return mapHNodeToDescSet.get(node);
  }

  private boolean reachTo(HNode src, HNode dst, Set<HNode> visited) {
    Set<HNode> connectedSet = getOutgoingNodeSet(src);
    for (Iterator<HNode> iterator = connectedSet.iterator(); iterator.hasNext();) {
      HNode n = iterator.next();
      if (n.equals(dst)) {
        return true;
      }
      if (!visited.contains(n)) {
        visited.add(n);
        if (reachTo(n, dst, visited)) {
          return true;
        }
      }
    }
    return false;
  }

  private void recurReachSkeletonSet(HNode node, Set<HNode> connected, Set<HNode> visited) {

    Set<HNode> outSet = getOutgoingNodeSet(node);
    for (Iterator iterator = outSet.iterator(); iterator.hasNext();) {
      HNode outNode = (HNode) iterator.next();

      if (outNode.isSkeleton()) {
        connected.add(outNode);
      } else if (!visited.contains(outNode)) {
        visited.add(outNode);
        recurReachSkeletonSet(outNode, connected, visited);
      }
    }

  }

  public Set<HNode> getPossibleCycleNodes(HNode src, HNode dst) {
    // if an edge from src to dst introduces a new cycle flow,
    // the method returns the set of elements consisting of the cycle
    Set<HNode> cycleNodeSet = new HashSet<HNode>();
    // if the dst node reaches to the src node, the new relation
    // introduces a cycle to the lattice
    if (dst.equals(src)) {
      cycleNodeSet.add(dst);
      cycleNodeSet.add(src);
    } else if (reachTo(dst, src)) {
      cycleNodeSet.add(dst);
      cycleNodeSet.add(src);
      getInBetweenElements(dst, src, cycleNodeSet);
    }
    return cycleNodeSet;
  }

  private void getInBetweenElements(HNode start, HNode end, Set<HNode> nodeSet) {
    Set<HNode> connectedSet = getOutgoingNodeSet(start);
    for (Iterator iterator = connectedSet.iterator(); iterator.hasNext();) {
      HNode cur = (HNode) iterator.next();
      if ((!start.equals(cur)) && (!cur.equals(end)) && reachTo(cur, end)) {
        nodeSet.add(cur);
        getInBetweenElements(cur, end, nodeSet);
      }
    }
  }

  public boolean reachTo(HNode node1, HNode node2) {
    return reachTo(node1, node2, new HashSet<HNode>());
  }

  public Set<HNode> getCombineSetByCombinationNode(HNode node) {
    if (!mapCombinationNodeToCombineNodeSet.containsKey(node)) {
      mapCombinationNodeToCombineNodeSet.put(node, new HashSet<HNode>());
    }
    return mapCombinationNodeToCombineNodeSet.get(node);
  }

  private HNode getCombinationNode(Set<HNode> combineSet) {
    if (!mapCombineNodeSetToCombinationNode.containsKey(combineSet)) {
      String name = "COMB" + (seed++);
      HNode node = new HNode(name);
      node.setCombinationNode(true);
      nodeSet.add(node);
      mapCombineNodeSetToCombinationNode.put(combineSet, node);
      mapCombinationNodeToCombineNodeSet.put(node, combineSet);
    }

    return mapCombineNodeSetToCombinationNode.get(combineSet);
  }

  public Set<Set<HNode>> getCombineNodeSet() {
    return mapCombineNodeSetToOutgoingNodeSet.keySet();
  }

  public void insertCombinationNodesToGraph(HierarchyGraph hierarchyGraph) {
    // add a new combination node where parameter/field flows are actually combined.

    hierarchyGraph.identifyCombinationNodes();

    Set<Set<HNode>> keySet = hierarchyGraph.getCombineNodeSet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Set<HNode> combineSet = (Set<HNode>) iterator.next();
      System.out.println("combineSet=" + combineSet);
      HNode combinationNode = getCombinationNode(combineSet);

      // add an edge from a skeleton node to a combination node
      for (Iterator iterator2 = combineSet.iterator(); iterator2.hasNext();) {
        HNode inSkeletonNode = (HNode) iterator2.next();
        HNode srcNode = getHNode(inSkeletonNode.getDescriptor());
        System.out.println("inSkeletonNode=" + inSkeletonNode + "   srcNode=" + srcNode);
        addEdgeWithNoCycleCheck(srcNode, combinationNode);
      }

      // add an edge from the combination node to outgoing nodes
      Set<HNode> outSet = hierarchyGraph.getOutgoingNodeSetByCombineSet(combineSet);
      for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
        HNode curNode = (HNode) iterator2.next();
        if (curNode.isCombinationNode()) {
          Set<HNode> combineNode = hierarchyGraph.getCombineSetByCombinationNode(curNode);
          HNode outNode = getCombinationNode(combineNode);
          addEdgeWithNoCycleCheck(combinationNode, outNode);
        } else if (curNode.isSkeleton()) {
          addEdgeWithNoCycleCheck(combinationNode, curNode);
        }
      }

    }

  }

  private void addCombinationNode(HNode curNode, Set<HNode> reachToSet, Set<HNode> reachableSet) {
    if (!mapSkeletonNodeSetToCombinationNode.containsKey(reachToSet)) {
      // need to create a new combination node
      String nodeName = "Comb" + (seed++);
      HNode newCombinationNode = new HNode(nodeName);
      newCombinationNode.setCombinationNode(true);

      nodeSet.add(newCombinationNode);
      mapSkeletonNodeSetToCombinationNode.put(reachToSet, newCombinationNode);

      for (Iterator iterator = reachToSet.iterator(); iterator.hasNext();) {
        HNode reachToNode = (HNode) iterator.next();
        addEdge(reachToNode, newCombinationNode);
      }

    }

    HNode combinationNode = mapSkeletonNodeSetToCombinationNode.get(reachToSet);
    for (Iterator iterator = reachableSet.iterator(); iterator.hasNext();) {
      HNode reachableNode = (HNode) iterator.next();
      addEdge(combinationNode, reachableNode);
    }

  }

  private Set<HNode> getSkeleteNodeSetReachTo(HNode node) {

    Set<HNode> reachToSet = new HashSet<HNode>();
    Set<HNode> visited = new HashSet<HNode>();

    recurSkeletonReachTo(node, reachToSet, visited);

    return reachToSet;
  }

  private void recurSkeletonReachTo(HNode node, Set<HNode> reachToSet, Set<HNode> visited) {

    Set<HNode> inSet = getIncomingNodeSet(node);
    for (Iterator iterator = inSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();

      if (inNode.isSkeleton()) {
        reachToSet.add(inNode);
      } else if (!visited.contains(inNode)) {
        visited.add(inNode);
        recurSkeletonReachTo(inNode, reachToSet, visited);
      }
    }

  }

  public Map<HNode, Set<HNode>> getMapHNodeToOutgoingSet() {
    return mapHNodeToOutgoingSet;
  }

  public Map<HNode, Set<HNode>> getMapHNodeToIncomingSet() {
    return mapHNodeToIncomingSet;
  }

  public void setMapHNodeToOutgoingSet(Map<HNode, Set<HNode>> in) {
    mapHNodeToOutgoingSet.clear();
    Set<HNode> keySet = in.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      HNode key = (HNode) iterator.next();
      Set<HNode> inSet = in.get(key);
      Set<HNode> newSet = new HashSet<HNode>();
      newSet.addAll(inSet);
      mapHNodeToOutgoingSet.put(key, newSet);
    }
  }

  public void setMapHNodeToIncomingSet(Map<HNode, Set<HNode>> in) {
    mapHNodeToIncomingSet.clear();
    Set<HNode> keySet = in.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      HNode key = (HNode) iterator.next();
      Set<HNode> inSet = in.get(key);
      Set<HNode> newSet = new HashSet<HNode>();
      newSet.addAll(inSet);
      mapHNodeToIncomingSet.put(key, newSet);
    }
  }

  public void setNodeSet(Set<HNode> inSet) {
    nodeSet.clear();
    nodeSet.addAll(inSet);
  }

  public HierarchyGraph clone() {
    HierarchyGraph clone = new HierarchyGraph();
    clone.setDesc(getDesc());
    clone.setName(getName());
    clone.setNodeSet(getNodeSet());
    clone.setMapHNodeToIncomingSet(getMapHNodeToIncomingSet());
    clone.setMapHNodeToOutgoingSet(getMapHNodeToOutgoingSet());
    clone.setMapDescToHNode(getMapDescToHNode());
    clone.setMapHNodeToDescSet(getMapHNodeToDescSet());

    return clone;
  }

  public Set<HNode> getOutgoingNodeSetByCombineSet(Set<HNode> combineSet) {

    if (!mapCombineNodeSetToOutgoingNodeSet.containsKey(combineSet)) {
      mapCombineNodeSetToOutgoingNodeSet.put(combineSet, new HashSet<HNode>());
    }
    return mapCombineNodeSetToOutgoingNodeSet.get(combineSet);
  }

  public void identifyCombinationNodes() {

    // 1) set combination node flag if a node combines more than one skeleton node.
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      if (!node.isSkeleton()) {
        Set<HNode> reachToSet = getSkeleteNodeSetReachTo(node);
        if (reachToSet.size() > 1) {
          node.setCombinationNode(true);
          mapCombinationNodeToCombineNodeSet.put(node, reachToSet);
        }
      }
    }

    // 2) compute the outgoing set that needs to be directly connected from the combination node
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      if (node.isCombinationNode()) {
        Set<HNode> combineSet = mapCombinationNodeToCombineNodeSet.get(node);
        Set<HNode> outSet = getDirectlyReachableNodeSetFromCombinationNode(node);
        addMapCombineSetToOutgoingSet(combineSet, outSet);
      }
    }

  }

  private void addMapCombineSetToOutgoingSet(Set<HNode> combineSet, Set<HNode> outSet) {
    if (!mapCombineNodeSetToOutgoingNodeSet.containsKey(combineSet)) {
      mapCombineNodeSetToOutgoingNodeSet.put(combineSet, new HashSet<HNode>());
    }
    mapCombineNodeSetToOutgoingNodeSet.get(combineSet).addAll(outSet);
  }

  private Set<HNode> getDirectlyReachableNodeSetFromCombinationNode(HNode node) {
    // the method returns the set of nodes that are reachable from the current node
    // and do not combine the same set of skeleton nodes...

    Set<HNode> visited = new HashSet<HNode>();
    Set<HNode> reachableSet = new HashSet<HNode>();
    Set<HNode> combineSet = mapCombinationNodeToCombineNodeSet.get(node);

    recurDirectlyReachableNodeSetFromCombinationNode(node, combineSet, reachableSet, visited);

    return reachableSet;
  }

  private void recurDirectlyReachableNodeSetFromCombinationNode(HNode node, Set<HNode> combineSet,
      Set<HNode> reachableSet, Set<HNode> visited) {

    Set<HNode> outSet = getOutgoingNodeSet(node);
    for (Iterator iterator = outSet.iterator(); iterator.hasNext();) {
      HNode outNode = (HNode) iterator.next();

      if (outNode.isCombinationNode()) {
        Set<HNode> combineSetOfOutNode = mapCombinationNodeToCombineNodeSet.get(outNode);
        if (combineSetOfOutNode.equals(combineSet)) {
          recurDirectlyReachableNodeSetFromCombinationNode(outNode, combineSet, reachableSet,
              visited);
        } else {
          reachableSet.add(outNode);
        }
      } else if (outNode.isSkeleton()) {
        reachableSet.add(outNode);
      }

    }

  }

  public void writeGraph() {

    String graphName = "hierarchy" + name;
    graphName = graphName.replaceAll("[\\W]", "");

    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(graphName + ".dot"));

      bw.write("digraph " + graphName + " {\n");

      Iterator<HNode> iter = nodeSet.iterator();

      Set<HNode> addedNodeSet = new HashSet<HNode>();

      while (iter.hasNext()) {
        HNode u = iter.next();

        Set<HNode> outSet = getOutgoingNodeSet(u);

        if (outSet.size() == 0) {
          if (!addedNodeSet.contains(u)) {
            drawNode(bw, u);
            addedNodeSet.add(u);
          }
        } else {
          for (Iterator iterator = outSet.iterator(); iterator.hasNext();) {
            HNode v = (HNode) iterator.next();
            if (!addedNodeSet.contains(u)) {
              drawNode(bw, u);
              addedNodeSet.add(u);
            }
            if (!addedNodeSet.contains(v)) {
              drawNode(bw, v);
              addedNodeSet.add(v);
            }
            bw.write("" + u.getName() + " -> " + v.getName() + ";\n");
          }
        }

      }

      bw.write("}\n");
      bw.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void drawNode(BufferedWriter bw, HNode node) throws IOException {
    String shared = "";
    if (node.isSharedNode()) {
      shared = "*";
    }
    bw.write(node.getName() + " [label=\"" + node.getName() + shared + "\"]" + ";\n");
  }

}
