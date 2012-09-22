package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import IR.Descriptor;
import Util.Pair;

public class BuildLattice {

  public static int seed = 0;
  private LocationInference infer;

  public BuildLattice(LocationInference infer) {
    this.infer = infer;
  }

  public SSJavaLattice<String> buildLattice(HierarchyGraph inputGraph) {

    BasisSet basisSet = inputGraph.computeBasisSet();
    debug_print(inputGraph);

    Family family = generateFamily(basisSet);
    Map<Set<Integer>, Set<Set<Integer>>> mapImSucc = coveringGraph(basisSet, family);

    SSJavaLattice<String> lattice = buildLattice(basisSet, inputGraph, mapImSucc);
    return lattice;

  }

  private SSJavaLattice<String> buildLattice(BasisSet basisSet, HierarchyGraph inputGraph,
      Map<Set<Integer>, Set<Set<Integer>>> mapImSucc) {

    SSJavaLattice<String> lattice =
        new SSJavaLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM);

    Map<Set<Integer>, String> mapFToLocName = new HashMap<Set<Integer>, String>();

    Set<Set<Integer>> keySet = mapImSucc.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Set<Integer> higher = (Set<Integer>) iterator.next();

      String higherName = generateElementName(basisSet, inputGraph, mapFToLocName, higher);

      Set<Set<Integer>> lowerSet = mapImSucc.get(higher);
      for (Iterator iterator2 = lowerSet.iterator(); iterator2.hasNext();) {
        Set<Integer> lower = (Set<Integer>) iterator2.next();

        String lowerName = generateElementName(basisSet, inputGraph, mapFToLocName, lower);

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

  public HNode getCombinationNodeInSCGraph(Descriptor desc, HNode simpleGraphNode) {

    Set<HNode> combineSkeletonNodeSet =
        infer.getSimpleHierarchyGraph(desc).getCombineSetByCombinationNode(simpleGraphNode);
    HNode combinationNodeInSCGraph =
        infer.getSkeletonCombinationHierarchyGraph(desc).getCombinationNode(combineSkeletonNodeSet);
    return combinationNodeInSCGraph;
  }

  public SSJavaLattice<String> insertIntermediateNodesToStraightLine(Descriptor desc,
      SSJavaLattice<String> skeletonLattice) {

    // perform DFS that starts from each skeleton/combination node and ends by another
    // skeleton/combination node

    HierarchyGraph simpleGraph = infer.getSimpleHierarchyGraph(desc);
    HierarchyGraph scGraph = infer.getSkeletonCombinationHierarchyGraph(desc);

    SSJavaLattice<String> lattice = skeletonLattice.clone();

    Map<TripleItem, String> mapIntermediateLocation = new HashMap<TripleItem, String>();

    Set<HNode> visited = new HashSet<HNode>();

    Set<HNode> nodeSet = simpleGraph.getNodeSet();

    // expand a combination node
    Map<TripleItem, String> mapIntermediateLoc = new HashMap<TripleItem, String>();
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      HNode node = (HNode) iterator.next();
      if (node.isSkeleton() && (!visited.contains(node))) {
        visited.add(node);

        Set<HNode> outSet = simpleGraph.getOutgoingNodeSet(node);
        for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
          HNode outNode = (HNode) iterator2.next();

          if (!outNode.isSkeleton()) {
            if (outNode.isCombinationNode()) {
              // here we need to expand the corresponding combination location in the lattice
              HNode combinationNodeInSCGraph = getCombinationNodeInSCGraph(desc, outNode);

              Set<HNode> combineSkeletonNodeSet =
                  simpleGraph.getCombineSetByCombinationNode(outNode);
              Set<HNode> combinationNodeSet =
                  simpleGraph.getCombinationNodeSetByCombineNodeSet(combineSkeletonNodeSet);
              Set<HNode> endNodeSetFromSimpleGraph =
                  simpleGraph.getDirectlyReachableSkeletonCombinationNodeFrom(outNode,
                      combinationNodeSet);
              Set<HNode> endCombNodeSet = new HashSet<HNode>();
              for (Iterator iterator3 = endNodeSetFromSimpleGraph.iterator(); iterator3.hasNext();) {
                HNode endNode = (HNode) iterator3.next();
                endCombNodeSet.add(getCombinationNodeInSCGraph(desc, endNode));
              }
              visited.add(outNode);
              // follows the straight line up to another skeleton/combination node
              if (endCombNodeSet.size() > 0) {
                recurDFS(desc, lattice, combinationNodeInSCGraph, endCombNodeSet, visited,
                    mapIntermediateLoc, 1, outNode);
              }

            } else {
              // we have a node that is neither combination or skeleton node
              HNode startNode = node;
              Set<HNode> endNodeSetFromSimpleGraph =
                  simpleGraph.getDirectlyReachableSkeletonCombinationNodeFrom(outNode, null);

              Set<HNode> endCombNodeSet = new HashSet<HNode>();
              for (Iterator iterator3 = endNodeSetFromSimpleGraph.iterator(); iterator3.hasNext();) {
                HNode endNode = (HNode) iterator3.next();
                endCombNodeSet.add(getCombinationNodeInSCGraph(desc, endNode));
              }

              visited.add(outNode);
              if (endCombNodeSet.size() > 0) {
                // follows the straight line up to another skeleton/combination node
                recurDFSNormalNode(desc, lattice, startNode, endCombNodeSet, visited,
                    mapIntermediateLoc, 1, outNode);

              }

            }

          }

        }
      }
    }

