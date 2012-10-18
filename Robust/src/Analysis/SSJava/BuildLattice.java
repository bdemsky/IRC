package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import IR.Descriptor;
import IR.MethodDescriptor;
import Util.Pair;

public class BuildLattice {

  private LocationInference infer;

  private final HNode topNode;
  private final HNode bottomNode;

  public BuildLattice(LocationInference infer) {
    this.infer = infer;
    topNode = new HNode(infer.ssjava.TOP);
    bottomNode = new HNode(infer.ssjava.BOTTOM);
  }

  public SSJavaLattice<String> buildLattice(Descriptor desc) {

    HierarchyGraph inputGraph = infer.getSkeletonCombinationHierarchyGraph(desc);
    LocationSummary locSummary = infer.getLocationSummary(desc);

    Set<HNode> nodeSetWithCompositeLocation = new HashSet<HNode>();
    if (desc instanceof MethodDescriptor) {
      FlowGraph flowGraph = infer.getFlowGraph((MethodDescriptor) desc);

      for (Iterator iterator = inputGraph.getNodeSet().iterator(); iterator.hasNext();) {
        HNode hnode = (HNode) iterator.next();
        Descriptor hnodeDesc = hnode.getDescriptor();
        if (hnodeDesc != null) {
          NTuple<Descriptor> descTuple = new NTuple<Descriptor>();
          descTuple.add(hnodeDesc);

          if (flowGraph.contains(descTuple)) {
            FlowNode flowNode = flowGraph.getFlowNode(descTuple);
            if (flowNode.getCompositeLocation() != null) {
              nodeSetWithCompositeLocation.add(hnode);
            }
          }

        }
      }

    }

    BasisSet basisSet = inputGraph.computeBasisSet(nodeSetWithCompositeLocation);
    debug_print(inputGraph);

    Family family = generateFamily(basisSet);
    Map<Set<Integer>, Set<Set<Integer>>> mapImSucc = coveringGraph(basisSet, family);

    SSJavaLattice<String> lattice = buildLattice(basisSet, inputGraph, locSummary, mapImSucc);
    return lattice;

  }

  private SSJavaLattice<String> buildLattice(BasisSet basisSet, HierarchyGraph inputGraph,
      LocationSummary locSummary, Map<Set<Integer>, Set<Set<Integer>>> mapImSucc) {

    SSJavaLattice<String> lattice =
        new SSJavaLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM);

    Map<Set<Integer>, String> mapFToLocName = new HashMap<Set<Integer>, String>();

    Set<Set<Integer>> keySet = mapImSucc.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Set<Integer> higher = (Set<Integer>) iterator.next();

      String higherName = generateElementName(basisSet, inputGraph, mapFToLocName, higher);

      HNode higherNode = inputGraph.getHNode(higherName);
      if (higherNode != null && higherNode.isSharedNode()) {
        lattice.addSharedLoc(higherName);
      }
      Set<Descriptor> descSet = inputGraph.getDescSetOfNode(higherNode);
      // System.out.println("higherName=" + higherName + "  higherNode=" + higherNode + "  descSet="
      // + descSet);
      for (Iterator iterator2 = descSet.iterator(); iterator2.hasNext();) {
        Descriptor d = (Descriptor) iterator2.next();
        locSummary.addMapHNodeNameToLocationName(d.getSymbol(), higherName);
      }
      // locSummary.addMapHNodeNameToLocationName(higherName, higherName);

