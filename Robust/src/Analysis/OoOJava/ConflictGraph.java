package Analysis.OoOJava;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import Analysis.Disjoint.AllocSite;
import Analysis.Disjoint.Effect;
import Analysis.Disjoint.Taint;
import IR.Flat.FlatNode;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.TempDescriptor;

public class ConflictGraph {

  public Hashtable<String, ConflictNode> id2cn;

  public static final int NON_WRITE_CONFLICT = 0;
  public static final int FINE_GRAIN_EDGE = 1;
  public static final int COARSE_GRAIN_EDGE = 2;

  public ConflictGraph() {
    id2cn = new Hashtable<String, ConflictNode>();
  }
  
  public void addLiveIn(Hashtable<Taint, Set<Effect>> taint2Effects) {
    Iterator entryIter = taint2Effects.entrySet().iterator();
    while (entryIter.hasNext()) {
      Entry entry = (Entry) entryIter.next();
      Taint taint = (Taint) entry.getKey();
      Set<Effect> effectSet = (Set<Effect>) entry.getValue();
      if (!effectSet.isEmpty()) {
        Iterator<Effect> effectIter = effectSet.iterator();
        while (effectIter.hasNext()) {
          Effect effect = (Effect) effectIter.next();
          addLiveInNodeEffect(taint, effect);
        }
      }
    }
  }

  public void addStallSite(Hashtable<Taint, Set<Effect>> taint2Effects) {
    Iterator entryIter = taint2Effects.entrySet().iterator();
    while (entryIter.hasNext()) {
      Entry entry = (Entry) entryIter.next();
      Taint taint = (Taint) entry.getKey();
      Set<Effect> effectSet = (Set<Effect>) entry.getValue();
      if (!effectSet.isEmpty()) {
        Iterator<Effect> effectIter = effectSet.iterator();
        while (effectIter.hasNext()) {
          Effect effect = (Effect) effectIter.next();
          addStallSiteEffect(taint, effect);
        }
      }
    }
  }

  public void addStallSiteEffect(Taint t, Effect e) {
    FlatNode fn = t.getStallSite();
    TempDescriptor var = t.getVar();
    AllocSite as = t.getAllocSite();

    String id = var + "_" + fn;
    ConflictNode node = id2cn.get(id);
    if (node == null) {
      node = new ConflictNode(id, ConflictNode.INVAR);
    }

    if (!id2cn.containsKey(id)) {

    } else {
      node = id2cn.get(id);
    }
    node.addEffect(as, e);

    id2cn.put(id, node);

  }

  public void addLiveInNodeEffect(Taint t, Effect e) {
    FlatSESEEnterNode sese = t.getSESE();
    TempDescriptor invar = t.getVar();
    AllocSite as = t.getAllocSite();

    String id = invar + "_" + sese.getIdentifier();

    ConflictNode node = id2cn.get(id);
    if (node == null) {
      node = new ConflictNode(id, ConflictNode.INVAR);
    }

    if (!id2cn.containsKey(id)) {

    } else {
      node = id2cn.get(id);
    }
    node.addEffect(as, e);

    id2cn.put(id, node);
  }

  public void addConflictEdge(int type, ConflictNode nodeU, ConflictNode nodeV) {

    // if there are two edges between the same node pair, coarse has a
    // priority
    Set<ConflictEdge> set = nodeU.getEdgeSet();
    ConflictEdge toBeRemoved = null;
    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      ConflictEdge conflictEdge = (ConflictEdge) iterator.next();

      if ((conflictEdge.getVertexU().equals(nodeU) && conflictEdge.getVertexV().equals(nodeV))
          || (conflictEdge.getVertexU().equals(nodeV) && conflictEdge.getVertexV().equals(nodeU))) {
        if (conflictEdge.getType() == ConflictGraph.FINE_GRAIN_EDGE
            && type == ConflictGraph.COARSE_GRAIN_EDGE) {
          toBeRemoved = conflictEdge;
          break;
        } else if (conflictEdge.getType() == ConflictGraph.COARSE_GRAIN_EDGE
            && type == ConflictGraph.FINE_GRAIN_EDGE) {
          // ignore
          return;
        }
      }
    }

    if (toBeRemoved != null) {
      nodeU.getEdgeSet().remove(toBeRemoved);
      nodeV.getEdgeSet().remove(toBeRemoved);
    }

