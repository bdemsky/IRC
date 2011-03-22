package Analysis.OoOJava;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import IR.State;

import Analysis.Disjoint.Alloc;
import Analysis.Disjoint.AllocSite;
import Analysis.Disjoint.DisjointAnalysis;
import Analysis.Disjoint.Effect;
import Analysis.Disjoint.Taint;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNew;
import IR.Flat.FlatNode;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.TempDescriptor;

public class ConflictGraph {

  protected Hashtable<String, ConflictNode> id2cn;
  protected Hashtable<FlatNode, Hashtable<Taint, Set<Effect>>> sese2te;

  protected DisjointAnalysis da;
  protected FlatMethod fmEnclosing;

  public static final int NON_WRITE_CONFLICT = 0;
  public static final int FINE_GRAIN_EDGE = 1;
  public static final int COARSE_GRAIN_EDGE = 2;
 
  State state;

  public ConflictGraph(State state) {
    this.state=state;
    id2cn = new Hashtable<String, ConflictNode>();
    sese2te = new Hashtable<FlatNode, Hashtable<Taint, Set<Effect>>>();
  }

  public void setDisJointAnalysis(DisjointAnalysis da) {
    this.da = da;
  }

  public void setFMEnclosing(FlatMethod fmEnclosing) {
    this.fmEnclosing = fmEnclosing;
  }

  public void addLiveIn(Hashtable<Taint, Set<Effect>> taint2Effects) {
    if (taint2Effects == null) {
      return;
    }
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

  public void addStallSite(Hashtable<Taint, Set<Effect>> taint2Effects, TempDescriptor var) {
    if (taint2Effects == null) {
      return;
    }
    Iterator entryIter = taint2Effects.entrySet().iterator();
    while (entryIter.hasNext()) {
      Entry entry = (Entry) entryIter.next();
      Taint taint = (Taint) entry.getKey();
      Set<Effect> effectSet = (Set<Effect>) entry.getValue();
      if (!effectSet.isEmpty()) {
        Iterator<Effect> effectIter = effectSet.iterator();
        while (effectIter.hasNext()) {
          Effect effect = (Effect) effectIter.next();
          if (taint.getVar().equals(var)) {
            addStallSiteEffect(taint, effect);
          }
        }
      }
    }
  }

  public void addStallSiteEffect(Taint t, Effect e) {
    FlatNode fn = t.getStallSite();
    TempDescriptor var = t.getVar();
    Alloc as = t.getAllocSite();

    String id = var + "_fn" + fn.hashCode();
    ConflictNode node = id2cn.get(id);
    if (node == null) {
      node = new ConflictNode(id, ConflictNode.STALLSITE, t.getVar(), t.getStallSite());
    }
    node.addEffect(as, e);
    node.addTaint(t);
    
    id2cn.put(id, node);
  }

  public void addLiveInNodeEffect(Taint t, Effect e) {

    FlatSESEEnterNode sese = t.getSESE();
    TempDescriptor invar = t.getVar();
    Alloc as = t.getAllocSite();

    String id = invar + "_sese" + sese.getPrettyIdentifier();
    ConflictNode node = id2cn.get(id);
    if (node == null) {
      node = new ConflictNode(id, ConflictNode.INVAR, t.getVar(), t.getSESE());
    }
    node.addEffect(as, e);
    node.addTaint(t);

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

  public void analyzeConflicts(Set<FlatNew> sitesToFlag, boolean useReachInfo) {

    Set<String> keySet = id2cn.keySet();
    Set<String> analyzedIDSet = new HashSet<String>();

    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      String nodeID = (String) iterator.next();
      ConflictNode node = id2cn.get(nodeID);
      analyzePossibleConflicts(analyzedIDSet, node, sitesToFlag, useReachInfo);
    }

  }

  private void analyzePossibleConflicts(Set<String> analyzedIDSet, ConflictNode currentNode,
      Set<FlatNew> sitesToFlag, boolean useReachInfo) {
    // compare with all nodes
    // examine the case where self-edge exists

    int conflictType;
    if (currentNode.isInVarNode()) {
      conflictType = calculateConflictType(currentNode, useReachInfo);
      if (conflictType > ConflictGraph.NON_WRITE_CONFLICT) {
        addConflictEdge(conflictType, currentNode, currentNode);
        if (sitesToFlag != null) {
          sitesToFlag.addAll(currentNode.getFlatNewSet());
        }
      }
    }

    Set<Entry<String, ConflictNode>> set = id2cn.entrySet();
    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      Entry<String, ConflictNode> entry = (Entry<String, ConflictNode>) iterator.next();

      String entryNodeID = entry.getKey();
      ConflictNode entryNode = entry.getValue();

      if (currentNode.isStallSiteNode() && entryNode.isStallSiteNode()) {
        continue;
      }

      if ((currentNode.isInVarNode() && entryNode.isInVarNode())
          && (currentNode.getSESEIdentifier() == entryNode.getSESEIdentifier())
          && (currentNode.getVar().equals(entryNode.getVar()))) {
        continue;
      }

      if ((!currentNode.getID().equals(entryNodeID))
          && !(analyzedIDSet.contains(currentNode.getID() + entryNodeID) || analyzedIDSet
               .contains(entryNodeID + currentNode.getID()))) {
        
        conflictType = calculateConflictType(currentNode, entryNode, useReachInfo);
        if (conflictType > ConflictGraph.NON_WRITE_CONFLICT) {
          addConflictEdge(conflictType, currentNode, entryNode);
          if (sitesToFlag != null) {
            sitesToFlag.addAll(currentNode.getFlatNewSet());
            sitesToFlag.addAll(entryNode.getFlatNewSet());
          }
        }
        analyzedIDSet.add(currentNode.getID() + entryNodeID);

      }
    }

  }