      Set<Set<Integer>> lowerSet = mapImSucc.get(higher);
      for (Iterator iterator2 = lowerSet.iterator(); iterator2.hasNext();) {
        Set<Integer> lower = (Set<Integer>) iterator2.next();

        String lowerName = generateElementName(basisSet, inputGraph, mapFToLocName, lower);
        HNode lowerNode = inputGraph.getHNode(lowerName);
        if (lowerNode != null && lowerNode.isSharedNode()) {
          lattice.addSharedLoc(lowerName);
        }

        Set<Descriptor> lowerDescSet = inputGraph.getDescSetOfNode(lowerNode);
        // System.out.println("lowerName=" + lowerName + "  lowerNode=" + lowerNode + "  descSet="
        // + lowerDescSet);
        for (Iterator iterator3 = lowerDescSet.iterator(); iterator3.hasNext();) {
          Descriptor d = (Descriptor) iterator3.next();
          locSummary.addMapHNodeNameToLocationName(d.getSymbol(), lowerName);
        }
        // locSummary.addMapHNodeNameToLocationName(lowerName, lowerName);

        if (higher.size() == 0) {
          // empty case
          lattice.put(lowerName);
        } else {
          lattice.addRelationHigherToLower(higherName, lowerName);
        }

      }

    }

    return lattice;
  }

  public HNode getCombinationNodeInSCGraph(Descriptor desc, HNode nodeFromSimpleGraph) {

    HierarchyGraph scGraph = infer.getSkeletonCombinationHierarchyGraph(desc);

    if (nodeFromSimpleGraph.isSkeleton()) {
      return scGraph.getCurrentHNode(nodeFromSimpleGraph);
    }

    Set<HNode> combineSkeletonNodeSet =
        infer.getSimpleHierarchyGraph(desc).getCombineSetByCombinationNode(nodeFromSimpleGraph);
    HNode combinationNodeInSCGraph =
        infer.getSkeletonCombinationHierarchyGraph(desc).getMapCombineNodeSetToCombinationNode()
            .get(combineSkeletonNodeSet);

    // Set<HNode> combineSkeletonNodeSet =
    // infer.getSimpleHierarchyGraph(desc).getCombineSetByCombinationNode(simpleGraphNode);
    // HNode combinationNodeInSCGraph =
    // infer.getSkeletonCombinationHierarchyGraph(desc).getCombinationNode(combineSkeletonNodeSet);
    return combinationNodeInSCGraph;
  }

  public SSJavaLattice<String> insertIntermediateNodesToStraightLine(Descriptor desc,
      SSJavaLattice<String> skeletonLattice) {

    // perform DFS that starts from each skeleton/combination node and ends by another
    // skeleton/combination node

    HierarchyGraph simpleGraph = infer.getSimpleHierarchyGraph(desc);
    HierarchyGraph scGraph = infer.getSkeletonCombinationHierarchyGraph(desc);
    LocationSummary locSummary = infer.getLocationSummary(desc);

    SSJavaLattice<String> lattice = skeletonLattice.clone();

    Set<HNode> visited = new HashSet<HNode>();

    Set<HNode> nodeSet = simpleGraph.getNodeSet();

    Map<TripleItem, String> mapIntermediateLoc = new HashMap<TripleItem, String>();

    System.out.println("*insert=" + desc);
    System.out.println("***nodeSet=" + nodeSet);
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      System.out.println("node=" + node);
      if (node.isSkeleton() && (!visited.contains(node))) {
        visited.add(node);

        Set<HNode> outSet = simpleGraph.getOutgoingNodeSet(node);
        for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
          HNode outNode = (HNode) iterator2.next();

          if (!outNode.isSkeleton()) {
            if (outNode.isCombinationNode()) {
              expandCombinationNode(desc, lattice, visited, mapIntermediateLoc, locSummary, outNode);
            } else {
              // we have a node that is neither combination or skeleton node
              // System.out.println("%%%skeleton node=" + node + "  outNode=" + outNode);
              HNode startNode = scGraph.getCurrentHNode(node);

              // if (node.getDescriptor() != null) {
              // // node is a skeleton node and it might be merged into another node in the SC
              // graph.
              // startNode = scGraph.getHNode(node.getDescriptor());
              // } else {
              // // this node has already been merged before the SC graph.
              // startNode = node;
              // }

              Set<HNode> endNodeSetFromSimpleGraph =
                  simpleGraph.getDirectlyReachableSkeletonCombinationNodeFrom(outNode, null);

              // System.out.println("endNodeSetFromSimpleGraph=" + endNodeSetFromSimpleGraph
              // + "   from=" + outNode);
              Set<HNode> endCombNodeSet = new HashSet<HNode>();
              for (Iterator iterator3 = endNodeSetFromSimpleGraph.iterator(); iterator3.hasNext();) {
                HNode endNode = (HNode) iterator3.next();
                endCombNodeSet.add(getCombinationNodeInSCGraph(desc, endNode));
              }
              // System.out.println("endCombNodeSet=" + endCombNodeSet);
              visited.add(outNode);
              if (endCombNodeSet.size() > 0) {
                // follows the straight line up to another skeleton/combination node
                endCombNodeSet = removeTransitivelyReachToNode(desc, startNode, endCombNodeSet);
              } else if (endCombNodeSet.size() == 0) {
                // the outNode is (directly/transitively) connected to the bottom node
                // therefore, we just add a dummy bottom HNode to the endCombNodeSet.
                endCombNodeSet.add(bottomNode);
              }

              recurDFSNormalNode(desc, lattice, startNode, endCombNodeSet, visited,
                  mapIntermediateLoc, 1, locSummary, outNode);
            }

          }

        }
      } else if (!node.isSkeleton() && !node.isCombinationNode() && !node.isMergeNode()
          && !visited.contains(node)) {

        System.out.println("n=" + node);

        // an intermediate node 'node' may be located between "TOP" location and a skeleton node
        if (simpleGraph.getIncomingNodeSet(node).size() == 0) {
          // this node will be directly connected to the TOP location
          // start adding the following nodes from this node

          // Set<HNode> endNodeSetFromSimpleGraph =
          // simpleGraph.getDirectlyReachableSkeletonCombinationNodeFrom(node, null);

          Set<HNode> endNodeSet = simpleGraph.getReachableSCNodeSet(node);

          Set<HNode> endCombNormalNodeSet = new HashSet<HNode>();
          for (Iterator iterator3 = endNodeSet.iterator(); iterator3.hasNext();) {
            HNode endNode = (HNode) iterator3.next();
            if (endNode.isSkeleton() || endNode.isCombinationNode()) {
              endCombNormalNodeSet.add(getCombinationNodeInSCGraph(desc, endNode));
            } else {
              endCombNormalNodeSet.addAll(simpleGraph.getSCNodeReachToSet(endNode));
            }
          }

          HNode startNode = topNode;
          visited.add(startNode);
          if (endCombNormalNodeSet.size() > 0) {
            // follows the straight line up to another skeleton/combination node
            // endCombNodeSet = removeTransitivelyReachToNode(desc, node, endCombNodeSet);
            recurDFSNormalNode(desc, lattice, startNode, endCombNormalNodeSet, visited,
                mapIntermediateLoc, 1, locSummary, node);
          }

        }

      }
    }

    return lattice;

  }

  private void expandCombinationNode(Descriptor desc, SSJavaLattice<String> lattice,
      Set<HNode> visited, Map<TripleItem, String> mapIntermediateLoc, LocationSummary locSummary,
      HNode cnode) {

    // expand the combination node 'outNode'
    // here we need to expand the corresponding combination location in the lattice
    HNode combinationNodeInSCGraph = getCombinationNodeInSCGraph(desc, cnode);

    HierarchyGraph simpleGraph = infer.getSimpleHierarchyGraph(desc);

    Set<HNode> combineSkeletonNodeSet = simpleGraph.getCombineSetByCombinationNode(cnode);

    // System.out.println("combineSkeletonNodeSet=" + combineSkeletonNodeSet);

    Set<HNode> combinationNodeSet =
        simpleGraph.getCombinationNodeSetByCombineNodeSet(combineSkeletonNodeSet);

    // System.out.println("combinationNodeSet=" + combinationNodeSet);

    Set<HNode> endNodeSetFromSimpleGraph =
        simpleGraph.getDirectlyReachableSkeletonCombinationNodeFrom(cnode, combinationNodeSet);
    // System.out.println("-endNodeSetFromSimpleGraph=" + endNodeSetFromSimpleGraph);
    Set<HNode> endCombNodeSet = new HashSet<HNode>();
    for (Iterator iterator3 = endNodeSetFromSimpleGraph.iterator(); iterator3.hasNext();) {
      HNode endNode = (HNode) iterator3.next();
      endCombNodeSet.add(getCombinationNodeInSCGraph(desc, endNode));
    }
    visited.add(cnode);

    // follows the straight line up to another skeleton/combination node
    if (endCombNodeSet.size() > 0) {
      System.out.println("---endCombNodeSet=" + endCombNodeSet);
      endCombNodeSet =
          removeTransitivelyReachToNode(desc, combinationNodeInSCGraph, endCombNodeSet);

      recurDFS(desc, lattice, combinationNodeInSCGraph, endCombNodeSet, visited,
          mapIntermediateLoc, 1, locSummary, cnode);
    } else {
      endCombNodeSet.add(bottomNode);
      System.out.println("---endCombNodeSet is zero");
      System.out.println("---endNodeSetFromSimpleGraph=" + endNodeSetFromSimpleGraph);
      System.out.println("---incoming=" + simpleGraph.getIncomingNodeSet(cnode));
      recurDFS(desc, lattice, combinationNodeInSCGraph, endCombNodeSet, visited,
          mapIntermediateLoc, 1, locSummary, cnode);

    }

  }

  private Set<HNode> removeTransitivelyReachToNode(Descriptor desc, HNode startNode,
      Set<HNode> endNodeSet) {

    // if an end node is not directly connected to the start node in the SC graph
    // replace it with a directly connected one which transitively reaches to it.

    HierarchyGraph scGraph = infer.getSkeletonCombinationHierarchyGraph(desc);

    Set<HNode> newEndNodeSet = new HashSet<HNode>();
    for (Iterator iterator = endNodeSet.iterator(); iterator.hasNext();) {
      HNode endNode = (HNode) iterator.next();
      if (scGraph.isDirectlyConnectedTo(startNode, endNode)) {
        newEndNodeSet.add(endNode);
      } else {
        HNode newEndNode =
            getDirectlyReachableNodeFromStartNodeReachToEndNode(scGraph, startNode, endNode);
        // System.out.println("#### old END NODE=" + endNode + " --->" + newEndNode);
        newEndNodeSet.add(newEndNode);
      }
    }

    // System.out.println("removeTransitivelyReachToNode=" + endNodeSet + "  newSet=" +
    // newEndNodeSet);

    return newEndNodeSet;

  }

  private HNode getDirectlyReachableSCNodeFromEndNode(HierarchyGraph scGraph, HNode startNode,
      Set<HNode> endNodeSet) {

    System.out.println("getDirectlyReachableSCNodeFromEndNode start=" + startNode + " endNodeSet="
        + endNodeSet);
    Set<HNode> newStartNodeSet = new HashSet<HNode>();

    for (Iterator iterator = endNodeSet.iterator(); iterator.hasNext();) {
      HNode endNode = (HNode) iterator.next();
      Set<HNode> connectedToEndNodeSet = scGraph.getIncomingNodeSet(endNode);

      for (Iterator iterator2 = connectedToEndNodeSet.iterator(); iterator2.hasNext();) {
        HNode curNode = (HNode) iterator2.next();
        if (recurConnectedFromStartNode(scGraph, startNode, curNode, new HashSet<HNode>())) {
          newStartNodeSet.add(curNode);
        }
      }
    }

    System.out.println("newStartNodeSet=" + newStartNodeSet);

    if (newStartNodeSet.size() == 0) {
      newStartNodeSet.add(startNode);
    }

    return newStartNodeSet.iterator().next();
  }

  private boolean recurConnectedFromStartNode(HierarchyGraph scGraph, HNode startNode,
      HNode curNode, Set<HNode> visited) {
    // return true if curNode is transitively connected from the startNode

    boolean isConnected = false;
    Set<HNode> inNodeSet = scGraph.getIncomingNodeSet(curNode);
    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      HNode in = (HNode) iterator.next();
      if (in.equals(startNode)) {
        return true;
      } else {
        visited.add(in);
        isConnected |= recurConnectedFromStartNode(scGraph, startNode, in, visited);
      }
    }

    return isConnected;
  }

  private HNode getDirectlyReachableNodeFromStartNodeReachToEndNode(HierarchyGraph scGraph,
      HNode startNode, HNode endNode) {
    System.out.println("getDirectlyReachableNodeFromStartNodeReachToEndNode start=" + startNode
        + " end=" + endNode);
    Set<HNode> connected = new HashSet<HNode>();
    recurDirectlyReachableNodeFromStartNodeReachToEndNode(scGraph, startNode, endNode, connected);
    if (connected.size() == 0) {
      connected.add(endNode);
    }
    System.out.println("connected=" + connected);

    return connected.iterator().next();
  }

  private void recurDirectlyReachableNodeFromStartNodeReachToEndNode(HierarchyGraph scGraph,
      HNode startNode, HNode curNode, Set<HNode> connected) {

    Set<HNode> inNodeSet = scGraph.getIncomingNodeSet(curNode);
    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      HNode inNode = (HNode) iterator.next();
      if (inNode.equals(startNode)) {
        connected.add(curNode);
      } else {
        recurDirectlyReachableNodeFromStartNodeReachToEndNode(scGraph, startNode, inNode, connected);
      }
    }

  }

  private void recurDFSNormalNode(Descriptor desc, SSJavaLattice<String> lattice, HNode startNode,
      Set<HNode> endNodeSet, Set<HNode> visited, Map<TripleItem, String> mapIntermediateLoc,
      int idx, LocationSummary locSummary, HNode curNode) {

    TripleItem item = new TripleItem(startNode, endNodeSet, idx);
    // System.out.println("item=" + item);
    if (!mapIntermediateLoc.containsKey(item)) {
      // need to create a new intermediate location in the lattice
      String newLocName = "ILOC" + (LocationInference.locSeed++);
      String above;
      if (idx == 1) {
        above = startNode.getName();
      } else {
        int prevIdx = idx - 1;
        TripleItem prevItem = new TripleItem(startNode, endNodeSet, prevIdx);
        above = mapIntermediateLoc.get(prevItem);
      }

      Set<String> belowSet = new HashSet<String>();
      for (Iterator iterator = endNodeSet.iterator(); iterator.hasNext();) {
        HNode endNode = (HNode) iterator.next();
        String locName;
        if (locSummary.getMapHNodeNameToLocationName().containsKey(endNode.getName())) {
          locName = locSummary.getLocationName(endNode.getName());
        } else {
          locName = endNode.getName();
        }
        belowSet.add(locName);
      }
      lattice.insertNewLocationBetween(above, belowSet, newLocName);

      mapIntermediateLoc.put(item, newLocName);
    }

    String locName = mapIntermediateLoc.get(item);

    HierarchyGraph graph = infer.getSimpleHierarchyGraph(desc);

    Set<Descriptor> descSet = graph.getDescSetOfNode(curNode);
    for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
      Descriptor d = (Descriptor) iterator.next();
      locSummary.addMapHNodeNameToLocationName(d.getSymbol(), locName);
    }
    locSummary.addMapHNodeNameToLocationName(curNode.getName(), locName);

    if (curNode.isSharedNode()) {
      lattice.addSharedLoc(locName);
    }

    System.out.println("-TripleItem=" + item);
    System.out.println("-curNode=" + curNode.getName() + " locName=" + locName);

    Set<HNode> outSet = graph.getOutgoingNodeSet(curNode);
    for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
      HNode outNode = (HNode) iterator2.next();
      if (!outNode.isSkeleton() && !outNode.isCombinationNode() && !visited.contains(outNode)) {
        visited.add(outNode);
        recurDFSNormalNode(desc, lattice, startNode, endNodeSet, visited, mapIntermediateLoc,
            idx + 1, locSummary, outNode);
      } else if (!outNode.isSkeleton() && outNode.isCombinationNode() && !visited.contains(outNode)) {
        expandCombinationNode(desc, lattice, visited, mapIntermediateLoc, locSummary, outNode);
      }
    }

  }

  private void recurDFS(Descriptor desc, SSJavaLattice<String> lattice,
      HNode combinationNodeInSCGraph, Set<HNode> endNodeSet, Set<HNode> visited,
      Map<TripleItem, String> mapIntermediateLoc, int idx, LocationSummary locSummary, HNode curNode) {

    TripleItem item = new TripleItem(combinationNodeInSCGraph, endNodeSet, idx);

    if (!mapIntermediateLoc.containsKey(item)) {
      // need to create a new intermediate location in the lattice
      String above;
      if (idx == 1) {
        String newLocName = combinationNodeInSCGraph.getName();
        mapIntermediateLoc.put(item, newLocName);
      } else {
        String newLocName = "ILOC" + (LocationInference.locSeed++);
        int prevIdx = idx - 1;
        TripleItem prevItem = new TripleItem(combinationNodeInSCGraph, endNodeSet, prevIdx);
        above = mapIntermediateLoc.get(prevItem);

        Set<String> belowSet = new HashSet<String>();
        for (Iterator iterator = endNodeSet.iterator(); iterator.hasNext();) {
          HNode endNode = (HNode) iterator.next();
          belowSet.add(endNode.getName());
        }
        lattice.insertNewLocationBetween(above, belowSet, newLocName);
        mapIntermediateLoc.put(item, newLocName);

      }

    }

    HierarchyGraph graph = infer.getSimpleHierarchyGraph(desc);
    String locName = mapIntermediateLoc.get(item);
    Set<Descriptor> descSet = graph.getDescSetOfNode(curNode);
    for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
      Descriptor d = (Descriptor) iterator.next();
      locSummary.addMapHNodeNameToLocationName(d.getSymbol(), locName);
    }
    locSummary.addMapHNodeNameToLocationName(curNode.getName(), locName);

    // System.out.println("-TripleItem=" + item);
    // System.out.println("-curNode=" + curNode.getName() + " locName=" + locName);

    Set<HNode> outSet = graph.getOutgoingNodeSet(curNode);
    for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
      HNode outNode = (HNode) iterator2.next();
      if (!outNode.isSkeleton() && !visited.contains(outNode)) {
        if (combinationNodeInSCGraph.equals(getCombinationNodeInSCGraph(desc, outNode))) {
          visited.add(outNode);
          recurDFS(desc, lattice, combinationNodeInSCGraph, endNodeSet, visited,
              mapIntermediateLoc, idx + 1, locSummary, outNode);
        }
      }
    }

  }

  private String generateElementName(BasisSet basisSet, HierarchyGraph inputGraph,
      Map<Set<Integer>, String> mapF2LocName, Set<Integer> F) {

    if (mapF2LocName.containsKey(F)) {
      return mapF2LocName.get(F);
    }

    HNode node = basisSet.getHNode(F);
    if (node != null) {
      mapF2LocName.put(F, node.getName());
      return node.getName();
    } else {
      if (inputGraph.BASISTOPELEMENT.equals(F)) {
        return SSJavaAnalysis.BOTTOM;
      } else {
        String str = "LOC" + (LocationInference.locSeed++);
        mapF2LocName.put(F, str);
        return str;
      }
    }
  }

  private void resetCount(Map<Set<Integer>, Integer> mapFtoCount, Family family) {
    for (Iterator<Set<Integer>> iter = family.FIterator(); iter.hasNext();) {
      Set<Integer> F = iter.next();
      mapFtoCount.put(F, 0);
    }
  }

  private Map<Set<Integer>, Set<Set<Integer>>> coveringGraph(BasisSet basisSet, Family family) {

    Map<Set<Integer>, Integer> mapFtoCount = new HashMap<Set<Integer>, Integer>();
    Map<Set<Integer>, Set<Set<Integer>>> mapImSucc = new HashMap<Set<Integer>, Set<Set<Integer>>>();

    // initialize COUNT(F) to 0 for all elements of the family
    resetCount(mapFtoCount, family);

    for (Iterator<Set<Integer>> iter = family.FIterator(); iter.hasNext();) {
      Set<Integer> F = iter.next();
      Set<HNode> gammaF = family.getGamma(F);

      Set<HNode> curHNodeSet = basisSet.getHNodeSet();
      curHNodeSet.removeAll(gammaF);
      Set<Set<Integer>> Bset = basisSet.getBasisSetByHNodeSet(curHNodeSet);

      for (Iterator iterator = Bset.iterator(); iterator.hasNext();) {
        Set<Integer> B = (Set<Integer>) iterator.next();

        Set<Integer> Fprime = new HashSet<Integer>();
        Fprime.addAll(F);
        Fprime.addAll(B);

        // COUNT(F')++;
        mapFtoCount.put(Fprime, mapFtoCount.get(Fprime) + 1);

        // if |gamma(F')|==COUNT(F') + |gamma(F)|
        int numGammaFprime = family.getGamma(Fprime).size();
        int countFprime = mapFtoCount.get(Fprime);
        int numGammaF = family.getGamma(F).size();
        if (numGammaFprime == (countFprime + numGammaF)) {
          // ImSucc(F)=IMSucc(F) union F'
          addImSucc(mapImSucc, F, Fprime);
        }

      }
      resetCount(mapFtoCount, family);
    }

    // System.out.println("mapImSucc=" + mapImSucc);

    return mapImSucc;
  }

  private Set<Set<Integer>> getImSucc(Map<Set<Integer>, Set<Set<Integer>>> mapImSucc, Set<Integer> F) {
    if (!mapImSucc.containsKey(F)) {
      mapImSucc.put(F, new HashSet<Set<Integer>>());
    }
    return mapImSucc.get(F);
  }

  private void addImSucc(Map<Set<Integer>, Set<Set<Integer>>> mapImSucc, Set<Integer> F,
      Set<Integer> Fprime) {

    if (!mapImSucc.containsKey(F)) {
      mapImSucc.put(F, new HashSet<Set<Integer>>());
    }

    mapImSucc.get(F).add(Fprime);

  }

  private Family generateFamily(BasisSet basisSet) {

    Family family = new Family();

    for (Iterator<Set<Integer>> iterator = basisSet.basisIterator(); iterator.hasNext();) {
      Set<Integer> B = iterator.next();

      Set<Pair<Set<Integer>, Set<HNode>>> tobeadded = new HashSet<Pair<Set<Integer>, Set<HNode>>>();

      for (Iterator<Set<Integer>> iterator2 = family.FIterator(); iterator2.hasNext();) {
        Set<Integer> F = iterator2.next();

        Set<Integer> Fprime = new HashSet<Integer>();
        Fprime.addAll(F);
        Fprime.addAll(B);

        Set<HNode> gammaFPrimeSet = new HashSet<HNode>();
        gammaFPrimeSet.addAll(family.getGamma(F));
        gammaFPrimeSet.add(basisSet.getHNode(B));

        if (!family.containsF(Fprime)) {
          Pair<Set<Integer>, Set<HNode>> pair =
              new Pair<Set<Integer>, Set<HNode>>(Fprime, gammaFPrimeSet);
          tobeadded.add(pair);
        } else {
          family.updateGammaF(Fprime, gammaFPrimeSet);
        }
      }

      for (Iterator<Pair<Set<Integer>, Set<HNode>>> iterator2 = tobeadded.iterator(); iterator2
          .hasNext();) {
        Pair<Set<Integer>, Set<HNode>> pair = iterator2.next();
        family.addFElement(pair.getFirst());
        family.updateGammaF(pair.getFirst(), pair.getSecond());
      }

    }
    return family;
  }

  private void debug_print(HierarchyGraph inputGraph) {
    System.out.println("\nBuild Lattice:" + inputGraph.getName());
    // System.out.println("Node2Index:\n" + inputGraph.getMapHNodeToUniqueIndex());
    // System.out.println("Node2Basis:\n" + inputGraph.getMapHNodeToBasis());
  }

}

