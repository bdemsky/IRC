package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import IR.ClassDescriptor;
import IR.Descriptor;
import IR.MethodDescriptor;
import IR.NameDescriptor;
import Util.Pair;

public class BuildLattice {

  private LocationInference infer;
  private Map<HNode, TripleItem> mapSharedNodeToTripleItem;
  private Map<HNode, Integer> mapHNodeToHighestIndex;

  private Map<Descriptor, Map<TripleItem, String>> mapDescToIntermediateLocMap;

  private Map<Pair<HNode, HNode>, Integer> mapItemToHighestIndex;

  private Map<SSJavaLattice<String>, Set<String>> mapLatticeToLocalLocSet;

  public BuildLattice(LocationInference infer) {
    this.infer = infer;
    this.mapSharedNodeToTripleItem = new HashMap<HNode, TripleItem>();
    this.mapHNodeToHighestIndex = new HashMap<HNode, Integer>();
    this.mapItemToHighestIndex = new HashMap<Pair<HNode, HNode>, Integer>();
    this.mapDescToIntermediateLocMap = new HashMap<Descriptor, Map<TripleItem, String>>();
    this.mapLatticeToLocalLocSet = new HashMap<SSJavaLattice<String>, Set<String>>();
  }

  public SSJavaLattice<String> buildLattice(Descriptor desc) {

    HierarchyGraph inputGraph = infer.getSkeletonCombinationHierarchyGraph(desc);
    LocationSummary locSummary = infer.getLocationSummary(desc);

    HierarchyGraph naiveGraph = infer.getSimpleHierarchyGraph(desc);

    // I don't think we need to keep the below if statement anymore
    // because hierarchy graph does not have any composite location
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

    // /////////////////////////////////////////////////////////////////////////////////////
    // lattice generation for the native approach

    if (infer.state.SSJAVA_INFER_NAIVE_WRITEDOTS) {
      BasisSet naiveBasisSet = naiveGraph.computeBasisSet(nodeSetWithCompositeLocation);

      Family naiveFamily = generateFamily(naiveBasisSet);
      Map<Set<Integer>, Set<Set<Integer>>> naive_mapImSucc =
          coveringGraph(naiveBasisSet, naiveFamily);

      SSJavaLattice<String> naive_lattice =
          buildLattice(desc, naiveBasisSet, naiveGraph, null, naive_mapImSucc);
      LocationInference.numLocationsNaive += naive_lattice.getKeySet().size();
      infer.addNaiveLattice(desc, naive_lattice);
    }

    // /////////////////////////////////////////////////////////////////////////////////////

    // lattice generation for the proposed approach
    BasisSet basisSet = inputGraph.computeBasisSet(nodeSetWithCompositeLocation);
    // debug_print(inputGraph);

    Family family = generateFamily(basisSet);
    Map<Set<Integer>, Set<Set<Integer>>> mapImSucc = coveringGraph(basisSet, family);

    SSJavaLattice<String> lattice = buildLattice(desc, basisSet, inputGraph, locSummary, mapImSucc);
    return lattice;

  }

  public void setIntermediateLocMap(Descriptor desc, Map<TripleItem, String> map) {
    mapDescToIntermediateLocMap.put(desc, map);
  }

  public Map<TripleItem, String> getIntermediateLocMap(Descriptor desc) {
    if (!mapDescToIntermediateLocMap.containsKey(desc)) {
      mapDescToIntermediateLocMap.put(desc, new HashMap<TripleItem, String>());
    }
    return mapDescToIntermediateLocMap.get(desc);
  }

  private Descriptor getParent(Descriptor desc) {
    if (desc instanceof MethodDescriptor) {
      MethodDescriptor md = (MethodDescriptor) desc;
      ClassDescriptor cd = md.getClassDesc();
      return infer.getParentMethodDesc(cd, md);
    } else {
      return ((ClassDescriptor) desc).getSuperDesc();
    }
  }

