package Analysis.SSJava;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import IR.Descriptor;
import IR.FieldDescriptor;

public class HierarchyGraph {

  Descriptor desc;

  boolean isSCgraph;

  String name;

  // graph structure
  Map<HNode, Set<HNode>> mapHNodeToIncomingSet;
  Map<HNode, Set<HNode>> mapHNodeToOutgoingSet;

  Map<Descriptor, HNode> mapDescToHNode;
  Map<HNode, Set<Descriptor>> mapHNodeToDescSet;
  Map<HNode, HNode> mapHNodeToCurrentHNode; // tracking which node corresponds to the initial node
  Map<String, HNode> mapHNodeNameToCurrentHNode; // tracking which node corresponds to the initial
                                                 // node
  Map<HNode, Set<HNode>> mapMergeNodetoMergingSet;

  // data structures for a combination node
  Map<Set<HNode>, HNode> mapSkeletonNodeSetToCombinationNode;
  Map<HNode, Set<HNode>> mapCombinationNodeToCombineNodeSet;
  Map<Set<HNode>, HNode> mapCombineNodeSetToCombinationNode;
  Map<Set<HNode>, Set<HNode>> mapCombineNodeSetToOutgoingNodeSet;

  Map<HNode, Set<HNode>> mapNormalNodeToSCNodeReachToSet;

  Map<Set<HNode>, Set<HNode>> mapCombineNodeSetToFirstNodeOfChainSet;

  Set<HNode> nodeSet;

  // for the lattice generation
  Map<HNode, Integer> mapHNodeToUniqueIndex;
  Map<HNode, Set<Integer>> mapHNodeToBasis;
  Set<Integer> BASISTOPELEMENT;

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

    mapHNodeToUniqueIndex = new HashMap<HNode, Integer>();
    mapHNodeToBasis = new HashMap<HNode, Set<Integer>>();

    mapMergeNodetoMergingSet = new HashMap<HNode, Set<HNode>>();

    mapHNodeToCurrentHNode = new HashMap<HNode, HNode>();

    mapHNodeNameToCurrentHNode = new HashMap<String, HNode>();

    mapNormalNodeToSCNodeReachToSet = new HashMap<HNode, Set<HNode>>();

    mapCombineNodeSetToFirstNodeOfChainSet = new HashMap<Set<HNode>, Set<HNode>>();