class Identifier {
  public HNode node;
  public int idx;

  public Identifier(HNode n, int i) {
    node = n;
    idx = i;
  }

  public int hashCode() {
    return node.hashCode() + idx;
  }

  public boolean equals(Object obj) {

    if (obj instanceof Identifier) {
      Identifier in = (Identifier) obj;
      if (node.equals(in.node) && idx == in.idx) {
        return true;
      }
    }

    return false;
  }

}

class TripleItem {
  public HNode higherNode;
  public Set<HNode> lowerNodeSet;
  public int idx;

  public TripleItem(HNode h, Set<HNode> l, int i) {
    higherNode = h;
    lowerNodeSet = l;
    idx = i;
  }

  public int hashCode() {

    int h = 0;
    if (higherNode != null) {
      h = higherNode.hashCode();
    }

    return h + lowerNodeSet.hashCode() + idx;
  }

  public boolean equals(Object obj) {

    if (obj instanceof TripleItem) {
      TripleItem in = (TripleItem) obj;
      if ((higherNode == null || (higherNode != null && higherNode.equals(in.higherNode)))
          && lowerNodeSet.equals(in.lowerNodeSet) && idx == in.idx) {
        return true;
      }
    }

    return false;
  }

  public String toString() {
    return higherNode + "-" + idx + "->" + lowerNodeSet;
  }
}