  private int calculateConflictType(ConflictNode node, boolean useReachInfo) {
    
    int conflictType = ConflictGraph.NON_WRITE_CONFLICT;

    Hashtable<Alloc, Set<Effect>> alloc2readEffects = node.getReadEffectSet();      
    Hashtable<Alloc, Set<Effect>> alloc2writeEffects = node.getWriteEffectSet();    
    Hashtable<Alloc, Set<Effect>> alloc2SUEffects = node.getStrongUpdateEffectSet(); 

    conflictType =
        updateConflictType(conflictType, determineConflictType(node, alloc2writeEffects, node,
            alloc2writeEffects, useReachInfo));

    conflictType =
        updateConflictType(conflictType, hasStrongUpdateConflicts(node, alloc2SUEffects, node,
            alloc2readEffects, alloc2writeEffects, useReachInfo));

    return conflictType;
  }

  private int calculateConflictType(ConflictNode nodeA, ConflictNode nodeB, boolean useReachInfo) {

    int conflictType = ConflictGraph.NON_WRITE_CONFLICT;

    Hashtable<Alloc, Set<Effect>> alloc2readEffectsA = nodeA.getReadEffectSet();
    Hashtable<Alloc, Set<Effect>> alloc2writeEffectsA = nodeA.getWriteEffectSet();
    Hashtable<Alloc, Set<Effect>> alloc2SUEffectsA = nodeA.getStrongUpdateEffectSet();
    Hashtable<Alloc, Set<Effect>> alloc2readEffectsB = nodeB.getReadEffectSet();
    Hashtable<Alloc, Set<Effect>> alloc2writeEffectsB = nodeB.getWriteEffectSet();
    Hashtable<Alloc, Set<Effect>> alloc2SUEffectsB = nodeB.getStrongUpdateEffectSet();

    // if node A has write effects on reading/writing regions of node B
    conflictType =
        updateConflictType(conflictType, determineConflictType(nodeA, alloc2writeEffectsA, nodeB,
            alloc2readEffectsB, useReachInfo));
    conflictType =
        updateConflictType(conflictType, determineConflictType(nodeA, alloc2writeEffectsA, nodeB,
            alloc2writeEffectsB, useReachInfo));

    // if node B has write effects on reading regions of node A
    conflictType =
        updateConflictType(conflictType, determineConflictType(nodeB, alloc2writeEffectsB, nodeA,
            alloc2readEffectsA, useReachInfo));

    // strong udpate effects conflict with all effects
    // on objects that are reachable from the same heap roots
    // if node A has SU on regions of node B
    if (!alloc2SUEffectsA.isEmpty()) {
      conflictType =
          updateConflictType(conflictType, hasStrongUpdateConflicts(nodeA, alloc2SUEffectsA, nodeB,
              alloc2readEffectsB, alloc2writeEffectsB, useReachInfo));
    }

    // if node B has SU on regions of node A
    if (!alloc2SUEffectsB.isEmpty()) {
      conflictType =
          updateConflictType(conflictType, hasStrongUpdateConflicts(nodeB, alloc2SUEffectsB, nodeA,
              alloc2readEffectsA, alloc2writeEffectsA, useReachInfo));
    }

    return conflictType;
  }