  private SSJavaLattice<String> buildLattice(Descriptor desc, BasisSet basisSet,
      HierarchyGraph inputGraph, LocationSummary locSummary,
      Map<Set<Integer>, Set<Set<Integer>>> mapImSucc) {

    System.out.println("\nBuild Lattice:" + inputGraph.getName());

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

      if (locSummary != null) {
        for (Iterator iterator2 = descSet.iterator(); iterator2.hasNext();) {
          Descriptor d = (Descriptor) iterator2.next();
          locSummary.addMapHNodeNameToLocationName(d.getSymbol(), higherName);
        }
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
        if (locSummary != null) {
          for (Iterator iterator3 = lowerDescSet.iterator(); iterator3.hasNext();) {
            Descriptor d = (Descriptor) iterator3.next();
            locSummary.addMapHNodeNameToLocationName(d.getSymbol(), lowerName);
          }
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

    SSJavaLattice<String> lattice = skeletonLattice.clone();
    LocationSummary locSummary = infer.getLocationSummary(desc);

    Descriptor parentDesc = getParent(desc);
    if (parentDesc != null) {
      SSJavaLattice<String> parentLattice = infer.getLattice(parentDesc);

      Map<String, Set<String>> parentMap = parentLattice.getTable();
      Set<String> parentKeySet = parentMap.keySet();
      for (Iterator iterator = parentKeySet.iterator(); iterator.hasNext();) {
        String parentKey = (String) iterator.next();
        Set<String> parentValueSet = parentMap.get(parentKey);
        for (Iterator iterator2 = parentValueSet.iterator(); iterator2.hasNext();) {
          String value = (String) iterator2.next();
          lattice.put(parentKey, value);
        }
      }

      Set<String> parentSharedLocSet = parentLattice.getSharedLocSet();
      for (Iterator iterator = parentSharedLocSet.iterator(); iterator.hasNext();) {
        String parentSharedLoc = (String) iterator.next();
        lattice.addSharedLoc(parentSharedLoc);
      }
    }

    HierarchyGraph hierarchyGraph = infer.getSimpleHierarchyGraph(desc);
    HierarchyGraph scGraph = infer.getSkeletonCombinationHierarchyGraph(desc);

    Set<HNode> hierarchyGraphNodeSet = hierarchyGraph.getNodeSet();
    for (Iterator iterator = hierarchyGraphNodeSet.iterator(); iterator.hasNext();) {
      HNode hNode = (HNode) iterator.next();
      if (!hNode.isSkeleton()) {
        // here we need to insert an intermediate node for the hNode
        System.out.println("\n#local node=" + hNode);

        // 1) find the lowest node m in the lattice that is above hnode in the lattice
        // 2) count the number of non-shared nodes d between the hnode and the node m
        // int numNonSharedNodes;
        int dist;

        HNode SCNode;
        Set<HNode> combineSkeletonNodeSet = null;
        if (hNode.isDirectCombinationNode()) {
          // this node itself is the lowest node m. it is the first node of the chain
          Set<HNode> combineSet = hierarchyGraph.getCombineSetByCombinationNode(hNode);

          System.out.println("     # direct combine node::combineSkeletonNodeSet=" + combineSet);

          SCNode = scGraph.getCombinationNode(combineSet);
          // numNonSharedNodes = -1;
          dist = 0;
        } else {

          Set<HNode> aboveSet = new HashSet<HNode>();
          if (hNode.isCombinationNode()) {
            // the current node is a combination node
            combineSkeletonNodeSet = hierarchyGraph.getCombineSetByCombinationNode(hNode);
            System.out.println("     combineSkeletonNodeSet=" + combineSkeletonNodeSet
                + " combinationNode=" + scGraph.getCombinationNode(combineSkeletonNodeSet));

            scGraph.getCombinationNode(combineSkeletonNodeSet);

            System.out.println("        firstnodeOfSimpleGraph="
                + hierarchyGraph.getFirstNodeOfCombinationNodeChainSet(combineSkeletonNodeSet));
            aboveSet.addAll(hierarchyGraph
                .getFirstNodeOfCombinationNodeChainSet(combineSkeletonNodeSet));

            SCNode = scGraph.getCombinationNode(combineSkeletonNodeSet);

          } else {
            // the current node is not a combination node
            // there is only one parent node which should be skeleton node.

            System.out.println("   hierarchyGraph.getSkeleteNodeSetReachTo(" + hNode + ")="
                + hierarchyGraph.getSkeleteNodeSetReachTo(hNode));
            aboveSet.addAll(hierarchyGraph.getSkeleteNodeSetReachTo(hNode));
            System.out.println("   aboveset of " + hNode + "=" + aboveSet);
            // assert aboveSet.size() == 1;
            SCNode = aboveSet.iterator().next();
          }

          // update above set w.r.t the hierarchy graph with SC nodes
          // because the skeleton nodes in the original hierarchy graph may be merged to a new node
          Set<HNode> endSet = new HashSet<HNode>();
          for (Iterator iterator2 = aboveSet.iterator(); iterator2.hasNext();) {
            HNode aboveNode = (HNode) iterator2.next();
            endSet.add(hierarchyGraph.getCurrentHNode(aboveNode));
          }

          dist = hierarchyGraph.computeDistance(hNode, endSet, combineSkeletonNodeSet);
          System.out.println("##### " + hNode + "::dist=" + dist);

          // numNonSharedNodes = hierarchyGraph.countNonSharedNode(hNode, endSet);

          System.out.println("   COUNT-RESULT::node=" + hNode + " above=" + endSet + " distance="
              + dist + "   SCNode=" + SCNode);
        }

        // 3) convert the node m into a chain of nodes with the last node in the chain having m’s
        // outgoing edges.
        Set<HNode> outgoingSCNodeSet = scGraph.getOutgoingNodeSet(SCNode);
        System.out.println("   outgoing scnode set from " + SCNode + "=" + outgoingSCNodeSet);

        // convert hnodes to location names
        String startLocName = locSummary.getLocationName(SCNode.getName());
        Set<String> outgoingLocNameSet = new HashSet<String>();
        for (Iterator iterator2 = outgoingSCNodeSet.iterator(); iterator2.hasNext();) {
          HNode outSCNode = (HNode) iterator2.next();
          String locName = locSummary.getLocationName(outSCNode.getName());
          if (!locName.equals(outSCNode.getName())) {
            System.out.println("                         outSCNode=" + outSCNode + " -> locName="
                + locName);
          }
          outgoingLocNameSet.add(locName);
        }

        if (outgoingLocNameSet.isEmpty()) {
          outgoingLocNameSet.add(lattice.getBottomItem());
        }

        // 4) If hnode is not a shared location, check if there already exists a local variable
        // node that has distance d below m along this chain. If such a node
        // does not exist, insert it.
        String locName =
            getNewLocation(lattice, startLocName, outgoingLocNameSet, dist, hNode.isSharedNode());
        System.out.println("       ###hNode=" + hNode + "---->locName=" + locName);
        locSummary.addMapHNodeNameToLocationName(hNode.getName(), locName);

      }
    }

    return lattice;
  }

  private void addLocalLocation(SSJavaLattice<String> lattice, String localLoc) {
    if (!mapLatticeToLocalLocSet.containsKey(lattice)) {
      mapLatticeToLocalLocSet.put(lattice, new HashSet<String>());
    }
    mapLatticeToLocalLocSet.get(lattice).add(localLoc);
  }

  private boolean isLocalLocation(SSJavaLattice<String> lattice, String localLoc) {
    if (mapLatticeToLocalLocSet.containsKey(lattice)) {
      return mapLatticeToLocalLocSet.get(lattice).contains(localLoc);
    }
    return false;
  }

  public String getNewLocation(SSJavaLattice<String> lattice, String start, Set<String> endSet,
      int dist, boolean isShared) {
    System.out.println("       #GETNEWLOCATION:: start=" + start + "  endSet=" + endSet + " dist="
        + dist + " isShared=" + isShared);
    return recur_getNewLocation(lattice, start, start, endSet, dist, isShared);
  }

  private String recur_getNewLocation(SSJavaLattice<String> lattice, String start, String cur,
      Set<String> endSet, int dist, boolean isShared) {

    System.out.println("          recur_getNewLocation cur=" + cur + " dist=" + dist);

    if (dist == 0) {
      if (isShared) {
        // first check if there already exists a non-shared node at distance d
        if (!isLocalLocation(lattice, cur) && !start.equals(cur)) {
          // if not, need to insert a new SHARED local location at this point
          System.out.println("if not, need to insert a new SHARED local location at this point");
          String newLocName = "ILOC" + (LocationInference.locSeed++);
          Set<String> lowerSet = new HashSet<String>();
          lowerSet.addAll(lattice.get(cur));
          lattice.insertNewLocationBetween(cur, lowerSet, newLocName);
          lattice.addSharedLoc(newLocName);
          addLocalLocation(lattice, newLocName);
          return newLocName;
        }
        // if there exists a non-shared node at distance d
        // then try to add a new SHARED loc at distance d+1

        Set<String> connectedSet = lattice.get(cur);
        if (connectedSet == null) {
          connectedSet = new HashSet<String>();
        }
        System.out.println("cur=" + cur + "  connectedSet=" + connectedSet);

        // check if there already exists a shared node that has distance d + 1 on the chain
        boolean needToInsertSharedNode = false;
        if (connectedSet.equals(endSet)) {
          needToInsertSharedNode = true;
        } else {
          // in this case, the current node is in the middle of the chain
          assert connectedSet.size() == 1;
          String below = connectedSet.iterator().next();
          if (lattice.isSharedLoc(below)) {
            return below;
          } else {
            needToInsertSharedNode = true;
          }
        }

        if (needToInsertSharedNode) {
          // no shared local location at d+1, need to insert it!
          String newSharedLocName = "ILOC" + (LocationInference.locSeed++);
          Set<String> lowerSet = new HashSet<String>();
          lowerSet.addAll(connectedSet);
          lattice.insertNewLocationBetween(cur, lowerSet, newSharedLocName);
          lattice.addSharedLoc(newSharedLocName);
          addLocalLocation(lattice, newSharedLocName);
          System.out.println("          INSERT NEW SHARED LOC=" + newSharedLocName);
          cur = newSharedLocName;
        }

        return cur;

      } else {
        // if the node is not a shared one,
        // check if the cur node is a shared node
        if (lattice.isSharedLoc(cur)) {
          // here, we need to add a new local NONSHARED node above cur

          String newLocName = "ILOC" + (LocationInference.locSeed++);
          lattice.insertNewLocationAtOneLevelHigher(cur, newLocName);
          addLocalLocation(lattice, newLocName);
          System.out.println("          INSERT NEW LOC=" + newLocName + " ABOVE=" + cur);
          return newLocName;
        } else {
          // if cur is not shared, return it!
          return cur;
        }
      }
    }

    Set<String> connectedSet = lattice.get(cur);
    if (connectedSet == null) {
      connectedSet = new HashSet<String>();
    }

    System.out.println("cur=" + cur + " connected set=" + connectedSet);
    if (cur.equals(lattice.getTopItem()) || connectedSet.equals(endSet)) {
      // if not, need to insert a new local location at this point
      System.out.println("NEED TO INSERT A NEW LOCAL LOC FOR NEXT connectedSet=" + connectedSet);
      String newLocName = "ILOC" + (LocationInference.locSeed++);
      Set<String> lowerSet = new HashSet<String>();
      lowerSet.addAll(connectedSet);
      lattice.insertNewLocationBetween(cur, lowerSet, newLocName);
      addLocalLocation(lattice, newLocName);
      cur = newLocName;
    } else {
      // in this case, the current node is in the middle of the chain
      assert connectedSet.size() == 1;
      cur = connectedSet.iterator().next();
    }

    if (!lattice.isSharedLoc(cur)) {
      dist--;
    }
    return recur_getNewLocation(lattice, start, cur, endSet, dist, isShared);

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

  private void expandCombinationNode(Descriptor desc, SSJavaLattice<String> lattice,
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
    HierarchyGraph scGraph = infer.getSkeletonCombinationHierarchyGraph(desc);

    Set<HNode> combineSkeletonNodeSet = simpleGraph.getCombineSetByCombinationNode(cnode);

    // System.out.println("combineSkeletonNodeSet=" + combineSkeletonNodeSet);

    Set<HNode> combinationNodeSet =
        simpleGraph.getCombinationNodeSetByCombineNodeSet(combineSkeletonNodeSet);

    // System.out.println("combinationNodeSet=" + combinationNodeSet);

    // TODO
    // Set<HNode> endNodeSetFromSimpleGraph =
    // simpleGraph.getDirectlyReachableSkeletonCombinationNodeFrom(cnode, combinationNodeSet);
    // System.out.println("-endNodeSetFromSimpleGraph=" + endNodeSetFromSimpleGraph);
    // Set<HNode> endCombNodeSet = new HashSet<HNode>();
    // for (Iterator iterator3 = endNodeSetFromSimpleGraph.iterator(); iterator3.hasNext();) {
    // HNode endNode = (HNode) iterator3.next();
    // endCombNodeSet.add(getCombinationNodeInSCGraph(desc, endNode));
    // }

    Set<HNode> endCombNodeSet = scGraph.getOutgoingNodeSet(combinationNodeInSCGraph);
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
      System.out.println("###SHARED ITEM=" + item);
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
        Pair<HNode, HNode> pair = new Pair(startNode, outNode);
        if (visited.containsAll(simpleHierarchyGraph.getIncomingNodeSet(outNode))) {
          visited.add(outNode);
          int newidx = getCurrentHighestIndex(pair, idx + 1);
          // int newidx = getCurrentHighestIndex(outNode, idx + 1);
          recurDFSNormalNode(desc, lattice, startNode, endNodeSet, visited, mapIntermediateLoc,
              newidx, locSummary, outNode);
          // recurDFSNormalNode(desc, lattice, startNode, endNodeSet, visited, mapIntermediateLoc,
          // idx + 1, locSummary, outNode);
        } else {
          updateHighestIndex(pair, idx + 1);
          // updateHighestIndex(outNode, idx + 1);
          System.out.println("NOT RECUR");
        }
      } else if (!outNode.isSkeleton() && outNode.isCombinationNode() && !visited.contains(outNode)) {
        if (needToExpandCombinationNode(desc, outNode)) {
          System.out.println("NEED TO");
          expandCombinationNode(desc, lattice, visited, mapIntermediateLoc, locSummary, outNode);
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
      System.out.println("###SHARED ITEM=" + item);
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
            Pair<HNode, HNode> pair = new Pair(combinationNodeInSCGraph, outNode);
            if (visited.containsAll(incomingCombinedHNodeSet)) {
              visited.add(outNode);
              System.out.println("-------curIdx=" + (idx + 1));

              int newIdx = getCurrentHighestIndex(pair, idx + 1);
              // int newIdx = getCurrentHighestIndex(outNode, idx + 1);
              System.out.println("-------newIdx=" + newIdx);
              recurDFS(desc, lattice, combinationNodeInSCGraph, endNodeSet, visited,
                  mapIntermediateLoc, newIdx, locSummary, outNode);
              // recurDFS(desc, lattice, combinationNodeInSCGraph, endNodeSet, visited,
              // mapIntermediateLoc, idx + 1, locSummary, outNode);
            } else {
              updateHighestIndex(pair, idx + 1);
              // updateHighestIndex(outNode, idx + 1);
              System.out.println("-----NOT RECUR!");
            }
          } else {
            if (needToExpandCombinationNode(desc, outNode)) {
              System.out.println("NEED TO");
              expandCombinationNode(desc, lattice, visited, mapIntermediateLoc, locSummary, outNode);
            } else {
              System.out.println("NOT NEED TO");
            }

          }
        }
      }
      // }

    }

  }

  private int getCurrentHighestIndex(Pair<HNode, HNode> pair, int curIdx) {
    int recordedIdx = getCurrentHighestIndex(pair);
    if (recordedIdx > curIdx) {
      return recordedIdx;
    } else {
      return curIdx;
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

  private int getCurrentHighestIndex(Pair<HNode, HNode> pair) {
    if (!mapItemToHighestIndex.containsKey(pair)) {
      mapItemToHighestIndex.put(pair, new Integer(-1));
    }
    return mapItemToHighestIndex.get(pair).intValue();
  }

  private void updateHighestIndex(Pair<HNode, HNode> pair, int idx) {
    if (idx > getCurrentHighestIndex(pair)) {
      mapItemToHighestIndex.put(pair, new Integer(idx));
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
    System.out.println("Node2Index:\n" + inputGraph.getMapHNodeToUniqueIndex());
    System.out.println("Node2Basis:\n" + inputGraph.getMapHNodeToBasis());
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
