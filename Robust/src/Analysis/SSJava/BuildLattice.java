package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import IR.Descriptor;
import IR.MethodDescriptor;
import IR.NameDescriptor;
import Util.Pair;

public class BuildLattice {

  private LocationInference infer;
  private Map<HNode, TripleItem> mapSharedNodeToTripleItem;
  private Map<HNode, Integer> mapHNodeToHighestIndex;

  public BuildLattice(LocationInference infer) {
    this.infer = infer;
    this.mapSharedNodeToTripleItem = new HashMap<HNode, TripleItem>();
    this.mapHNodeToHighestIndex = new HashMap<HNode, Integer>();
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

    SSJavaLattice<String> lattice = buildLattice(desc, basisSet, inputGraph, locSummary, mapImSucc);
    return lattice;

  }

  private SSJavaLattice<String> buildLattice(Descriptor desc, BasisSet basisSet,
      HierarchyGraph inputGraph, LocationSummary locSummary,
      Map<Set<Integer>, Set<Set<Integer>>> mapImSucc) {

    SSJavaLattice<String> lattice =
        new SSJavaLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM);

    Map<Set<Integer>, String> mapFToLocName = new HashMap<Set<Integer>, String>();

    Set<Set<Integer>> keySet = mapImSucc.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Set<Integer> higher = (Set<Integer>) iterator.next();

      String higherName = generateElementName(basisSet, inputGraph, mapFToLocName, higher);

      HNode higherNode = inputGraph.getHNode(higherName);

      if (higherNode == null) {
        NameDescriptor d = new NameDescriptor(higherName);
        higherNode = inputGraph.getHNode(d);
        higherNode.setSkeleton(true);
      }

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

        if (lowerNode == null && !lowerName.equals(SSJavaAnalysis.BOTTOM)) {
          NameDescriptor d = new NameDescriptor(lowerName);
          lowerNode = inputGraph.getHNode(d);
          lowerNode.setSkeleton(true);
        }

        if (lowerNode != null && !inputGraph.isDirectlyConnectedTo(higherNode, lowerNode)) {
          inputGraph.addEdge(higherNode, lowerNode);
        }

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

    inputGraph.removeRedundantEdges();
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

    mapSharedNodeToTripleItem.clear();

    HierarchyGraph hierarchyGraph = infer.getSimpleHierarchyGraph(desc);
    HierarchyGraph scGraph = infer.getSkeletonCombinationHierarchyGraph(desc);
    LocationSummary locSummary = infer.getLocationSummary(desc);
    SSJavaLattice<String> lattice = skeletonLattice.clone();
    Set<HNode> visited = new HashSet<HNode>();
    Set<HNode> scNodeSet = scGraph.getNodeSet();

    Map<TripleItem, String> mapIntermediateLoc = new HashMap<TripleItem, String>();

    for (Iterator iterator = scNodeSet.iterator(); iterator.hasNext();) {
      HNode scNode = (HNode) iterator.next();
      Set<HNode> outHierarchyNodeSet = hierarchyGraph.getOutgoingNodeSet(scNode);
      for (Iterator iterator2 = outHierarchyNodeSet.iterator(); iterator2.hasNext();) {
        HNode outHierarchyNode = (HNode) iterator2.next();

        if (!visited.contains(outHierarchyNode)) {

          if (!outHierarchyNode.isCombinationNode() && !outHierarchyNode.isSkeleton()) {
            visited.add(outHierarchyNode);
            Set<HNode> outSCNodeSet = scGraph.getOutgoingNodeSet(scNode);

            if (outSCNodeSet.size() > 0) {
              // follows the straight line up to another skeleton/combination node
              outSCNodeSet = removeTransitivelyReachToNode(desc, scNode, outSCNodeSet);
            } else if (outSCNodeSet.size() == 0) {
              // the outNode is (directly/transitively) connected to the bottom node
              // therefore, we just add a dummy bottom HNode to the endCombNodeSet.
              outSCNodeSet.add(LocationInference.BOTTOMHNODE);
            }

            recurDFVisitNormalNode(scNode, outSCNodeSet, outHierarchyNode, 1, desc, lattice,
                visited, locSummary, mapIntermediateLoc);
          } else if (outHierarchyNode.isCombinationNode()) {
            visited.add(outHierarchyNode);
            expandCombinationNode(desc, lattice, visited, mapIntermediateLoc, locSummary,
                outHierarchyNode);
          }

        }

      }

    }