  private int hasStrongUpdateConflicts(ConflictNode nodeA,
      Hashtable<Alloc, Set<Effect>> SUEffectsTableA, ConflictNode nodeB,
      Hashtable<Alloc, Set<Effect>> readTableB, Hashtable<Alloc, Set<Effect>> writeTableB,
      boolean useReachInfo) {

    int conflictType = ConflictGraph.NON_WRITE_CONFLICT;

    Iterator effectItrA = SUEffectsTableA.entrySet().iterator();
    while (effectItrA.hasNext()) {
      Map.Entry meA = (Map.Entry) effectItrA.next();
      Alloc asA = (Alloc) meA.getKey();
      Set<Effect> strongUpdateSetA = (Set<Effect>) meA.getValue();

      Iterator effectItrB = readTableB.entrySet().iterator();
      while (effectItrB.hasNext()) {
        Map.Entry meB = (Map.Entry) effectItrB.next();
        Alloc asB = (Alloc) meB.getKey();
        Set<Effect> esB = (Set<Effect>) meB.getValue();

        for (Iterator iterator = strongUpdateSetA.iterator(); iterator.hasNext();) {
          Effect strongUpdateA = (Effect) iterator.next();
          for (Iterator iterator2 = esB.iterator(); iterator2.hasNext();) {
            Effect effectB = (Effect) iterator2.next();

            if (strongUpdateA.getAffectedAllocSite().equals(effectB.getAffectedAllocSite())
                && strongUpdateA.getField().equals(effectB.getField())) {
              if (useReachInfo) {
                FlatNew fnRoot1 = asA.getFlatNew();
                FlatNew fnRoot2 = asB.getFlatNew();
                FlatNew fnTarget = strongUpdateA.getAffectedAllocSite().getFlatNew();
                if (da.mayBothReachTarget(fmEnclosing, fnRoot1, fnRoot2, fnTarget)) {
                  addCoarseEffect(nodeA, asA, strongUpdateA);
                  if (!nodeA.equals(nodeB)) {
                    addCoarseEffect(nodeB, asB, effectB);
                  }
                  conflictType = updateConflictType(conflictType, ConflictGraph.COARSE_GRAIN_EDGE);
                }
              } else {
                if (state.RCR) {
                  // need coarse effects for RCR from just one pass
                  addCoarseEffect(nodeA, asA, strongUpdateA);
                  if (!nodeA.equals(nodeB)) {
                    addCoarseEffect(nodeB, asB, effectB);
                  }
                  conflictType=ConflictGraph.COARSE_GRAIN_EDGE;
                } else {
                  return ConflictGraph.COARSE_GRAIN_EDGE;
                }
              }

            }

          }
        }
      }

      effectItrB = writeTableB.entrySet().iterator();
      while (effectItrB.hasNext()) {
        Map.Entry meB = (Map.Entry) effectItrB.next();
        Alloc asB = (Alloc) meB.getKey();
        Set<Effect> esB = (Set<Effect>) meB.getValue();

        for (Iterator iterator = strongUpdateSetA.iterator(); iterator.hasNext();) {
          Effect strongUpdateA = (Effect) iterator.next();
          for (Iterator iterator2 = esB.iterator(); iterator2.hasNext();) {
            Effect effectB = (Effect) iterator2.next();

            if (strongUpdateA.getAffectedAllocSite().equals(effectB.getAffectedAllocSite())
                && strongUpdateA.getField().equals(effectB.getField())) {

              if (useReachInfo) {
                FlatNew fnRoot1 = asA.getFlatNew();
                FlatNew fnRoot2 = asB.getFlatNew();
                FlatNew fnTarget = strongUpdateA.getAffectedAllocSite().getFlatNew();
                if (da.mayBothReachTarget(fmEnclosing, fnRoot1, fnRoot2, fnTarget)) {
                  addCoarseEffect(nodeA, asA, strongUpdateA);
                  if (!nodeA.equals(nodeB)) {
                    addCoarseEffect(nodeB, asB, effectB);
                  }
                  conflictType = updateConflictType(conflictType, ConflictGraph.COARSE_GRAIN_EDGE);
                }
              } else {
                return ConflictGraph.COARSE_GRAIN_EDGE;
              }
            }

          }
        }
      }

    }

    return conflictType;

  }