    ConflictEdge newEdge = new ConflictEdge(nodeU, nodeV, type);
    nodeU.addEdge(newEdge);
    nodeV.addEdge(newEdge);

  }

  public void analyzeConflicts() {

    Set<String> keySet = id2cn.keySet();
    Set<String> analyzedIDSet = new HashSet<String>();

    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      String nodeID = (String) iterator.next();
      ConflictNode node = id2cn.get(nodeID);
      analyzePossibleConflicts(analyzedIDSet, node);
    }

  }

  private void analyzePossibleConflicts(Set<String> analyzedIDSet, ConflictNode currentNode) {
    // compare with all nodes
    // examine the case where self-edge exists

    int conflictType;
    if (currentNode.isInVarNode()) {
      conflictType = calculateConflictType(currentNode);
      if (conflictType > ConflictGraph.NON_WRITE_CONFLICT) {
        addConflictEdge(conflictType, currentNode, currentNode);
      }
    }

    Set<Entry<String, ConflictNode>> set = id2cn.entrySet();
    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      Entry<String, ConflictNode> entry = (Entry<String, ConflictNode>) iterator.next();

      String entryNodeID = entry.getKey();
      ConflictNode entryNode = entry.getValue();

      if ((!currentNode.getID().equals(entryNodeID))
          && !(analyzedIDSet.contains(currentNode.getID() + entryNodeID) || analyzedIDSet
              .contains(entryNodeID + currentNode.getID()))) {

        if (currentNode.isStallSiteNode() && entryNode.isInVarNode()) {
          /*
           * int conflictType = calculateConflictType((StallSiteNode)
           * currentNode, (LiveInNode) entryNode); if (conflictType > 0) {
           * addConflictEdge(conflictType, currentNode, entryNode); }
           * 
           * analyzedIDSet.add(currentNode.getID() + entryNodeID);
           */
        } else if (currentNode.isInVarNode() && entryNode.isInVarNode()) {
          conflictType = calculateConflictType(currentNode, entryNode);
          if (conflictType > ConflictGraph.NON_WRITE_CONFLICT) {
            addConflictEdge(conflictType, currentNode, entryNode);
          }
          analyzedIDSet.add(currentNode.getID() + entryNodeID);
        }
      }
    }

  }

  private int calculateConflictType(ConflictNode node) {

    int conflictType = ConflictGraph.NON_WRITE_CONFLICT;
    Hashtable<AllocSite, Set<Effect>> alloc2readEffects = node.getReadEffectSet();
    Hashtable<AllocSite, Set<Effect>> alloc2writeEffects = node.getWriteEffectSet();
    Hashtable<AllocSite, Set<Effect>> alloc2SUEffects = node.getStrongUpdateEffectSet();

    conflictType =
        updateConflictType(conflictType, determineConflictType(alloc2writeEffects,
            alloc2writeEffects));

    conflictType =
        updateConflictType(conflictType, hasStrongUpdateConflicts(alloc2SUEffects,
            alloc2readEffects, alloc2writeEffects));

    return conflictType;
  }

  private int calculateConflictType(ConflictNode nodeA, ConflictNode nodeB) {

    int conflictType = ConflictGraph.NON_WRITE_CONFLICT;

    Hashtable<AllocSite, Set<Effect>> alloc2readEffectsA = nodeA.getReadEffectSet();
    Hashtable<AllocSite, Set<Effect>> alloc2writeEffectsA = nodeA.getWriteEffectSet();
    Hashtable<AllocSite, Set<Effect>> alloc2SUEffectsA = nodeA.getStrongUpdateEffectSet();
    Hashtable<AllocSite, Set<Effect>> alloc2readEffectsB = nodeB.getReadEffectSet();
    Hashtable<AllocSite, Set<Effect>> alloc2writeEffectsB = nodeB.getWriteEffectSet();
    Hashtable<AllocSite, Set<Effect>> alloc2SUEffectsB = nodeB.getStrongUpdateEffectSet();

    // if node A has write effects on reading/writing regions of node B
    conflictType =
        updateConflictType(conflictType, determineConflictType(alloc2writeEffectsA,
            alloc2readEffectsB));
    conflictType =
        updateConflictType(conflictType, determineConflictType(alloc2writeEffectsA,
            alloc2writeEffectsB));

    // if node B has write effects on reading regions of node A
    determineConflictType(alloc2writeEffectsB, alloc2readEffectsA);

    // strong udpate effects conflict with all effects
    // on objects that are reachable from the same heap roots
    // if node A has SU on regions of node B
    if (!alloc2SUEffectsA.isEmpty()) {
      conflictType =
          updateConflictType(conflictType, hasStrongUpdateConflicts(alloc2SUEffectsA,
              alloc2readEffectsB, alloc2writeEffectsB));
    }

    // if node B has SU on regions of node A
    if (!alloc2SUEffectsB.isEmpty()) {
      conflictType =
          updateConflictType(conflictType, hasStrongUpdateConflicts(alloc2SUEffectsB,
              alloc2readEffectsA, alloc2writeEffectsA));
    }

    return conflictType;
  }

  private int hasStrongUpdateConflicts(Hashtable<AllocSite, Set<Effect>> SUEffectsTableA,
      Hashtable<AllocSite, Set<Effect>> readTableB, Hashtable<AllocSite, Set<Effect>> writeTableB) {

    int conflictType = ConflictGraph.NON_WRITE_CONFLICT;

    Iterator effectItrA = SUEffectsTableA.entrySet().iterator();
    while (effectItrA.hasNext()) {
      Map.Entry meA = (Map.Entry) effectItrA.next();
      AllocSite asA = (AllocSite) meA.getKey();
      Set<Effect> strongUpdateSetA = (Set<Effect>) meA.getValue();

      Iterator effectItrB = readTableB.entrySet().iterator();
      while (effectItrB.hasNext()) {
        Map.Entry meB = (Map.Entry) effectItrB.next();
        AllocSite asB = (AllocSite) meA.getKey();
        Set<Effect> esB = (Set<Effect>) meA.getValue();

        for (Iterator iterator = strongUpdateSetA.iterator(); iterator.hasNext();) {
          Effect strongUpdateA = (Effect) iterator.next();
          for (Iterator iterator2 = esB.iterator(); iterator2.hasNext();) {
            Effect effectB = (Effect) iterator2.next();

            if (strongUpdateA.getAffectedAllocSite().equals(effectB.getAffectedAllocSite())
                && strongUpdateA.getField().equals(effectB.getField())) {
              // possible conflict
              // check affected allocation site can be reached from both heap
              // roots
              // if(og.isReachable(asA, asB,
              // strongUpdateA.getAffectedAllocSite()){
              // return ConflictGraph.COARSE_GRAIN_EDGE;
              // }
            }

          }
        }
      }

      effectItrB = writeTableB.entrySet().iterator();
      while (effectItrB.hasNext()) {
        Map.Entry meB = (Map.Entry) effectItrB.next();
        AllocSite asB = (AllocSite) meA.getKey();
        Set<Effect> esB = (Set<Effect>) meA.getValue();

        for (Iterator iterator = strongUpdateSetA.iterator(); iterator.hasNext();) {
          Effect strongUpdateA = (Effect) iterator.next();
          for (Iterator iterator2 = esB.iterator(); iterator2.hasNext();) {
            Effect effectB = (Effect) iterator2.next();

            if (strongUpdateA.getAffectedAllocSite().equals(effectB.getAffectedAllocSite())
                && strongUpdateA.getField().equals(effectB.getField())) {
              // possible conflict
              // check affected allocation site can be reached from both heap
              // roots
              // if(og.isReachable(asA, asB,
              // strongUpdateA.getAffectedAllocSite()){
              // return ConflictGraph.COARSE_GRAIN_EDGE;
              // }
            }

          }
        }
      }

    }

    return conflictType;

  }

  private int determineConflictType(Hashtable<AllocSite, Set<Effect>> nodeAtable,
      Hashtable<AllocSite, Set<Effect>> nodeBtable) {

    int conflictType = ConflictGraph.NON_WRITE_CONFLICT;

    Iterator effectItrA = nodeAtable.entrySet().iterator();
    while (effectItrA.hasNext()) {
      Map.Entry meA = (Map.Entry) effectItrA.next();
      AllocSite asA = (AllocSite) meA.getKey();
      Set<Effect> esA = (Set<Effect>) meA.getValue();

      Iterator effectItrB = nodeBtable.entrySet().iterator();
      while (effectItrB.hasNext()) {
        Map.Entry meB = (Map.Entry) effectItrB.next();
        AllocSite asB = (AllocSite) meA.getKey();
        Set<Effect> esB = (Set<Effect>) meA.getValue();

        for (Iterator iterator = esA.iterator(); iterator.hasNext();) {
          Effect effectA = (Effect) iterator.next();
          for (Iterator iterator2 = esB.iterator(); iterator2.hasNext();) {
            Effect effectB = (Effect) iterator2.next();

            if (effectA.getAffectedAllocSite().equals(effectB.getAffectedAllocSite())
                && effectA.getField().equals(effectB.getField())) {
              // possible conflict
              /*
               * if(og.isReachable(asA, asB, effectA.getAffectedAllocSite())){
               * //affected allocation site can be reached from both heap roots
               * if(isFineGrainConflict()){
               * conflictType=updateConflictType(conflictType
               * ,ConflictGraph.FINE_GRAIN_EDGE); }else{
               * conflictType=updateConflictType
               * (conflictType,ConflictGraph.COARSE_GRAIN_EDGE); } }
               */
            }
          }
        }
      }
    }

    return conflictType;
  }

  private int updateConflictType(int current, int newType) {
    if (newType > current) {
      return newType;
    } else {
      return current;
    }
  }

  public HashSet<ConflictEdge> getEdgeSet() {

    HashSet<ConflictEdge> returnSet = new HashSet<ConflictEdge>();

    Collection<ConflictNode> nodes = id2cn.values();
    for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
      ConflictNode conflictNode = (ConflictNode) iterator.next();
      returnSet.addAll(conflictNode.getEdgeSet());
    }

    return returnSet;
  }

  public boolean hasConflictEdge() {

    Set<String> keySet = id2cn.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      String key = (String) iterator.next();
      ConflictNode node = id2cn.get(key);
      if (node.getEdgeSet().size() > 0) {
        return true;
      }
    }
    return false;
  }

  public void writeGraph(String graphName, boolean filter) throws java.io.IOException {

    graphName = graphName.replaceAll("[\\W]", "");

    BufferedWriter bw = new BufferedWriter(new FileWriter(graphName + ".dot"));
    bw.write("graph " + graphName + " {\n");

    // then visit every heap region node
    Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
    Iterator<Entry<String, ConflictNode>> i = s.iterator();

    HashSet<ConflictEdge> addedSet = new HashSet<ConflictEdge>();

    while (i.hasNext()) {
      Entry<String, ConflictNode> entry = i.next();
      ConflictNode node = entry.getValue();

      if (filter) {
        if (node.getID().startsWith("___dst") || node.getID().startsWith("___srctmp")
            || node.getID().startsWith("___neverused") || node.getID().startsWith("___temp")) {

          continue;
        }
      }

      String attributes = "[";

      attributes += "label=\"ID" + node.getID() + "\\n";

      if (node.isStallSiteNode()) {
        attributes += "STALL SITE" + "\\n" + "\"]";
      } else {
        attributes += "LIVE-IN" + "\\n" + "\"]";
      }
      bw.write(entry.getKey() + attributes + ";\n");

      Set<ConflictEdge> edgeSet = node.getEdgeSet();
      for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
        ConflictEdge conflictEdge = (ConflictEdge) iterator.next();

        ConflictNode u = conflictEdge.getVertexU();
        ConflictNode v = conflictEdge.getVertexV();

        if (filter) {
          String uID = u.getID();
          String vID = v.getID();
          if (uID.startsWith("___dst") || uID.startsWith("___srctmp")
              || uID.startsWith("___neverused") || uID.startsWith("___temp")
              || vID.startsWith("___dst") || vID.startsWith("___srctmp")
              || vID.startsWith("___neverused") || vID.startsWith("___temp")) {
            continue;
          }
        }

        if (!addedSet.contains(conflictEdge)) {
          bw.write(" " + u.getID() + "--" + v.getID() + "[label="
              + conflictEdge.toGraphEdgeString() + ",decorate];\n");
          addedSet.add(conflictEdge);
        }

      }
    }

    bw.write("  graphTitle[label=\"" + graphName + "\",shape=box];\n");

    bw.write("}\n");
    bw.close();

  }

}