    // add shared locations
    Set<HNode> sharedNodeSet = mapSharedNodeToTripleItem.keySet();
    for (Iterator iterator = sharedNodeSet.iterator(); iterator.hasNext();) {
      HNode sharedNode = (HNode) iterator.next();
      TripleItem item = mapSharedNodeToTripleItem.get(sharedNode);
      String nonSharedLocName = mapIntermediateLoc.get(item);
      // System.out.println("sharedNode=" + sharedNode + "    locName=" + nonSharedLocName);

      String newLocName;
      if (locSummary.getHNodeNameSetByLatticeLoationName(nonSharedLocName) != null
          && !lattice.isSharedLoc(nonSharedLocName)) {
        // need to generate a new shared location in the lattice, which is one level lower than the
        // 'locName' location
        newLocName = "ILOC" + (LocationInference.locSeed++);

        // Set<String> aboveElementSet = getAboveElementSet(lattice, locName);
        Set<String> belowElementSet = new HashSet<String>();
        belowElementSet.addAll(lattice.get(nonSharedLocName));

        // System.out.println("nonSharedLocName=" + nonSharedLocName + "   belowElementSet="
        // + belowElementSet + "  newLocName=" + newLocName);

        lattice.insertNewLocationBetween(nonSharedLocName, belowElementSet, newLocName);
      } else {
        newLocName = nonSharedLocName;
      }

      lattice.addSharedLoc(newLocName);
      HierarchyGraph graph = infer.getSimpleHierarchyGraph(desc);
      Set<Descriptor> descSet = graph.getDescSetOfNode(sharedNode);
      for (Iterator iterator2 = descSet.iterator(); iterator2.hasNext();) {
        Descriptor d = (Descriptor) iterator2.next();
        locSummary.addMapHNodeNameToLocationName(d.getSymbol(), newLocName);
      }
      locSummary.addMapHNodeNameToLocationName(sharedNode.getName(), newLocName);

    }