  private int determineConflictType(ConflictNode nodeA,
      Hashtable<Alloc, Set<Effect>> nodeAtable, ConflictNode nodeB,
      Hashtable<Alloc, Set<Effect>> nodeBtable, boolean useReachInfo) {

    int conflictType = ConflictGraph.NON_WRITE_CONFLICT;

    Iterator effectItrA = nodeAtable.entrySet().iterator();
    while (effectItrA.hasNext()) {
      Map.Entry meA = (Map.Entry) effectItrA.next();
      Alloc asA = (Alloc) meA.getKey();
      Set<Effect> esA = (Set<Effect>) meA.getValue();

      Iterator effectItrB = nodeBtable.entrySet().iterator();
      while (effectItrB.hasNext()) {
        Map.Entry meB = (Map.Entry) effectItrB.next();
        Alloc asB = (Alloc) meB.getKey();
        Set<Effect> esB = (Set<Effect>) meB.getValue();

        for (Iterator iterator = esA.iterator(); iterator.hasNext();) {
          Effect effectA = (Effect) iterator.next();
          for (Iterator iterator2 = esB.iterator(); iterator2.hasNext();) {
            Effect effectB = (Effect) iterator2.next();

            if (effectA.getAffectedAllocSite().equals(effectB.getAffectedAllocSite())
                && ((effectA.getField()!=null&&effectB.getField()!=null&&effectA.getField().equals(effectB.getField()))||
		    (effectA.getField()==null&&effectB.getField()==null))) {

              if (useReachInfo) {
                FlatNew fnRoot1 = asA.getFlatNew();
                FlatNew fnRoot2 = asB.getFlatNew();
                FlatNew fnTarget = effectA.getAffectedAllocSite().getFlatNew();
                if (fnRoot1.equals(fnRoot2)) {
                  if (!da.mayManyReachTarget(fmEnclosing, fnRoot1, fnTarget)) {
                    // fine-grained conflict case
                    conflictType = updateConflictType(conflictType, ConflictGraph.FINE_GRAIN_EDGE);
                  } else {
                    // coarse-grained conflict case
                    addCoarseEffect(nodeA, asA, effectA);
                    if (!nodeA.equals(nodeB)) {
                      addCoarseEffect(nodeB, asB, effectB);
                    }
                    conflictType =
                        updateConflictType(conflictType, ConflictGraph.COARSE_GRAIN_EDGE);
                  }
                } else {
                  if (da.mayBothReachTarget(fmEnclosing, fnRoot1, fnRoot2, fnTarget)) {
                    addCoarseEffect(nodeA, asA, effectA);
                    if (!nodeA.equals(nodeB)) {
                      addCoarseEffect(nodeB, asB, effectB);
                    }
                    conflictType =
                        updateConflictType(conflictType, ConflictGraph.COARSE_GRAIN_EDGE);
                  } else {
                  }
                }
              } else {
                if (state.RCR) {
                  // need coarse effects for RCR from just one pass
                  addCoarseEffect(nodeA, asA, effectA);
                  if (!nodeA.equals(nodeB)) {
                    addCoarseEffect(nodeB, asB, effectB);
                  }
                  conflictType=ConflictGraph.COARSE_GRAIN_EDGE;
                } else {
                  return ConflictGraph.COARSE_GRAIN_EDGE;
                }
              }
            }
          }
        }
      }
    }

    return conflictType;
  }

  private void addCoarseEffect(ConflictNode node, Alloc as, Effect e) {
    Taint t = node.getTaint(as);
    addEffectSetByTaint(t, e);
  }

  private void addEffectSetByTaint(Taint t, Effect e) {

    FlatNode node=t.getSESE();
    if(node==null){
      // stall site case
      node=t.getStallSite();
    }
    
    Hashtable<Taint, Set<Effect>> taint2Conflicts = sese2te.get(node);
    if (taint2Conflicts == null) {
      taint2Conflicts = new Hashtable<Taint, Set<Effect>>();
    }

    Set<Effect> effectSet = taint2Conflicts.get(t);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);
    taint2Conflicts.put(t, effectSet);