    return lattice;

  }

  private void recurDFSNormalNode(Descriptor desc, SSJavaLattice<String> lattice, HNode startNode,
      Set<HNode> endNodeSet, Set<HNode> visited, Map<TripleItem, String> mapIntermediateLoc,
      int idx, HNode curNode) {

    TripleItem item = new TripleItem(startNode, endNodeSet, idx);

    if (!mapIntermediateLoc.containsKey(item)) {
      // need to create a new intermediate location in the lattice
      String newLocName = "ILOC" + (seed++);
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
        belowSet.add(endNode.getName());
      }

      lattice.insertNewLocationBetween(above, belowSet, newLocName);

      mapIntermediateLoc.put(item, newLocName);
    }

    String locName = mapIntermediateLoc.get(item);

    HierarchyGraph graph = infer.getSimpleHierarchyGraph(desc);
    Set<HNode> outSet = graph.getOutgoingNodeSet(curNode);
    for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
      HNode outNode = (HNode) iterator2.next();
      if (!outNode.isSkeleton() && !outNode.isCombinationNode() && !visited.contains(outNode)) {
        visited.add(outNode);
        recurDFSNormalNode(desc, lattice, startNode, endNodeSet, visited, mapIntermediateLoc,
            idx + 1, outNode);
      }
    }

  }

  private void recurDFS(Descriptor desc, SSJavaLattice<String> lattice,
      HNode combinationNodeInSCGraph, Set<HNode> endNodeSet, Set<HNode> visited,
      Map<TripleItem, String> mapIntermediateLoc, int idx, HNode curNode) {

    TripleItem item = new TripleItem(combinationNodeInSCGraph, endNodeSet, idx);

    if (!mapIntermediateLoc.containsKey(item)) {
      // need to create a new intermediate location in the lattice
      String newLocName = "ILOC" + (seed++);
      String above;
      if (idx == 1) {
        above = combinationNodeInSCGraph.getName();
      } else {
        int prevIdx = idx - 1;
        TripleItem prevItem = new TripleItem(combinationNodeInSCGraph, endNodeSet, prevIdx);
        above = mapIntermediateLoc.get(prevItem);
      }

      Set<String> belowSet = new HashSet<String>();
      for (Iterator iterator = endNodeSet.iterator(); iterator.hasNext();) {
        HNode endNode = (HNode) iterator.next();
        belowSet.add(endNode.getName());
      }
      lattice.insertNewLocationBetween(above, belowSet, newLocName);

      mapIntermediateLoc.put(item, newLocName);

      String locName = mapIntermediateLoc.get(item);

    }

    HierarchyGraph graph = infer.getSimpleHierarchyGraph(desc);
    Set<HNode> outSet = graph.getOutgoingNodeSet(curNode);
    for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
      HNode outNode = (HNode) iterator2.next();
      if (!outNode.isSkeleton() && !visited.contains(outNode)) {
        if (combinationNodeInSCGraph.equals(getCombinationNodeInSCGraph(desc, outNode))) {
          visited.add(outNode);
          recurDFS(desc, lattice, combinationNodeInSCGraph, endNodeSet, visited,
              mapIntermediateLoc, idx + 1, outNode);
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
        String str = "LOC" + (seed++);
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

    System.out.println("mapImSucc=" + mapImSucc);

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

  public TripleItem(HNode h, Set<HNode> l, int i) {
    higherNode = h;
    lowerNodeSet = l;
    idx = i;
  }

  public int hashCode() {
    return higherNode.hashCode() + lowerNodeSet.hashCode() + idx;
  }

  public boolean equals(Object obj) {

    if (obj instanceof TripleItem) {
      TripleItem in = (TripleItem) obj;
      if (higherNode.equals(in.higherNode) && lowerNodeSet.equals(in.lowerNodeSet) && idx == in.idx) {
        return true;
      }
    }

    return false;
  }

  public String toString() {
    return higherNode + "-" + idx + "->" + lowerNodeSet;
  }
}