    return lattice;
  }

  private void recurDFVisitNormalNode(HNode scStartNode, Set<HNode> scEndNodeSet,
      HNode curHierarchyNode, int idx, Descriptor desc, SSJavaLattice<String> lattice,
      Set<HNode> visited, LocationSummary locSummary, Map<TripleItem, String> mapIntermediateLoc) {

    TripleItem item = new TripleItem(scStartNode, scEndNodeSet, idx);
    // System.out.println("item=" + item);
    if (!mapIntermediateLoc.containsKey(item)) {
      // need to create a new intermediate location in the lattice
      String newLocName = "ILOC" + (LocationInference.locSeed++);
      String above;
      if (idx == 1) {
        above = scStartNode.getName();
      } else {
        int prevIdx = idx - 1;
        TripleItem prevItem = new TripleItem(scStartNode, scEndNodeSet, prevIdx);
        above = mapIntermediateLoc.get(prevItem);
      }

      Set<String> belowSet = new HashSet<String>();
      for (Iterator iterator = scEndNodeSet.iterator(); iterator.hasNext();) {
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

    String curLocName = mapIntermediateLoc.get(item);
    HierarchyGraph hierarchyGraph = infer.getSimpleHierarchyGraph(desc);

    if (curHierarchyNode.isSharedNode()) {
      // if the current node is shared location, add a shared location to the lattice later
      mapSharedNodeToTripleItem.put(curHierarchyNode, item);
    } else {
      Set<Descriptor> descSet = hierarchyGraph.getDescSetOfNode(curHierarchyNode);
      for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
        Descriptor d = (Descriptor) iterator.next();
        locSummary.addMapHNodeNameToLocationName(d.getSymbol(), curLocName);
      }
      locSummary.addMapHNodeNameToLocationName(curHierarchyNode.getName(), curLocName);
    }

    System.out.println("-TripleItem normal=" + item);
    System.out.println("-curNode=" + curHierarchyNode.getName() + " S="
        + curHierarchyNode.isSharedNode() + " locName=" + curLocName + "  isC="
        + curHierarchyNode.isCombinationNode());

    Set<HNode> outSet = hierarchyGraph.getOutgoingNodeSet(curHierarchyNode);
    for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
      HNode outHierarchyNodeFromCurNode = (HNode) iterator2.next();

      // Set<HNode> incomingHNodeSetToOutNode = simpleHierarchyGraph.getIncomingNodeSet(outNode);
      System.out.println("outHierarchyNodeFromCurNode=" + outHierarchyNodeFromCurNode);
      // System.out.println("---incomingHNodeSetToOutNode=" + incomingHNodeSetToOutNode);

      if (outHierarchyNodeFromCurNode.isSkeleton()
          || outHierarchyNodeFromCurNode.isCombinationNode()) {
        String lowerLocName = locSummary.getLocationName(outHierarchyNodeFromCurNode.getName());
        lattice.addRelationHigherToLower(curLocName, lowerLocName);
      } else {
        if (visited.containsAll(hierarchyGraph.getIncomingNodeSet(outHierarchyNodeFromCurNode))) {
          visited.add(outHierarchyNodeFromCurNode);
          int newidx = getCurrentHighestIndex(outHierarchyNodeFromCurNode, idx + 1);
          recurDFVisitNormalNode(scStartNode, scEndNodeSet, outHierarchyNodeFromCurNode, newidx,
              desc, lattice, visited, locSummary, mapIntermediateLoc);
        } else {
          System.out.println("NOT RECUR");
          updateHighestIndex(outHierarchyNodeFromCurNode, idx + 1);
        }
      }

      // if (!outNode.isSkeleton() && !outNode.isCombinationNode() && !visited.contains(outNode)) {
      // if (visited.containsAll(simpleHierarchyGraph.getIncomingNodeSet(outNode))) {
      // visited.add(outNode);
      // int newidx = getCurrentHighestIndex(outNode, idx + 1);
      // recurDFSNormalNode(desc, lattice, startNode, endNodeSet, visited, mapIntermediateLoc,
      // newidx, locSummary, outNode);
      // // recurDFSNormalNode(desc, lattice, startNode, endNodeSet, visited, mapIntermediateLoc,
      // // idx + 1, locSummary, outNode);
      // } else {
      // updateHighestIndex(outNode, idx + 1);
      // System.out.println("NOT RECUR");
      // }
      // } else if (!outNode.isSkeleton() && outNode.isCombinationNode() &&
      // !visited.contains(outNode)) {
      // if (needToExpandCombinationNode(desc, outNode)) {
      // System.out.println("NEED TO");
      // expandCombinationNode(desc, lattice, visited, mapIntermediateLoc, locSummary, outNode);
      // } else {
      // System.out.println("NOT NEED TO");
      // }
      // }

    }

  }

  private void recurDFVisitCombinationNode(HNode scCombNode, Set<HNode> scEndNodeSet,
      HNode curHierarchyCombNode, int idx, Descriptor desc, SSJavaLattice<String> lattice,
      Set<HNode> visited, LocationSummary locSummary, Map<TripleItem, String> mapIntermediateLoc) {

    // Descriptor desc, SSJavaLattice<String> lattice,
    // HNode combinationNodeInSCGraph, Set<HNode> endNodeSet, Set<HNode> visited,
    // Map<TripleItem, String> mapIntermediateLoc, int idx, LocationSummary locSummary, HNode
    // curNode) {

    TripleItem item = new TripleItem(scCombNode, scEndNodeSet, idx);

    if (!mapIntermediateLoc.containsKey(item)) {
      // need to create a new intermediate location in the lattice
      String above;
      if (idx == 1) {
        String newLocName = scCombNode.getName();
        mapIntermediateLoc.put(item, newLocName);
      } else {
        String newLocName = "ILOC" + (LocationInference.locSeed++);
        int prevIdx = idx - 1;
        TripleItem prevItem = new TripleItem(scCombNode, scEndNodeSet, prevIdx);
        above = mapIntermediateLoc.get(prevItem);

        Set<String> belowSet = new HashSet<String>();
        for (Iterator iterator = scEndNodeSet.iterator(); iterator.hasNext();) {
          HNode endNode = (HNode) iterator.next();
          belowSet.add(endNode.getName());
        }
        lattice.insertNewLocationBetween(above, belowSet, newLocName);
        mapIntermediateLoc.put(item, newLocName);
      }

    }

    HierarchyGraph hierarchyNode = infer.getSimpleHierarchyGraph(desc);
    String locName = mapIntermediateLoc.get(item);
    if (curHierarchyCombNode.isSharedNode()) {
      // if the current node is shared location, add a shared location to the lattice later
      mapSharedNodeToTripleItem.put(curHierarchyCombNode, item);
    } else {
      Set<Descriptor> descSet = hierarchyNode.getDescSetOfNode(curHierarchyCombNode);
      for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
        Descriptor d = (Descriptor) iterator.next();
        locSummary.addMapHNodeNameToLocationName(d.getSymbol(), locName);
      }
      locSummary.addMapHNodeNameToLocationName(curHierarchyCombNode.getName(), locName);
    }

    System.out.println("-TripleItem=" + item);
    System.out.println("-curNode=" + curHierarchyCombNode.getName() + " S="
        + curHierarchyCombNode.isSharedNode() + " locName=" + locName);

    Set<HNode> outSet = hierarchyNode.getOutgoingNodeSet(curHierarchyCombNode);
    for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
      HNode outHierarchyNode = (HNode) iterator2.next();

      System.out.println("---recurDFS outNode=" + outHierarchyNode);
      System.out.println("---outNode combinationNodeInSCGraph="
          + getCombinationNodeInSCGraph(desc, outHierarchyNode));

      if (outHierarchyNode.isCombinationNode()) {
        HNode outCombinationNodeInSCGraph = getCombinationNodeInSCGraph(desc, outHierarchyNode);
        if (outCombinationNodeInSCGraph.equals(scCombNode)) {

          Set<HNode> combineSkeletonNodeSet =
              hierarchyNode.getCombineSetByCombinationNode(outHierarchyNode);
          Set<HNode> incomingHNodeSetToOutNode = hierarchyNode.getIncomingNodeSet(outHierarchyNode);
          // extract nodes belong to the same combine node
          Set<HNode> incomingCombinedHNodeSet = new HashSet<HNode>();
          for (Iterator iterator = incomingHNodeSetToOutNode.iterator(); iterator.hasNext();) {
            HNode inNode = (HNode) iterator.next();
            if (combineSkeletonNodeSet.contains(inNode)) {
              incomingCombinedHNodeSet.add(inNode);
            }
          }
          System.out.println("incomingCombinedHNodeSet=" + incomingCombinedHNodeSet);
          if (visited.containsAll(incomingCombinedHNodeSet)) {
            visited.add(outHierarchyNode);
            System.out.println("-------curIdx=" + (idx + 1));
            int newIdx = getCurrentHighestIndex(outHierarchyNode, idx + 1);
            System.out.println("-------newIdx=" + newIdx);
            recurDFVisitCombinationNode(scCombNode, scEndNodeSet, outHierarchyNode, newIdx, desc,
                lattice, visited, locSummary, mapIntermediateLoc);
          } else {
            updateHighestIndex(outHierarchyNode, idx + 1);
            System.out.println("-----NOT RECUR!");
          }

        }
      }

    }

  }

  private void expandCombinationNode(Descriptor desc, SSJavaLattice<String> lattice,
      Set<HNode> visited, Map<TripleItem, String> mapIntermediateLoc, LocationSummary locSummary,
      HNode cnode) {

    // expand the combination node 'outNode'
    // here we need to expand the corresponding combination location in the lattice
    HNode combinationNodeInSCGraph = getCombinationNodeInSCGraph(desc, cnode);
    Set<HNode> endNodeSet =
        infer.getSkeletonCombinationHierarchyGraph(desc).getOutgoingNodeSet(
            combinationNodeInSCGraph);

    System.out.println("expandCombinationNode=" + cnode + "  cnode in scgraph="
        + combinationNodeInSCGraph);
    System.out.println("endnodeset=" + endNodeSet);

    if (combinationNodeInSCGraph == null) {
      return;
    }

    // HierarchyGraph hierarchyGraph = infer.getSimpleHierarchyGraph(desc);
    //
    // Set<HNode> combineSkeletonNodeSet = hierarchyGraph.getCombineSetByCombinationNode(cnode);
    //
    // // System.out.println("combineSkeletonNodeSet=" + combineSkeletonNodeSet);
    //
    // Set<HNode> combinationNodeSet =
    // hierarchyGraph.getCombinationNodeSetByCombineNodeSet(combineSkeletonNodeSet);
    //
    // // System.out.println("combinationNodeSet=" + combinationNodeSet);
    //
    // Set<HNode> endNodeSetFromSimpleGraph =
    // hierarchyGraph.getDirectlyReachableSkeletonCombinationNodeFrom(cnode, combinationNodeSet);
    // // System.out.println("-endNodeSetFromSimpleGraph=" + endNodeSetFromSimpleGraph);
    // Set<HNode> endCombNodeSet = new HashSet<HNode>();
    // for (Iterator iterator3 = endNodeSetFromSimpleGraph.iterator(); iterator3.hasNext();) {
    // HNode endNode = (HNode) iterator3.next();
    // endCombNodeSet.add(getCombinationNodeInSCGraph(desc, endNode));
    // }

    visited.add(cnode);

    // follows the straight line up to another skeleton/combination node
    if (endNodeSet.size() > 0) {
      // System.out.println("---endCombNodeSet=" + endCombNodeSet);
      endNodeSet = removeTransitivelyReachToNode(desc, combinationNodeInSCGraph, endNodeSet);

      recurDFVisitCombinationNode(combinationNodeInSCGraph, endNodeSet, cnode, 1, desc, lattice,
          visited, locSummary, mapIntermediateLoc);

    } else {
      endNodeSet.add(LocationInference.BOTTOMHNODE);
      // System.out.println("---endCombNodeSet is zero");
      // System.out.println("---endNodeSetFromSimpleGraph=" + endNodeSetFromSimpleGraph);
      // System.out.println("---incoming=" + simpleGraph.getIncomingNodeSet(cnode));
      recurDFVisitCombinationNode(combinationNodeInSCGraph, endNodeSet, cnode, 1, desc, lattice,
          visited, locSummary, mapIntermediateLoc);
    }

  }

  public SSJavaLattice<String> insertIntermediateNodesToStraightLine2(Descriptor desc,
      SSJavaLattice<String> skeletonLattice) {

    // perform DFS that starts from each skeleton/combination node and ends by another
    // skeleton/combination node

    mapSharedNodeToTripleItem.clear();

    HierarchyGraph simpleGraph = infer.getSimpleHierarchyGraph(desc);
    HierarchyGraph scGraph = infer.getSkeletonCombinationHierarchyGraph(desc);
    LocationSummary locSummary = infer.getLocationSummary(desc);

    SSJavaLattice<String> lattice = skeletonLattice.clone();

    Set<HNode> visited = new HashSet<HNode>();

    Set<HNode> nodeSet = simpleGraph.getNodeSet();

    Map<TripleItem, String> mapIntermediateLoc = new HashMap<TripleItem, String>();

    // System.out.println("*insert=" + desc);
    // System.out.println("***nodeSet=" + nodeSet);
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
              if (visited.containsAll(simpleGraph.getIncomingNodeSet(outNode))) {
                // if (needToExpandCombinationNode(desc, outNode)) {
                expandCombinationNode3(desc, lattice, visited, mapIntermediateLoc, locSummary,
                    outNode);
                // }
              }
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
                endCombNodeSet.add(LocationInference.BOTTOMHNODE);
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

          Set<HNode> endNodeSetFromSimpleGraph =
              simpleGraph.getDirectlyReachableSkeletonCombinationNodeFrom(node, null);

          Set<HNode> endCombNodeSet = new HashSet<HNode>();
          for (Iterator iterator3 = endNodeSetFromSimpleGraph.iterator(); iterator3.hasNext();) {
            HNode endNode = (HNode) iterator3.next();
            endCombNodeSet.add(getCombinationNodeInSCGraph(desc, endNode));
          }

          System.out.println("endCombNodeSet=" + endCombNodeSet);
          HNode startNode = LocationInference.TOPHNODE;
          visited.add(startNode);
          if (endCombNodeSet.size() > 0) {
            // follows the straight line up to another skeleton/combination node
            // endCombNodeSet = removeTransitivelyReachToNode(desc, node, endCombNodeSet);
            recurDFSNormalNode(desc, lattice, startNode, endCombNodeSet, visited,
                mapIntermediateLoc, 1, locSummary, node);
          }

        }

      }
    }

    // add shared locations
    Set<HNode> sharedNodeSet = mapSharedNodeToTripleItem.keySet();
    for (Iterator iterator = sharedNodeSet.iterator(); iterator.hasNext();) {
      HNode sharedNode = (HNode) iterator.next();
      TripleItem item = mapSharedNodeToTripleItem.get(sharedNode);
      String nonSharedLocName = mapIntermediateLoc.get(item);
      // System.out.println("sharedNode=" + sharedNode + "    locName=" + nonSharedLocName);

      String newLocName;
      if (locSummary.getHNodeNameSetByLatticeLoationName(nonSharedLocName) != null
          && !lattice.isSharedLoc(nonSharedLocName)) {
        // need to generate a new shared location in the lattice, which is one level lower than the
        // 'locName' location
        newLocName = "ILOC" + (LocationInference.locSeed++);

        // Set<String> aboveElementSet = getAboveElementSet(lattice, locName);
        Set<String> belowElementSet = new HashSet<String>();
        belowElementSet.addAll(lattice.get(nonSharedLocName));

        // System.out.println("nonSharedLocName=" + nonSharedLocName + "   belowElementSet="
        // + belowElementSet + "  newLocName=" + newLocName);

        lattice.insertNewLocationBetween(nonSharedLocName, belowElementSet, newLocName);
      } else {
        newLocName = nonSharedLocName;
      }

      lattice.addSharedLoc(newLocName);
      HierarchyGraph graph = infer.getSimpleHierarchyGraph(desc);
      Set<Descriptor> descSet = graph.getDescSetOfNode(sharedNode);
      for (Iterator iterator2 = descSet.iterator(); iterator2.hasNext();) {
        Descriptor d = (Descriptor) iterator2.next();
        locSummary.addMapHNodeNameToLocationName(d.getSymbol(), newLocName);
      }
      locSummary.addMapHNodeNameToLocationName(sharedNode.getName(), newLocName);

    }

    return lattice;

  }

  private Set<String> getAboveElementSet(SSJavaLattice<String> lattice, String loc) {

    Set<String> aboveSet = new HashSet<String>();

    Map<String, Set<String>> latticeMap = lattice.getTable();
    Set<String> keySet = latticeMap.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      String key = (String) iterator.next();
      if (latticeMap.get(key).contains(loc)) {
        aboveSet.add(key);
      }
    }

    return aboveSet;
  }

  private boolean needToExpandCombinationNode(Descriptor desc, HNode cnode) {

    System.out.println("needToExpandCombinationNode?=" + cnode);

    HierarchyGraph simpleGraph = infer.getSimpleHierarchyGraph(desc);
    // HNode combinationNodeInSCGraph = getCombinationNodeInSCGraph(desc, cnode);
    Set<HNode> combineSkeletonNodeSet = simpleGraph.getCombineSetByCombinationNode(cnode);
    Set<HNode> combinationNodeSetInSimpleGraph =
        simpleGraph.getCombinationNodeSetByCombineNodeSet(combineSkeletonNodeSet);
    System.out.println("---combinationNodeSetInSimpleGraph=" + combinationNodeSetInSimpleGraph);
    Set<HNode> inNodeSetToCNode = simpleGraph.getIncomingNodeSet(cnode);
    System.out.println("------inNodeSetToCNode=" + inNodeSetToCNode);
    for (Iterator iterator = combinationNodeSetInSimpleGraph.iterator(); iterator.hasNext();) {
      HNode nodeBelongToTheSameCombinationNode = (HNode) iterator.next();
      if (inNodeSetToCNode.contains(nodeBelongToTheSameCombinationNode)) {
        // the combination node 'cnode' is not the highest location among the same combination node
        return false;
      }
    }

    return true;
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

    // System.out.println("getDirectlyReachableSCNodeFromEndNode start=" + startNode +
    // " endNodeSet="
    // + endNodeSet);
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

    // System.out.println("newStartNodeSet=" + newStartNodeSet);

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
    // System.out.println("getDirectlyReachableNodeFromStartNodeReachToEndNode start=" + startNode
    // + " end=" + endNode);
    Set<HNode> connected = new HashSet<HNode>();
    recurDirectlyReachableNodeFromStartNodeReachToEndNode(scGraph, startNode, endNode, connected);
    if (connected.size() == 0) {
      connected.add(endNode);
    }
    // System.out.println("connected=" + connected);

    return connected.iterator().next();
  }

  private void expandCombinationNode3(Descriptor desc, SSJavaLattice<String> lattice,
      Set<HNode> visited, Map<TripleItem, String> mapIntermediateLoc, LocationSummary locSummary,
      HNode cnode) {

    // expand the combination node 'outNode'
    // here we need to expand the corresponding combination location in the lattice
    HNode combinationNodeInSCGraph = getCombinationNodeInSCGraph(desc, cnode);

    System.out.println("expandCombinationNode=" + cnode + "  cnode in scgraph="
        + combinationNodeInSCGraph);

    if (combinationNodeInSCGraph == null) {
      return;
    }

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
      // System.out.println("---endCombNodeSet=" + endCombNodeSet);
      endCombNodeSet =
          removeTransitivelyReachToNode(desc, combinationNodeInSCGraph, endCombNodeSet);

      recurDFS(desc, lattice, combinationNodeInSCGraph, endCombNodeSet, visited,
          mapIntermediateLoc, 1, locSummary, cnode);
    } else {
      endCombNodeSet.add(LocationInference.BOTTOMHNODE);
      // System.out.println("---endCombNodeSet is zero");
      // System.out.println("---endNodeSetFromSimpleGraph=" + endNodeSetFromSimpleGraph);
      // System.out.println("---incoming=" + simpleGraph.getIncomingNodeSet(cnode));
      recurDFS(desc, lattice, combinationNodeInSCGraph, endCombNodeSet, visited,
          mapIntermediateLoc, 1, locSummary, cnode);

    }

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
    HierarchyGraph simpleHierarchyGraph = infer.getSimpleHierarchyGraph(desc);

    if (curNode.isSharedNode()) {
      // if the current node is shared location, add a shared location to the lattice later
      mapSharedNodeToTripleItem.put(curNode, item);
    } else {
      Set<Descriptor> descSet = simpleHierarchyGraph.getDescSetOfNode(curNode);
      for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
        Descriptor d = (Descriptor) iterator.next();
        locSummary.addMapHNodeNameToLocationName(d.getSymbol(), locName);
      }
      locSummary.addMapHNodeNameToLocationName(curNode.getName(), locName);
    }

    System.out.println("-TripleItem normal=" + item);
    System.out.println("-curNode=" + curNode.getName() + " S=" + curNode.isSharedNode()
        + " locName=" + locName + "  isC=" + curNode.isCombinationNode());

    Set<HNode> outSet = simpleHierarchyGraph.getOutgoingNodeSet(curNode);
    for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
      HNode outNode = (HNode) iterator2.next();

      Set<HNode> incomingHNodeSetToOutNode = simpleHierarchyGraph.getIncomingNodeSet(outNode);
      System.out.println("outNode=" + outNode);
      System.out.println("---incomingHNodeSetToOutNode=" + incomingHNodeSetToOutNode);

      if (!outNode.isSkeleton() && !outNode.isCombinationNode() && !visited.contains(outNode)) {
        if (visited.containsAll(simpleHierarchyGraph.getIncomingNodeSet(outNode))) {
          visited.add(outNode);
          int newidx = getCurrentHighestIndex(outNode, idx + 1);
          recurDFSNormalNode(desc, lattice, startNode, endNodeSet, visited, mapIntermediateLoc,
              newidx, locSummary, outNode);
          // recurDFSNormalNode(desc, lattice, startNode, endNodeSet, visited, mapIntermediateLoc,
          // idx + 1, locSummary, outNode);
        } else {
          updateHighestIndex(outNode, idx + 1);
          System.out.println("NOT RECUR");
        }
      } else if (!outNode.isSkeleton() && outNode.isCombinationNode() && !visited.contains(outNode)) {
        if (needToExpandCombinationNode(desc, outNode)) {
          System.out.println("NEED TO");
          expandCombinationNode3(desc, lattice, visited, mapIntermediateLoc, locSummary, outNode);
        } else {
          System.out.println("NOT NEED TO");
        }
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

    // TODO
    // Do we need to skip the combination node and assign a shared location to the next node?
    // if (idx == 1 && curNode.isSharedNode()) {
    // System.out.println("THE FIRST COMBINATION NODE EXPANSION IS SHARED!");
    // recurDFS(desc, lattice, combinationNodeInSCGraph, endNodeSet, visited, mapIntermediateLoc,
    // idx + 1, locSummary, curNode);
    // return;
    // }

    HierarchyGraph simpleHierarchyGraph = infer.getSimpleHierarchyGraph(desc);
    String locName = mapIntermediateLoc.get(item);
    if (curNode.isSharedNode()) {
      // if the current node is shared location, add a shared location to the lattice later
      mapSharedNodeToTripleItem.put(curNode, item);
    } else {
      Set<Descriptor> descSet = simpleHierarchyGraph.getDescSetOfNode(curNode);
      for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
        Descriptor d = (Descriptor) iterator.next();
        locSummary.addMapHNodeNameToLocationName(d.getSymbol(), locName);
      }
      locSummary.addMapHNodeNameToLocationName(curNode.getName(), locName);
    }

    System.out.println("-TripleItem=" + item);
    System.out.println("-curNode=" + curNode.getName() + " S=" + curNode.isSharedNode()
        + " locName=" + locName);

    Set<HNode> outSet = simpleHierarchyGraph.getOutgoingNodeSet(curNode);
    for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
      HNode outNode = (HNode) iterator2.next();
      System.out.println("---recurDFS outNode=" + outNode);
      System.out.println("---cur combinationNodeInSCGraph=" + combinationNodeInSCGraph);
      System.out.println("---outNode combinationNodeInSCGraph="
          + getCombinationNodeInSCGraph(desc, outNode));

      if (!outNode.isSkeleton() && !visited.contains(outNode)) {
        if (outNode.isCombinationNode()) {

          Set<HNode> combineSkeletonNodeSet =
              simpleHierarchyGraph.getCombineSetByCombinationNode(outNode);
          Set<HNode> incomingHNodeSetToOutNode = simpleHierarchyGraph.getIncomingNodeSet(outNode);
          // extract nodes belong to the same combine node
          Set<HNode> incomingCombinedHNodeSet = new HashSet<HNode>();
          for (Iterator iterator = incomingHNodeSetToOutNode.iterator(); iterator.hasNext();) {
            HNode inNode = (HNode) iterator.next();
            if (combineSkeletonNodeSet.contains(inNode)) {
              incomingCombinedHNodeSet.add(inNode);
            }
          }
          System.out.println("-----incomingCombinedHNodeSet=" + incomingCombinedHNodeSet);

          // check whether the next combination node is different from the current node
          if (combinationNodeInSCGraph.equals(getCombinationNodeInSCGraph(desc, outNode))) {
            if (visited.containsAll(incomingCombinedHNodeSet)) {
              visited.add(outNode);
              System.out.println("-------curIdx=" + (idx + 1));
              int newIdx = getCurrentHighestIndex(outNode, idx + 1);
              System.out.println("-------newIdx=" + newIdx);
              recurDFS(desc, lattice, combinationNodeInSCGraph, endNodeSet, visited,
                  mapIntermediateLoc, newIdx, locSummary, outNode);
              // recurDFS(desc, lattice, combinationNodeInSCGraph, endNodeSet, visited,
              // mapIntermediateLoc, idx + 1, locSummary, outNode);
            } else {
              updateHighestIndex(outNode, idx + 1);
              System.out.println("-----NOT RECUR!");
            }
          } else {
            if (needToExpandCombinationNode(desc, outNode)) {
              System.out.println("NEED TO");
              expandCombinationNode3(desc, lattice, visited, mapIntermediateLoc, locSummary,
                  outNode);
            } else {
              System.out.println("NOT NEED TO");
            }

          }
        }
      }
      // }

    }

  }

  private int getCurrentHighestIndex(HNode node, int curIdx) {
    int recordedIdx = getCurrentHighestIndex(node);
    if (recordedIdx > curIdx) {
      return recordedIdx;
    } else {
      return curIdx;
    }
  }

  private int getCurrentHighestIndex(HNode node) {
    if (!mapHNodeToHighestIndex.containsKey(node)) {
      mapHNodeToHighestIndex.put(node, new Integer(-1));
    }
    return mapHNodeToHighestIndex.get(node).intValue();
  }

  private void updateHighestIndex(HNode node, int idx) {
    if (idx > getCurrentHighestIndex(node)) {
      mapHNodeToHighestIndex.put(node, new Integer(idx));
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
  public boolean isShared;

  public TripleItem(HNode h, Set<HNode> l, int i) {
    higherNode = h;
    lowerNodeSet = l;
    idx = i;
    isShared = false;
  }

  public void setShared(boolean in) {
    this.isShared = in;
  }

  public boolean isShared() {
    return isShared;
  }

  public int hashCode() {

    int h = 0;
    if (higherNode != null) {
      h = higherNode.hashCode();
    }

    if (isShared) {
      h++;
    }

    return h + lowerNodeSet.hashCode() + idx;
  }

  public boolean equals(Object obj) {

    if (obj instanceof TripleItem) {
      TripleItem in = (TripleItem) obj;
      if ((higherNode == null || (higherNode != null && higherNode.equals(in.higherNode)))
          && lowerNodeSet.equals(in.lowerNodeSet) && idx == in.idx && isShared == in.isShared()) {
        return true;
      }
    }

    return false;
  }

  public String toString() {
    String rtr = higherNode + "-" + idx + "->" + lowerNodeSet;
    if (isShared) {
      rtr += " S";
    }
    return rtr;
  }
}