    isSCgraph = false;
  }

  public void setSCGraph(boolean in) {
    isSCgraph = in;
  }

  public boolean isSCGraph() {
    return isSCgraph;
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

  public Map<HNode, HNode> getMapHNodeToCurrentHNode() {
    return mapHNodeToCurrentHNode;
  }

  public Map<String, HNode> getMapHNodeNameToCurrentHNode() {
    return mapHNodeNameToCurrentHNode;
  }

  public void setMapHNodeToCurrentHNode(Map<HNode, HNode> mapHNodeToCurrentHNode) {
    this.mapHNodeToCurrentHNode = mapHNodeToCurrentHNode;
  }

  public void setMapHNodeNameToCurrentHNode(Map<String, HNode> mapHNodeNameToCurrentHNode) {
    this.mapHNodeNameToCurrentHNode = mapHNodeNameToCurrentHNode;
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

    if (possibleCycleSet.size() > 0) {

      if (possibleCycleSet.size() == 1) {
        // System.out.println("possibleCycleSet=" + possibleCycleSet + "  from src=" + srcHNode
        // + " dstHNode=" + dstHNode);
        if (dstHNode.isSharedNode()) {
          // it has already been assigned shared node.
        } else {
          dstHNode.setSharedNode(true);
          // System.out.println("$$$setShared=" + dstHNode);
        }
        return;
      }

      // System.out.println("--- CYCLIC VALUE FLOW: " + srcHNode + " -> " + dstHNode);
      HNode newMergeNode = mergeNodes(possibleCycleSet);
      newMergeNode.setSharedNode(true);

    } else {
      getIncomingNodeSet(dstHNode).add(srcHNode);
      getOutgoingNodeSet(srcHNode).add(dstHNode);
      // System.out.println("add an edge " + srcHNode + " -> " + dstHNode);
    }

  }

  public void addNode(HNode node) {
    nodeSet.add(node);
  }

  public void addEdge(Descriptor src, Descriptor dst) {

    if (src.equals(LocationInference.LITERALDESC)) {
      // in this case, we do not need to add a source hnode
      // just add a destination hnode
      getHNode(dst);
    } else {
      HNode srcHNode = getHNode(src);
      HNode dstHNode = getHNode(dst);
      addEdge(srcHNode, dstHNode);
    }

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

      if (d.equals(LocationInference.TOPDESC)) {
        newNode.setSkeleton(true);
      }

      String symbol = d.getSymbol();
      if (symbol.startsWith(LocationInference.PCLOC) || symbol.startsWith(LocationInference.RLOC)) {
        newNode.setSkeleton(true);
      }

      mappingDescriptorToHNode(d, newNode);
      nodeSet.add(newNode);
    }
    return mapDescToHNode.get(d);
  }

  public HNode getHNode(String name) {
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      if (node.getName().equals(name)) {
        return node;
      }
    }
    return null;
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
    skeletonGraph.setMapHNodetoMergeSet(getMapHNodetoMergeSet());
    skeletonGraph.setMapHNodeToCurrentHNode(getMapHNodeToCurrentHNode());
    skeletonGraph.setMapHNodeNameToCurrentHNode(getMapHNodeNameToCurrentHNode());

    return skeletonGraph;

  }

  private Set<HNode> getDirectlyReachSkeletonSet(HNode node) {

    Set<HNode> visited = new HashSet<HNode>();
    Set<HNode> connected = new HashSet<HNode>();
    recurReachSkeletonSet(node, connected, visited);

    return connected;
  }

  public void removeRedundantEdges() {

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

  public void simplifyHierarchyGraph(LocationInference infer) {
    removeRedundantEdges();
    combineRedundantNodes(infer);
  }

  public void combineRedundantNodes(LocationInference infer) {
    // Combine field/parameter nodes who have the same set of incoming/outgoing edges.
    boolean isUpdated = false;
    do {
      isUpdated = combineTwoRedundatnNodes(infer);
    } while (isUpdated);
  }

  public Set<HNode> getIncomingNodeSet(HNode node) {
    if (!mapHNodeToIncomingSet.containsKey(node)) {
      mapHNodeToIncomingSet.put(node, new HashSet<HNode>());
    }
    return mapHNodeToIncomingSet.get(node);
  }

  public Set<HNode> getOutgoingNodeSet(HNode node) {
    if (!mapHNodeToOutgoingSet.containsKey(node)) {
      mapHNodeToOutgoingSet.put(node, new HashSet<HNode>());
    }
    return mapHNodeToOutgoingSet.get(node);
  }

  private boolean combineTwoRedundatnNodes(LocationInference infer) {
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node1 = (HNode) iterator.next();

      // if ((onlyCombinationNodes && (!node1.isCombinationNode()))
      // || (!onlyCombinationNodes && (!node1.isSkeleton()))) {
      // continue;
      // }

      if (!node1.isSkeleton()) {
        continue;
      }

      Set<HNode> incomingNodeSet1 = getIncomingNodeSet(node1);
      Set<HNode> outgoingNodeSet1 = getOutgoingNodeSet(node1);

      for (Iterator iterator2 = nodeSet.iterator(); iterator2.hasNext();) {
        HNode node2 = (HNode) iterator2.next();

        // if ((onlyCombinationNodes && (!node2.isCombinationNode()))
        // || (!onlyCombinationNodes && (!node2.isSkeleton()))) {
        // continue;
        // }

        if (!node2.isSkeleton()) {
          continue;
        }

        // System.out.println("node1=" + node1 + " vs node2=" + node2);
        if (!isEligibleForMerging(node1, node2)) {
          continue;
        }

        if (!node1.equals(node2)) {

          Set<HNode> incomingNodeSet2 = getIncomingNodeSet(node2);
          Set<HNode> outgoingNodeSet2 = getOutgoingNodeSet(node2);

          // System.out.println(node1 + " " + node2 + " MERGING incoming?=" + incomingNodeSet1
          // + " vs " + incomingNodeSet2);
          // System.out.println(node1 + " " + node2 + " MERGING outgoing?=" + outgoingNodeSet1
          // + " vs " + outgoingNodeSet2);

          if (incomingNodeSet1.equals(incomingNodeSet2)
              && outgoingNodeSet1.equals(outgoingNodeSet2)) {
            // need to merge node1 and node2
            // System.out.println("MERGE!!!!!!!!!!!!!");
            // ///////////////
            // merge two nodes only if every hierarchy graph in the inheritance hierarchy
            // that includes both nodes allows the merging of them...
            Set<HNode> mergeSet = new HashSet<HNode>();
            mergeSet.add(node1);
            mergeSet.add(node2);
            infer.isValidMergeInheritanceCheck(desc, mergeSet);

            // ///////////////

            mergeNodes(mergeSet);
            return true;
          }

        }
      }

    }
    return false;
  }

  private boolean isEligibleForMerging(HNode node1, HNode node2) {

    if (node1.isSharedNode() || node2.isSharedNode()) {

      // if either of nodes is a shared node,
      // all descriptors of node1 & node2 should have a primitive type

      Set<Descriptor> descSet = new HashSet<Descriptor>();
      descSet.addAll(getDescSetOfNode(node1));
      descSet.addAll(getDescSetOfNode(node2));

      for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
        Descriptor desc = (Descriptor) iterator.next();
        if (!LocationInference.isPrimitive(desc)) {
          return false;
        }
      }
      return true;
    }
    return true;
  }

  private void addEdgeWithNoCycleCheck(HNode srcHNode, HNode dstHNode) {
    getIncomingNodeSet(dstHNode).add(srcHNode);
    getOutgoingNodeSet(srcHNode).add(dstHNode);
    // System.out.println("addEdgeWithNoCycleCheck src=" + srcHNode + " -> " + dstHNode);
  }

  private HNode mergeNodes(Set<HNode> set) {

    Set<HNode> incomingNodeSet = new HashSet<HNode>();
    Set<HNode> outgoingNodeSet = new HashSet<HNode>();

    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      incomingNodeSet.addAll(getIncomingNodeSet(node));
      outgoingNodeSet.addAll(getOutgoingNodeSet(node));
    }

    String nodeName;
    boolean isMergeNode = false;
    nodeName = "MNode" + (LocationInference.locSeed++);
    isMergeNode = true;

    HNode newMergeNode = new HNode(nodeName);
    newMergeNode.setMergeNode(isMergeNode);

    nodeSet.add(newMergeNode);
    nodeSet.removeAll(set);

    // if the input set contains a skeleton node, need to set a new merge node as skeleton also
    boolean hasSkeleton = false;
    boolean hasShared = false;
    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      if (inNode.isSkeleton()) {
        hasSkeleton = true;
      }
      if (inNode.isSharedNode()) {
        hasShared = true;
      }
    }
    // System.out.println("-----Set merging node=" + newMergeNode + " as a skeleton=" + set
    // + " hasSkeleton=" + hasSkeleton + " CUR DESC=" + desc);
    newMergeNode.setSkeleton(hasSkeleton);
    newMergeNode.setSharedNode(hasShared);

    // System.out.println("-----MERGING NODE=" + set + " new node=" + newMergeNode);

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

    Set<HNode> mergedSkeletonNode = new HashSet<HNode>();
    for (Iterator<HNode> iter = set.iterator(); iter.hasNext();) {
      HNode merged = iter.next();
      if (merged.isSkeleton()) {
        mergedSkeletonNode.add(merged);
      }
    }

    // mapMergeNodetoMergingSet.put(newMergeNode, mergedSkeletonNode);
    // for (Iterator iterator = set.iterator(); iterator.hasNext();) {
    mapMergeNodetoMergingSet.put(newMergeNode, set);
    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      HNode mergedNode = (HNode) iterator.next();
      addMapHNodeToCurrentHNode(mergedNode, newMergeNode);
    }

    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      HNode hNode = (HNode) iterator.next();
      // System.out.println("old=" + hNode + "----->newNode=" + getCurrentHNode(hNode));
    }

    // System.out.println();

    return newMergeNode;
  }

  private void addMapHNodeToCurrentHNode(HNode curNode, HNode newNode) {

    if (curNode.isMergeNode()) {
      Set<HNode> mergingSet = getMergingSet(curNode);
      mergingSet.add(curNode);
      // System.out.println("-------addMapHNodeToCurrentHNode curNode=" + curNode + " meringSet="
      // + mergingSet + " newNode=" + newNode);
      for (Iterator iterator = mergingSet.iterator(); iterator.hasNext();) {
        HNode mergingNode = (HNode) iterator.next();
        mapHNodeToCurrentHNode.put(mergingNode, newNode);
        mapHNodeNameToCurrentHNode.put(mergingNode.getName(), newNode);
      }
    } else {
      mapHNodeToCurrentHNode.put(curNode, newNode);
      mapHNodeNameToCurrentHNode.put(curNode.getName(), newNode);
    }

  }

  public HNode getCurrentHNode(HNode node) {
    if (!mapHNodeToCurrentHNode.containsKey(node)) {
      mapHNodeToCurrentHNode.put(node, node);
    }
    return mapHNodeToCurrentHNode.get(node);
  }

  public HNode getCurrentHNode(String nodeName) {
    return mapHNodeNameToCurrentHNode.get(nodeName);
  }

  private Set<HNode> getMergingSet(HNode mergeNode) {
    Set<HNode> mergingSet = new HashSet<HNode>();
    Set<HNode> mergedNode = mapMergeNodetoMergingSet.get(mergeNode);
    for (Iterator iterator = mergedNode.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      if (node.isMergeNode()) {
        mergingSet.add(node);
        mergingSet.addAll(getMergingSet(node));
      } else {
        mergingSet.add(node);
      }
    }
    return mergingSet;
  }

  public Set<Descriptor> getDescSetOfNode(HNode node) {
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

  public Set<HNode> getReachableSCNodeSet(HNode startNode) {
    // returns the set of hnodes which is reachable from the startNode and is either SC node or a
    // node which is directly connected to the SC nodes
    Set<HNode> reachable = new HashSet<HNode>();
    Set<HNode> visited = new HashSet<HNode>();
    visited.add(startNode);
    recurReachableNodeSet(startNode, visited, reachable);
    return reachable;
  }

  public Set<HNode> getSCNodeReachToSet(HNode node) {
    if (!mapNormalNodeToSCNodeReachToSet.containsKey(node)) {
      mapNormalNodeToSCNodeReachToSet.put(node, new HashSet<HNode>());
    }
    return mapNormalNodeToSCNodeReachToSet.get(node);
  }

  private void recurReachableNodeSet(HNode node, Set<HNode> visited, Set<HNode> reachable) {

    Set<HNode> outSet = getOutgoingNodeSet(node);
    for (Iterator iterator = outSet.iterator(); iterator.hasNext();) {
      HNode out = (HNode) iterator.next();

      if (!visited.contains(out)) {
        visited.add(out);
        Set<HNode> reachableFromSCNodeSet = reachableFromSCNode(out);
        mapNormalNodeToSCNodeReachToSet.put(out, reachableFromSCNodeSet);
        if (out.isSkeleton() || out.isCombinationNode() || reachableFromSCNodeSet.size() > 0) {
          reachable.add(out);
        } else {
          visited.add(out);
          recurReachableNodeSet(out, visited, reachable);
        }

      }

    }

  }

  private Set<HNode> reachableFromSCNode(HNode node) {
    Set<HNode> visited = new HashSet<HNode>();
    visited.add(node);
    Set<HNode> reachable = new HashSet<HNode>();
    recurReachableFromSCNode(node, reachable, visited);
    return reachable;
  }

  private void recurReachableFromSCNode(HNode node, Set<HNode> reachable, Set<HNode> visited) {
    Set<HNode> inNodeSet = getIncomingNodeSet(node);
    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      if (inNode.isSkeleton() || inNode.isCombinationNode()) {
        visited.add(inNode);
        reachable.add(inNode);
      } else if (!visited.contains(inNode)) {
        visited.add(inNode);
        recurReachableFromSCNode(inNode, reachable, visited);
      }
    }
  }

  public Set<HNode> getDirectlyReachableSkeletonCombinationNodeFrom(HNode node,
      Set<HNode> combinationNodeSet) {
    Set<HNode> reachable = new HashSet<HNode>();
    Set<HNode> visited = new HashSet<HNode>();
    visited.add(node);
    recurDirectlyReachableSkeletonCombinationNodeFrom(node, visited, reachable, combinationNodeSet);
    return reachable;
  }

  public void recurDirectlyReachableSkeletonCombinationNodeFrom(HNode node, Set<HNode> visited,
      Set<HNode> reachable, Set<HNode> combinationNodeSet) {

    Set<HNode> outSet = getOutgoingNodeSet(node);
    for (Iterator iterator = outSet.iterator(); iterator.hasNext();) {
      HNode out = (HNode) iterator.next();

      if (!visited.contains(out)) {
        visited.add(out);
        if (out.isSkeleton()) {
          reachable.add(out);
        } else if (out.isCombinationNode()) {
          if (combinationNodeSet == null) {
            reachable.add(out);
          } else if (!combinationNodeSet.contains(out)) {
            reachable.add(out);
          } else {
            recurDirectlyReachableSkeletonCombinationNodeFrom(out, visited, reachable,
                combinationNodeSet);
          }
        } else {
          recurDirectlyReachableSkeletonCombinationNodeFrom(out, visited, reachable,
              combinationNodeSet);
        }

      }

    }

  }

  public HNode getDirectlyReachableSkeletonCombinationNodeFrom(HNode node) {
    Set<HNode> visited = new HashSet<HNode>();
    return recurDirectlyReachableSkeletonCombinationNodeFrom(node, visited);
  }

  public HNode recurDirectlyReachableSkeletonCombinationNodeFrom(HNode node, Set<HNode> visited) {

    Set<HNode> outSet = getOutgoingNodeSet(node);
    for (Iterator iterator = outSet.iterator(); iterator.hasNext();) {
      HNode out = (HNode) iterator.next();
      // if (!visited.contains(out)) {
      if (out.isCombinationNode() || out.isSkeleton()) {
        return out;
      } else {
        // visited.add(out);
        return getDirectlyReachableSkeletonCombinationNodeFrom(out);
      }
    }
    // }

    return null;
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

  public HNode getCombinationNode(Set<HNode> combineSet) {
    assert isSCGraph();
    if (!mapCombineNodeSetToCombinationNode.containsKey(combineSet)) {
      String name = "COMB" + (LocationInference.locSeed++);
      System.out.println("-NEW COMB NODE=" + name);
      HNode node = new HNode(name);
      node.setCombinationNode(true);
      nodeSet.add(node);
      mapCombineNodeSetToCombinationNode.put(combineSet, node);
      mapCombinationNodeToCombineNodeSet.put(node, combineSet);
    }

    return mapCombineNodeSetToCombinationNode.get(combineSet);
  }

  public Map<Set<HNode>, HNode> getMapCombineNodeSetToCombinationNode() {
    return mapCombineNodeSetToCombinationNode;
  }

  public Set<Set<HNode>> getCombineNodeSet() {
    return mapCombineNodeSetToOutgoingNodeSet.keySet();
  }

  public void insertCombinationNodesToGraph(HierarchyGraph simpleHierarchyGraph) {
    // add a new combination node where parameter/field flows are actually combined.

    simpleHierarchyGraph.identifyCombinationNodes();

    Set<Set<HNode>> keySet = simpleHierarchyGraph.getCombineNodeSet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Set<HNode> combineSet = (Set<HNode>) iterator.next();
      HNode combinationNode = getCombinationNode(combineSet);
      System.out.println("\n@INSERT COMBINATION NODE FOR combineSet=" + combineSet
          + "  --combinationNode=" + combinationNode);
      System.out.println("      --hierarchynodes="
          + simpleHierarchyGraph.getCombinationNodeSetByCombineNodeSet(combineSet));

      Set<HNode> simpleHNodeSet =
          simpleHierarchyGraph.getCombinationNodeSetByCombineNodeSet(combineSet);

      // check whether a hnode in the simple hierarchy graph is the first node of the chain
      // if all incoming combination nodes to the hnode have a different combination set from the
      // hnode, it is the first node of the chain
      for (Iterator iterator2 = simpleHNodeSet.iterator(); iterator2.hasNext();) {
        HNode simpleHNode = (HNode) iterator2.next();
        boolean isFirstNodeOfChain = true;
        Set<HNode> incomingNodeSet = simpleHierarchyGraph.getIncomingNodeSet(simpleHNode);
        for (Iterator iterator3 = incomingNodeSet.iterator(); iterator3.hasNext();) {
          HNode inNode = (HNode) iterator3.next();
          if (inNode.isCombinationNode()) {
            Set<HNode> inNodeCombineSet =
                simpleHierarchyGraph.getCombineSetByCombinationNode(inNode);
            if (inNodeCombineSet.equals(combineSet)) {
              isFirstNodeOfChain = false;
              break;
            }
          }
        }
        simpleHNode.setDirectCombinationNode(isFirstNodeOfChain);
        if (isFirstNodeOfChain) {
          simpleHierarchyGraph.addFirstNodeOfChain(combineSet, simpleHNode);
          System.out.println("IT IS THE FIRST NODE OF THE CHAIN:" + simpleHNode);
          // System.out.println("--->INCOMING NODES=");
          // Set<HNode> inNodeSet = simpleHierarchyGraph.getIncomingNodeSet(simpleHNode);
          // for (Iterator iterator3 = inNodeSet.iterator(); iterator3.hasNext();) {
          // HNode inNode = (HNode) iterator3.next();
          // System.out.println("          inNode=" + inNode + "   combineSet="
          // + simpleHierarchyGraph.getCombineSetByCombinationNode(inNode) + " SKELETON TO SET="
          // + simpleHierarchyGraph.getSkeleteNodeSetReachTo(inNode));
          // }
        }
      }

      // add an edge from a skeleton node to a combination node
      for (Iterator iterator2 = combineSet.iterator(); iterator2.hasNext();) {
        HNode inSkeletonNode = (HNode) iterator2.next();
        // System.out.println("--inSkeletonNode=" + inSkeletonNode + "  desc="
        // + inSkeletonNode.getDescriptor());
        HNode srcNode;
        if (inSkeletonNode.getDescriptor() == null) {
          // the node is merging one...
          srcNode = inSkeletonNode;
        } else {
          srcNode = getHNode(inSkeletonNode.getDescriptor());
        }
        // System.out.println("--srcNode=" + srcNode);
        System.out.println("     ADD EDGE SRC=" + srcNode + " -> " + combinationNode);
        addEdgeWithNoCycleCheck(srcNode, combinationNode);
      }

      // add an edge from the combination node to outgoing nodes
      Set<HNode> outSet = simpleHierarchyGraph.getOutgoingNodeSetByCombineSet(combineSet);
      for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
        HNode curNode = (HNode) iterator2.next();
        if (curNode.isCombinationNode()) {
          Set<HNode> combineNode = simpleHierarchyGraph.getCombineSetByCombinationNode(curNode);
          HNode outNode = getCombinationNode(combineNode);
          addEdgeWithNoCycleCheck(combinationNode, outNode);
        } else if (curNode.isSkeleton()) {
          // HNode dstNode2 = getHNode(curNode.getDescriptor());
          HNode dstNode = getCurrentHNode(curNode);
          // System.out.println("-----curNode=" + curNode + "------->" + dstNode + "    dstNode2="
          // + dstNode2);
          addEdgeWithNoCycleCheck(combinationNode, dstNode);
        }
      }

      // System.out.println("--");

    }

  }

  public Set<HNode> getSkeleteNodeSetReachToNoTransitive(HNode node) {

    Set<HNode> reachToSet = new HashSet<HNode>();
    Set<HNode> visited = new HashSet<HNode>();
    // visited.add(node);
    recurSkeletonReachTo(node, reachToSet, visited);

    // obsolete!
    // if a node reaches to one of elements in the reachToSet, we do not need to keep it
    // because the node is not directly connected to the combination node
    // removeRedundantReachToNodes(reachToSet);

    return removeTransitivelyReachToSet(reachToSet);
    // return reachToSet;
  }

  public Set<HNode> getSkeleteNodeSetReachTo(HNode node) {

    Set<HNode> reachToSet = new HashSet<HNode>();
    Set<HNode> visited = new HashSet<HNode>();
    // visited.add(node);
    recurSkeletonReachTo(node, reachToSet, visited);

    // obsolete!
    // if a node reaches to one of elements in the reachToSet, we do not need to keep it
    // because the node is not directly connected to the combination node
    // removeRedundantReachToNodes(reachToSet);

    // TODO
    return removeTransitivelyReachToSet(reachToSet);
    // return reachToSet;
  }

  private void recurSkeletonReachTo(HNode node, Set<HNode> reachToSet, Set<HNode> visited) {

    Set<HNode> inSet = getIncomingNodeSet(node);
    for (Iterator iterator = inSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();

      if (inNode.isSkeleton()) {
        visited.add(inNode);
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
    clone.setMapHNodetoMergeSet(getMapHNodetoMergeSet());
    clone.setMapHNodeToCurrentHNode(getMapHNodeToCurrentHNode());
    clone.setMapHNodeNameToCurrentHNode(getMapHNodeNameToCurrentHNode());

    return clone;
  }

  public void setMapCombineNodeSetToCombinationNode(Map<Set<HNode>, HNode> in) {
    mapCombineNodeSetToCombinationNode = in;
  }

  public Map<HNode, Set<HNode>> getMapHNodetoMergeSet() {
    return mapMergeNodetoMergingSet;
  }

  public void setMapHNodetoMergeSet(Map<HNode, Set<HNode>> mapHNodetoMergeSet) {
    this.mapMergeNodetoMergingSet = mapHNodetoMergeSet;
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
        // Set<HNode> tempSet = removeTransitivelyReachToSet(reachToSet);
        // System.out.println("ALL REACH SET=" + reachToSet);
        // reachToSet = removeTransitivelyReachToSet(reachToSet);

        Set<HNode> curReachToSet = new HashSet<HNode>();
        for (Iterator iterator2 = reachToSet.iterator(); iterator2.hasNext();) {
          HNode reachSkeletonNode = (HNode) iterator2.next();
          curReachToSet.add(getCurrentHNode(reachSkeletonNode));
        }

        // System.out.println("-curReachToSett=" + curReachToSet + "  reachToSet=" + reachToSet);

        reachToSet = curReachToSet;
        // System.out.println("$node=" + node + "   reachToNodeSet=" + reachToSet + " tempSet="
        // + tempSet);
        if (reachToSet.size() > 1) {
          // if (countSkeletonNodes(reachToSet) > 1) {
          System.out.println("\n-node=" + node + "  reachToSet=" + reachToSet);
          System.out.println("-set combinationnode=" + node);
          node.setCombinationNode(true);
          mapCombinationNodeToCombineNodeSet.put(node, reachToSet);

          // check if this node is the first node of the chain
          // boolean isFirstNodeOfChain = false;
          // Set<HNode> inNodeSet = getIncomingNodeSet(node);
          // for (Iterator iterator2 = inNodeSet.iterator(); iterator2.hasNext();) {
          // HNode inNode = (HNode) iterator2.next();
          // if (inNode.isSkeleton()) {
          // isFirstNodeOfChain = true;
          // } else if (inNode.isCombinationNode()) {
          // Set<HNode> inNodeReachToSet = getSkeleteNodeSetReachTo(inNode);
          // if (!reachToSet.equals(inNodeReachToSet)) {
          // isFirstNodeOfChain = true;
          // }
          // }
          // }
          //
          // if (isFirstNodeOfChain) {
          // node.setDirectCombinationNode(true);
          // addFirstNodeOfChain(reachToSet, node);
          // }

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

  public void addFirstNodeOfChain(Set<HNode> combineSet, HNode firstNode) {

    if (!mapCombineNodeSetToFirstNodeOfChainSet.containsKey(combineSet)) {
      mapCombineNodeSetToFirstNodeOfChainSet.put(combineSet, new HashSet<HNode>());
    }

    mapCombineNodeSetToFirstNodeOfChainSet.get(combineSet).add(firstNode);

  }

  public Set<HNode> getFirstNodeOfCombinationNodeChainSet(Set<HNode> combineNodeSet) {
    return mapCombineNodeSetToFirstNodeOfChainSet.get(combineNodeSet);
  }

  private Set<HNode> removeTransitivelyReachToSet(Set<HNode> reachToSet) {

    Set<HNode> toberemoved = new HashSet<HNode>();
    Set<HNode> visited = new HashSet<HNode>();
    for (Iterator iterator = reachToSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      visited.add(node);
      recurIsReachingTo(node, reachToSet, toberemoved, visited);
    }

    Set<HNode> rSet = new HashSet<HNode>();
    rSet.addAll(reachToSet);
    rSet.removeAll(toberemoved);
    return rSet;
  }

  private void recurIsReachingTo(HNode curNode, Set<HNode> reachToSet, Set<HNode> toberemoved,
      Set<HNode> visited) {
    Set<HNode> inNodeSet = getIncomingNodeSet(curNode);

    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      if (reachToSet.contains(inNode)) {
        toberemoved.add(inNode);
      } else if (!visited.contains(inNode)) {
        visited.add(inNode);
        recurIsReachingTo(inNode, reachToSet, toberemoved, visited);
      }
    }

  }

  public Map<HNode, Set<HNode>> getMapCombinationNodeToCombineNodeSet() {
    return mapCombinationNodeToCombineNodeSet;
  }

  public int countSkeletonNodes(Set<HNode> set) {
    int count = 0;

    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      Set<Descriptor> descSet = getDescSetOfNode(node);
      count += descSet.size();
    }

    return count;
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

  private Set<HNode> getReachableNodeSetFrom(HNode node) {

    Set<HNode> reachableSet = new HashSet<HNode>();
    Set<HNode> visited = new HashSet<HNode>();

    recurReachableNodeSetFrom(node, reachableSet, visited);

    return reachableSet;
  }

  private void recurReachableNodeSetFrom(HNode node, Set<HNode> reachableSet, Set<HNode> visited) {

    Set<HNode> outgoingNodeSet = getOutgoingNodeSet(node);
    for (Iterator iterator = outgoingNodeSet.iterator(); iterator.hasNext();) {
      HNode outNode = (HNode) iterator.next();
      reachableSet.add(outNode);
      if (!visited.contains(outNode)) {
        visited.add(outNode);
        recurReachableNodeSetFrom(outNode, reachableSet, visited);
      }
    }

  }

  public void assignUniqueIndexToNode() {
    int idx = 1;
    // System.out.println("nodeSet=" + nodeSet);
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      mapHNodeToUniqueIndex.put(node, idx);
      idx++;
    }

    BASISTOPELEMENT = new HashSet<Integer>();
    for (int i = 1; i < idx + 1; i++) {
      BASISTOPELEMENT.add(i);
    }
  }

  public BasisSet computeBasisSet(Set<HNode> notGenerateSet) {

    // assign a unique index to a node
    assignUniqueIndexToNode();

    // compute basis for each node
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();

      if (notGenerateSet.contains(node)) {
        System.out.println("%%%SKIP =" + node);
        continue;
      }
      Set<Integer> basis = new HashSet<Integer>();
      basis.addAll(BASISTOPELEMENT);

      Set<HNode> reachableNodeSet = getReachableNodeSetFrom(node);
      // System.out.println("node=" + node + "    reachableNodeSet=" + reachableNodeSet);
      // System.out.println("mapHNodeToUniqueIndex.get(node)=" + mapHNodeToUniqueIndex.get(node));
      // if a node is reachable from the current node
      // need to remove the index of the reachable node from the basis

      basis.remove(getHNodeIndex(node));
      for (Iterator iterator2 = reachableNodeSet.iterator(); iterator2.hasNext();) {
        HNode reachableNode = (HNode) iterator2.next();
        // System.out.println("reachableNode=" + reachableNode);
        // System.out.println("getHNodeIndex(reachableNode))="
        // + mapHNodeToUniqueIndex.get(reachableNode));
        int idx = getHNodeIndex(reachableNode);
        basis.remove(idx);
      }

      mapHNodeToBasis.put(node, basis);
    }

    // construct the basis set

    BasisSet basisSet = new BasisSet();

    Set<HNode> keySet = mapHNodeToBasis.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      Set<Integer> basis = mapHNodeToBasis.get(node);
      basisSet.addElement(basis, node);
    }

    return basisSet;

  }

  public int getHNodeIndex(HNode node) {
    return mapHNodeToUniqueIndex.get(node).intValue();
  }

  public Map<HNode, Integer> getMapHNodeToUniqueIndex() {
    return mapHNodeToUniqueIndex;
  }

  public Map<HNode, Set<Integer>> getMapHNodeToBasis() {
    return mapHNodeToBasis;
  }

  public Set<HNode> getCombinationNodeSetByCombineNodeSet(Set<HNode> combineSkeletonNodeSet) {

    Set<HNode> combinationNodeSet = new HashSet<HNode>();
    Set<HNode> keySet = mapCombinationNodeToCombineNodeSet.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      HNode key = (HNode) iterator.next();

      if (mapCombinationNodeToCombineNodeSet.get(key).equals(combineSkeletonNodeSet)) {
        combinationNodeSet.add(key);
      }
    }

    return combinationNodeSet;
  }

  public void writeGraph() {
    writeGraph(false);
  }

  public void writeGraph(boolean isSimple) {

    String graphName = "hierarchy" + name;
    graphName = graphName.replaceAll("[\\W]", "");

    if (isSimple) {
      graphName += "_PAPER";
    }

    // System.out.println("***graphName=" + graphName + "   node siz=" + nodeSet.size());

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
            if (!isSimple) {
              drawNode(bw, u);
            } else {
              drawNodeName(bw, u);
            }
            addedNodeSet.add(u);
          }
        } else {
          for (Iterator iterator = outSet.iterator(); iterator.hasNext();) {
            HNode v = (HNode) iterator.next();
            if (!addedNodeSet.contains(u)) {
              if (!isSimple) {
                drawNode(bw, u);
              } else {
                drawNodeName(bw, u);
              }
              addedNodeSet.add(u);
            }
            if (!addedNodeSet.contains(v)) {
              if (!isSimple) {
                drawNode(bw, v);
              } else {
                drawNodeName(bw, v);
              }
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

  public boolean contains(HNode node) {
    return nodeSet.contains(node);
  }

  public boolean isDirectlyConnectedTo(HNode src, HNode dst) {
    return getOutgoingNodeSet(src).contains(dst);
  }

  private String convertMergeSetToString(Set<HNode> mergeSet) {
    String str = "";
    for (Iterator iterator = mergeSet.iterator(); iterator.hasNext();) {
      HNode merged = (HNode) iterator.next();
      if (merged.isMergeNode()) {
        str += " " + convertMergeSetToString(mapMergeNodetoMergingSet.get(merged));
      } else {
        str += " " + merged.getName();
      }
    }
    return str;
  }

  private void drawNodeName(BufferedWriter bw, HNode node) throws IOException {
    String nodeName = node.getNamePropertyString();
    bw.write(node.getName() + " [label=\"" + nodeName + "\"]" + ";\n");
  }

  private void drawNode(BufferedWriter bw, HNode node) throws IOException {
    String nodeName;
    if (node.isMergeNode()) {
      nodeName = node.getNamePropertyString();
      Set<HNode> mergeSet = mapMergeNodetoMergingSet.get(node);
      // System.out.println("node=" + node + "   mergeSet=" + mergeSet);
      nodeName += ":" + convertMergeSetToString(mergeSet);
    } else {
      nodeName = node.getNamePropertyString();
    }
    bw.write(node.getName() + " [label=\"" + nodeName + "\"]" + ";\n");
  }

  public int countHopFromTopLocation(HNode node) {

    Set<HNode> inNodeSet = getIncomingNodeSet(node);
    int count = 0;
    if (inNodeSet.size() > 0) {
      count = recurCountHopFromTopLocation(inNodeSet, 1);
    }

    return count;
  }

  private int recurCountHopFromTopLocation(Set<HNode> nodeSet, int curCount) {

    int max = curCount;
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      Set<HNode> inNodeSet = getIncomingNodeSet(node);
      if (inNodeSet.size() > 0) {
        int recurCount = recurCountHopFromTopLocation(inNodeSet, curCount + 1);
        if (max < recurCount) {
          max = recurCount;
        }
      }
    }
    return max;
  }

  public Stack<String> computeDistance2(HNode startNode, Set<HNode> endNodeSet,
      HierarchyGraph scGraph, Set<HNode> combineSet) {
    System.out
        .println("#####computeDistanceance startNode=" + startNode + " endNode=" + endNodeSet);
    Stack<String> trace = new Stack<String>();
    return recur_computeDistance2(startNode, endNodeSet, scGraph, 0, combineSet, trace);
  }

  public Stack<String> computeDistance(HNode startNode, Set<HNode> endNodeSet,
      HierarchyGraph scGraph, Set<HNode> combineSet) {
    System.out
        .println("#####computeDistanceance startNode=" + startNode + " endNode=" + endNodeSet);
    Stack<String> trace = new Stack<String>();
    return recur_computeDistance(startNode, endNodeSet, 0, combineSet, trace);
  }

  private Stack<String> recur_computeDistance(HNode curNode, Set<HNode> endNodeSet, int count,
      Set<HNode> combineSet, Stack<String> trace) {

    if (!curNode.isSkeleton()) {
      if (curNode.isSharedNode()) {
        trace.add("S");
      } else {
        trace.add("N");
      }
    }

    if (endNodeSet.contains(curNode)) {
      // it reaches to one of endNodeSet
      return trace;
    }

    Set<HNode> inNodeSet = getIncomingNodeSet(curNode);

    int curMaxDistance = 0;
    Stack<String> curMaxTrace = (Stack<String>) trace.clone();
    ;
    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      // traverse more...

      if (inNode.isCombinationNode() && combineSet != null) {
        // check if inNode have the same combination set of the starting node
        Set<HNode> inNodeCombineSet = getCombineSetByCombinationNode(inNode);
        if (!inNodeCombineSet.equals(combineSet)) {
          continue;
        }
      }

      // System.out.println("    traverse more to" + inNode + "  before-trace=" + trace);
      Stack<String> newTrace = (Stack<String>) trace.clone();
      Stack<String> curTrace =
          recur_computeDistance(inNode, endNodeSet, count, combineSet, newTrace);
      // System.out.println("curTracerTrace=" + curTrace);

      if (curTrace != null && curTrace.size() > curMaxDistance) {
        curMaxTrace = curTrace;
        curMaxDistance = curTrace.size();
      }
    }
    return curMaxTrace;

  }

  private Stack<String> recur_computeDistance2(HNode curNode, Set<HNode> endNodeSet,
      HierarchyGraph scGraph, int count, Set<HNode> combineSet, Stack<String> trace) {

    if (!curNode.isSkeleton()) {
      if (curNode.isSharedNode()) {
        trace.add("S");
      } else {
        trace.add("N");
      }
    }

    System.out.println("   curNode=" + curNode + "  curTrace=" + trace);
    // System.out.println("     curNode=" + curNode + "  curSCNode="
    // + scGraph.getCurrentHNode(curNode) + " contains="
    // + endNodeSet.contains(scGraph.getCurrentHNode(curNode)));
    if (endNodeSet.contains(scGraph.getCurrentHNode(curNode))) {
      // it reaches to one of endNodeSet
      return trace;
    }

    Set<HNode> inNodeSet = getIncomingNodeSet(curNode);

    int curMaxDistance = 0;
    Stack<String> curMaxTrace = (Stack<String>) trace.clone();
    ;
    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      // traverse more...

      if (inNode.isCombinationNode() && combineSet != null) {
        // check if inNode have the same combination set of the starting node
        Set<HNode> inNodeCombineSet = getCombineSetByCombinationNode(inNode);
        if (!inNodeCombineSet.equals(combineSet)) {
          continue;
        }
      }

      // Stack<String> newTrace = (Stack<String>) trace.clone();
      // Stack<String> curTrace =
      // recur_computeDistance(inNode, endNodeSet, scGraph, count, combineSet, newTrace);
      // if (curTrace != null) {
      // return curTrace;
      // }

      Set<HNode> inReachToNodeSet = getSkeleteNodeSetReachToNoTransitive(inNode);
      Set<HNode> inCurReachToNodeSet = new HashSet<HNode>();
      for (Iterator iterator2 = inReachToNodeSet.iterator(); iterator2.hasNext();) {
        HNode aboveNode = (HNode) iterator2.next();
        inCurReachToNodeSet.add(getCurrentHNode(aboveNode));
      }

      if (combineSet != null || inCurReachToNodeSet.equals(endNodeSet)) {
        System.out
            .println("        traverse to incomingNode=" + inNode + "  before-trace=" + trace);

        Stack<String> newTrace = (Stack<String>) trace.clone();
        Stack<String> curTrace =
            recur_computeDistance2(inNode, endNodeSet, scGraph, count, combineSet, newTrace);

        if (curTrace != null && curTrace.size() > curMaxDistance) {
          curMaxTrace = curTrace;
          curMaxDistance = curTrace.size();
        }
      } else {
        System.out.println("NOT TRAVERSE a new inNode=" + inNode + " inReachToNodeSet="
            + inCurReachToNodeSet);
      }

    }
    return curMaxTrace;
  }

  public int countNonSharedNode(HNode startNode, Set<HNode> endNodeSet) {
    System.out.println("countNonSharedNode startNode=" + startNode + " endNode=" + endNodeSet);
    return recur_countNonSharedNode(startNode, endNodeSet, 0);
  }

  private int recur_countNonSharedNode(HNode startNode, Set<HNode> endNodeSet, int count) {

    Set<HNode> inNodeSet = getIncomingNodeSet(startNode);

    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      if (endNodeSet.contains(inNode)) {
        count++;
        return count;
      } else {
        if (!inNode.isSharedNode()) {
          count++;
        }
        return recur_countNonSharedNode2(inNode, endNodeSet, count);
      }
    }

    return 0;
  }

  public int countNonSharedNode2(HNode startNode, Set<HNode> endNodeSet) {
    System.out.println("countNonSharedNode startNode=" + startNode + " endNode=" + endNodeSet);
    return recur_countNonSharedNode2(startNode, endNodeSet, 0);
  }

  private int recur_countNonSharedNode2(HNode startNode, Set<HNode> endNodeSet, int count) {

    Set<HNode> inNodeSet = getIncomingNodeSet(startNode);

    if (inNodeSet.size() == 0) {
      // it is directly connected to the TOP node
    }

    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      if (endNodeSet.contains(inNode)) {
        return count;
      } else {
        if (!inNode.isSharedNode()) {
          count++;
        }
        return recur_countNonSharedNode2(inNode, endNodeSet, count);
      }
    }

    // System.out.println("startNode=" + startNode + " inNodeSet=" + inNodeSet);
    // HNode inNode = inNodeSet.iterator().next();
    return -1;
  }

  public void removeIsolatedNodes() {
    Set<HNode> toberemoved = new HashSet<HNode>();
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      if (getIncomingNodeSet(node).isEmpty() && getOutgoingNodeSet(node).isEmpty()) {
        toberemoved.add(node);
      }
    }
    nodeSet.removeAll(toberemoved);
  }
}