    sese2te.put(node, taint2Conflicts);

  }

  private int updateConflictType(int current, int newType) {
    if (newType > current) {
      return newType;
    } else {
      return current;
    }
  }

  public void clearAllConflictEdge() {
    Collection<ConflictNode> nodes = id2cn.values();
    for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
      ConflictNode conflictNode = (ConflictNode) iterator.next();
      conflictNode.getEdgeSet().clear();
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

  public boolean isFineElement(int type) {
    if (type == ConflictNode.FINE_READ || type == ConflictNode.FINE_WRITE
        || type == ConflictNode.PARENT_READ || type == ConflictNode.PARENT_WRITE) {
      return true;
    } else {
      return false;
    }
  }

  public SESEWaitingQueue getWaitingElementSetBySESEID(int seseID, Set<SESELock> seseLockSet) {

    HashSet<WaitingElement> waitingElementSet = new HashSet<WaitingElement>();

    Iterator iter = id2cn.entrySet().iterator();
    while (iter.hasNext()) {
      Entry entry = (Entry) iter.next();
      String conflictNodeID = (String) entry.getKey();
      ConflictNode node = (ConflictNode) entry.getValue();

      if (node.isInVarNode()) {
        if (node.getSESEIdentifier() == seseID) {

          Set<ConflictEdge> edgeSet = node.getEdgeSet();
          for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
            ConflictEdge conflictEdge = (ConflictEdge) iterator.next();

            for (Iterator<SESELock> seseLockIter = seseLockSet.iterator(); seseLockIter.hasNext();) {
              SESELock seseLock = seseLockIter.next();
              if (seseLock.containsConflictNode(node)
                  && seseLock.containsConflictEdge(conflictEdge)) {
                WaitingElement newElement = new WaitingElement();
                newElement.setQueueID(seseLock.getID());
                newElement.setStatus(seseLock.getNodeType(node));
                newElement.setTempDesc(node.getVar());
                if (isFineElement(newElement.getStatus())) {
                  newElement.setDynID(node.getVar().toString());                  
                }
                if (!waitingElementSet.contains(newElement)) {
                  waitingElementSet.add(newElement);
                }

              }
            }
          }

        }
      }

    }

    // handle the case that multiple enqueues by an SESE for different live-in
    // into the same queue
     return refineQueue(waitingElementSet);  

  }

  public SESEWaitingQueue refineQueue(Set<WaitingElement> waitingElementSet) {

    Set<WaitingElement> refinedSet = new HashSet<WaitingElement>();
    HashMap<Integer, Set<WaitingElement>> map = new HashMap<Integer, Set<WaitingElement>>();
    SESEWaitingQueue seseDS = new SESEWaitingQueue();

    for (Iterator iterator = waitingElementSet.iterator(); iterator.hasNext();) {
      WaitingElement waitingElement = (WaitingElement) iterator.next();
      Set<WaitingElement> set = map.get(new Integer(waitingElement.getQueueID()));
      if (set == null) {
        set = new HashSet<WaitingElement>();
      }
      set.add(waitingElement);
      map.put(new Integer(waitingElement.getQueueID()), set);
    }
    
    Set<Integer> keySet = map.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Integer queueID = (Integer) iterator.next();
      Set<WaitingElement> queueWEset = map.get(queueID);
      refineQueue(queueID.intValue(), queueWEset, seseDS);
    }

    return seseDS;
  }

  private void refineQueue(int queueID, Set<WaitingElement> waitingElementSet,
      SESEWaitingQueue seseDS) {

    if (waitingElementSet.size() > 1) {
      // only consider there is more than one element submitted by same SESE
      Set<WaitingElement> refinedSet = new HashSet<WaitingElement>();

      int numCoarse = 0;
      int numRead = 0;
      int numWrite = 0;
      int total = waitingElementSet.size();
      WaitingElement SCCelement = null;
      WaitingElement coarseElement = null;

      for (Iterator iterator = waitingElementSet.iterator(); iterator.hasNext();) {
        WaitingElement waitingElement = (WaitingElement) iterator.next();
        if (waitingElement.getStatus() == ConflictNode.FINE_READ) {
          numRead++;
        } else if (waitingElement.getStatus() == ConflictNode.FINE_WRITE) {
          numWrite++;
        } else if (waitingElement.getStatus() == ConflictNode.COARSE) {
          numCoarse++;
          coarseElement = waitingElement;
        } else if (waitingElement.getStatus() == ConflictNode.SCC) {
          SCCelement = waitingElement;
        }
      }
      if (SCCelement != null) {
        // if there is at lease one SCC element, just enqueue SCC and
        // ignore others.
        if(state.RCR){
          // for rcr, we need to label all of coarse tempdescriptors
          // here assume that all waiting elements are coarse
          for (Iterator iterator = waitingElementSet.iterator(); iterator.hasNext();) {
            WaitingElement waitingElement = (WaitingElement) iterator.next();
            SCCelement.addTempDesc(waitingElement.getTempDesc());
            if(waitingElement!=SCCelement){
              waitingElement.setBogus(true);
              refinedSet.add(waitingElement);
            }
          }
        }
        refinedSet.add(SCCelement);
      } else if (numCoarse == 1 && (numRead + numWrite  == total)) {
        // if one is a coarse, the othere are reads/write, enqueue SCC.
        WaitingElement we = new WaitingElement();
        we.setQueueID(queueID);
        we.setStatus(ConflictNode.SCC);
        refinedSet.add(we);
      } else if (numCoarse == total) {
        // if there are multiple coarses, enqueue just one coarse.
        if(state.RCR){
          // for rcr, we need to label all of coarse tempdescriptors
          for (Iterator iterator = waitingElementSet.iterator(); iterator.hasNext();) {
            WaitingElement waitingElement = (WaitingElement) iterator.next();
            if(waitingElement!=coarseElement){
              coarseElement.addTempDesc(waitingElement.getTempDesc());
              waitingElement.setBogus(true);
              refinedSet.add(waitingElement);
            }
          }
        }
        refinedSet.add(coarseElement);
      } else if (numWrite == total || (numRead + numWrite) == total) {
        // code generator is going to handle the case for multiple writes &
        // read/writes.
        seseDS.setType(queueID, SESEWaitingQueue.EXCEPTION);
        refinedSet.addAll(waitingElementSet);
      } else {
        // otherwise, enqueue everything.
        refinedSet.addAll(waitingElementSet);
      }
      seseDS.setWaitingElementSet(queueID, refinedSet);
    } else {
      seseDS.setWaitingElementSet(queueID, waitingElementSet);
    }

  }

  public Set<WaitingElement> getStallSiteWaitingElementSet(FlatNode stallSite,
      Set<SESELock> seseLockSet) {

    HashSet<WaitingElement> waitingElementSet = new HashSet<WaitingElement>();
    Iterator iter = id2cn.entrySet().iterator();
    while (iter.hasNext()) {
      Entry entry = (Entry) iter.next();
      String conflictNodeID = (String) entry.getKey();
      ConflictNode node = (ConflictNode) entry.getValue();

      if (node.isStallSiteNode() && node.getStallSiteFlatNode().equals(stallSite)) {
        Set<ConflictEdge> edgeSet = node.getEdgeSet();
        for (Iterator iter2 = edgeSet.iterator(); iter2.hasNext();) {
          ConflictEdge conflictEdge = (ConflictEdge) iter2.next();

          for (Iterator<SESELock> seseLockIter = seseLockSet.iterator(); seseLockIter.hasNext();) {
            SESELock seseLock = seseLockIter.next();
            if (seseLock.containsConflictNode(node) && seseLock.containsConflictEdge(conflictEdge)) {
              WaitingElement newElement = new WaitingElement();
              newElement.setQueueID(seseLock.getID());
              newElement.setStatus(seseLock.getNodeType(node));
              if (isFineElement(newElement.getStatus())) {
                newElement.setDynID(node.getVar().toString());
              }
              newElement.setTempDesc(node.getVar());
              waitingElementSet.add(newElement);
            }
          }

        }

      }

    }

    return waitingElementSet;
  }

  public Hashtable<Taint, Set<Effect>> getConflictEffectSet(FlatNode fn) {
    return sese2te.get(fn);
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

        if (node.getEdgeSet().isEmpty()) {
          continue;
        }

      }

      String attributes = "[";

      attributes += "label=\"" + node.getID() + "\\n";

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
          bw.write("" + u.getID() + "--" + v.getID() + "[label=" + conflictEdge.toGraphEdgeString()
              + ",decorate];\n");
          addedSet.add(conflictEdge);
        }

      }
    }

    bw.write("  graphTitle[label=\"" + graphName + "\",shape=box];\n");

    bw.write("}\n");
    bw.close();

  }
  
  public Hashtable<String, ConflictNode> getId2cn() {
    return id2cn;
  }

}
