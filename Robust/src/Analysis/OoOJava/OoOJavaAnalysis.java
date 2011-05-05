package Analysis.OoOJava;

import java.io.*;
import java.util.*;

import Analysis.*;
import Analysis.CallGraph.*;
import Analysis.Disjoint.*;
import Analysis.Pointer.*;
import Util.*;
import IR.*;
import IR.Flat.*;

public class OoOJavaAnalysis {

  // data from the compiler
  private State state;
  private TypeUtil typeUtil;
  private CallGraph callGraph;
  private Liveness liveness;
  private RBlockRelationAnalysis rblockRel;
  private HeapAnalysis disjointAnalysisTaints;
  private DisjointAnalysis disjointAnalysisReach;
  private BuildStateMachines buildStateMachines;

  private Set<MethodDescriptor> descriptorsToAnalyze;

  private Hashtable<FlatNode, Set<TempDescriptor>> livenessGlobalView;
  private Hashtable<FlatNode, Set<TempDescriptor>> livenessVirtualReads;
  private Hashtable<FlatNode, VarSrcTokTable> variableResults;
  private Hashtable<FlatNode, Set<TempDescriptor>> notAvailableResults;
  private Hashtable<FlatNode, CodePlan> codePlans;

  private Hashtable<FlatSESEEnterNode, Set<TempDescriptor>> notAvailableIntoSESE;

  private Hashtable<FlatEdge, FlatWriteDynamicVarNode> wdvNodesToSpliceIn;

  private Hashtable<FlatNode, ContextTaskNames> fn2contextTaskNames;

  // get the flat method that contains any given flat node
  private Hashtable<FlatNode, FlatMethod> fn2fm;

  // temporal data structures to track analysis progress.
  static private int uniqueLockSetId = 0;
  // mapping of a conflict graph to its compiled lock
  private Hashtable<ConflictGraph, HashSet<SESELock>> conflictGraph2SESELock;
  // mapping of a sese block to its conflict graph
  private Hashtable<FlatNode, ConflictGraph> sese2conflictGraph;

  public static int maxSESEage = -1;

  public int getMaxSESEage() {
    return maxSESEage;
  }

  // may be null
  public CodePlan getCodePlan(FlatNode fn) {
    CodePlan cp = codePlans.get(fn);
    return cp;
  }

  public Set<FlatNode> getNodesWithPlans() {
    return codePlans.keySet();
  }

  public ContextTaskNames getContextTaskNames(FlatMethod fm) {
    ContextTaskNames out = fn2contextTaskNames.get(fm);
    if (out == null) {
      out = new ContextTaskNames();
    }
    return out;
  }

  public ContextTaskNames getContextTaskNames(FlatSESEEnterNode fsen) {
    ContextTaskNames out = fn2contextTaskNames.get(fsen);
    if (out == null) {
      out = new ContextTaskNames();
    }
    return out;
  }

  public FlatMethod getContainingFlatMethod(FlatNode fn) {
    FlatMethod fm = fn2fm.get(fn);
    assert fm != null;
    return fm;
  }

  public HeapAnalysis getDisjointAnalysis() {
    return disjointAnalysisTaints;
  }

  public BuildStateMachines getBuildStateMachines() {
    return buildStateMachines;
  }

  public OoOJavaAnalysis(State state, TypeUtil typeUtil, CallGraph callGraph, Liveness liveness,
                         ArrayReferencees arrayReferencees) {

    State.logEvent("Starting OoOJavaAnalysis");
    this.state = state;
    this.typeUtil = typeUtil;
    this.callGraph = callGraph;
    this.liveness = liveness;
    this.maxSESEage = state.OOO_MAXSESEAGE;

    livenessGlobalView = new Hashtable<FlatNode, Set<TempDescriptor>>();
    livenessVirtualReads = new Hashtable<FlatNode, Set<TempDescriptor>>();
    variableResults = new Hashtable<FlatNode, VarSrcTokTable>();
    notAvailableResults = new Hashtable<FlatNode, Set<TempDescriptor>>();
    codePlans = new Hashtable<FlatNode, CodePlan>();
    wdvNodesToSpliceIn = new Hashtable<FlatEdge, FlatWriteDynamicVarNode>();
    notAvailableIntoSESE = new Hashtable<FlatSESEEnterNode, Set<TempDescriptor>>();
    sese2conflictGraph = new Hashtable<FlatNode, ConflictGraph>();
    conflictGraph2SESELock = new Hashtable<ConflictGraph, HashSet<SESELock>>();
    fn2contextTaskNames = new Hashtable<FlatNode, ContextTaskNames>();
    fn2fm = new Hashtable<FlatNode, FlatMethod>();

    // state machines support heap examiners with
    // state transitions to improve precision
    if (state.RCR) {
      buildStateMachines = new BuildStateMachines();
    }

    // add all methods transitively reachable from the
    // source's main to set for analysis
    MethodDescriptor mdSourceEntry = typeUtil.getMain();
    FlatMethod fmMain = state.getMethodFlat(mdSourceEntry);

    descriptorsToAnalyze = callGraph.getAllMethods(mdSourceEntry);

    descriptorsToAnalyze.add(mdSourceEntry);

    // 0th pass, setup a useful mapping of any flat node to the
    // flat method it is a part of
    Iterator<MethodDescriptor> methItr = descriptorsToAnalyze.iterator();
    while (methItr.hasNext()) {
      Descriptor d = methItr.next();
      FlatMethod fm = state.getMethodFlat(d);
      buildFlatNodeToFlatMethod(fm);
    }
    State.logEvent("OoOJavaAnalysis Oth pass completed");

    // 1st pass, find basic rblock relations & potential stall sites
    rblockRel = new RBlockRelationAnalysis(state, typeUtil, callGraph);
    VarSrcTokTable.rblockRel = rblockRel;

    State.logEvent("OoOJavaAnalysis 1st pass completed");

    // 2nd pass, liveness, in-set out-set (no virtual reads yet!)
    Iterator<FlatSESEEnterNode> seseItr = rblockRel.getLocalRootSESEs().iterator();
    while (seseItr.hasNext()) {
      FlatSESEEnterNode sese = seseItr.next();
      livenessAnalysisBackward(sese);
    }

    State.logEvent("OoOJavaAnalysis 2nd pass completed");

    // 3rd pass, variable analysis
    methItr = rblockRel.getMethodsWithSESEs().iterator();
    while (methItr.hasNext()) {
      Descriptor d = methItr.next();
      FlatMethod fm = state.getMethodFlat(d);
      // starting from roots do a forward, fixed-point
      // variable analysis for refinement and stalls
      variableAnalysisForward(fm);
    }

    State.logEvent("OoOJavaAnalysis 3rd pass completed");

    // 4th pass, compute liveness contribution from
    // virtual reads discovered in variable pass
    seseItr = rblockRel.getLocalRootSESEs().iterator();
    while (seseItr.hasNext()) {
      FlatSESEEnterNode sese = seseItr.next();
      livenessAnalysisBackward(sese);
    }

    State.logEvent("OoOJavaAnalysis 4th pass completed");

    // 5th pass, use disjointness with NO FLAGGED REGIONS
    // to compute taints and effects
    if (state.POINTER) {
      disjointAnalysisTaints =
        new Pointer(state, typeUtil, callGraph, rblockRel, liveness, buildStateMachines);
      ((Pointer) disjointAnalysisTaints).doAnalysis();
    } else {
      disjointAnalysisTaints =
        new DisjointAnalysis(state, typeUtil, callGraph, liveness, arrayReferencees, null,
                             rblockRel, buildStateMachines, 
                             !state.OOODEBUG // only print out in OoOJava debug mode
                             );
    }
    State.logEvent("OoOJavaAnalysis 5th pass completed");

    // 6th pass, not available analysis FOR VARIABLES!
    methItr = rblockRel.getMethodsWithSESEs().iterator();
    while (methItr.hasNext()) {
      Descriptor d = methItr.next();
      FlatMethod fm = state.getMethodFlat(d);
      // compute what is not available at every program
      // point, in a forward fixed-point pass
      notAvailableForward(fm);
    }
    State.logEvent("OoOJavaAnalysis 6th pass completed");
    // 7th pass, start conflict graphs where a parent's graph has a
    // node for possibly conflicting children and its own stall sites
    startConflictGraphs();
    State.logEvent("OoOJavaAnalysis 7th pass completed");
    // 8th pass, calculate all possible conflicts without using
    // reachability info and identify set of FlatNew that next
    // disjoint reach. analysis should flag
    Set<FlatNew> sitesToFlag = new HashSet<FlatNew>();
    calculateConflicts(sitesToFlag, false);
    State.logEvent("OoOJavaAnalysis 8th pass completed");
    if (!state.RCR) {
      // 9th pass, ask disjoint analysis to compute reachability
      // for objects that may cause heap conflicts so the most
      // efficient method to deal with conflict can be computed
      // later
      disjointAnalysisReach =
        new DisjointAnalysis(state, typeUtil, callGraph, liveness, arrayReferencees, sitesToFlag,
                             null, // don't do effects analysis again!
                             null, // no BuildStateMachines needed
                             !state.OOODEBUG // only print out in OoOJava debug mode
                             );
      State.logEvent("OoOJavaAnalysis 9th pass completed");
      // 10th pass, calculate conflicts with reachability info
      calculateConflicts(null, true);
      State.logEvent("OoOJavaAnalysis 10th pass completed");
    } else {
      // in RCR/DFJ we want to do some extra processing on the
      // state machines before they get handed off to code gen,
      // and we're doing the same effect-conflict traversal needed
      // to identify heap examiners that are weakly connected, so
      // accomplish both at the same time
      pruneMachinesAndFindWeaklyConnectedExaminers();
      State.logEvent("OoOJavaAnalysis RCR pruneMachines pass completed");
    }

    // 11th pass, compiling memory Qs! The name "lock" is a legacy
    // term for the heap dependence queue, or memQ as the runtime calls it
    synthesizeLocks();
    State.logEvent("OoOJavaAnalysis 11th pass completed");

    // 12th pass, compute a plan for code injections
    methItr = descriptorsToAnalyze.iterator();
    while (methItr.hasNext()) {
      Descriptor d = methItr.next();
      FlatMethod fm = state.getMethodFlat(d);
      codePlansForward(fm);
    }

    State.logEvent("OoOJavaAnalysis 12th pass completed");
    // 13th pass,
    // splice new IR nodes into graph after all
    // analysis passes are complete
    Iterator spliceItr = wdvNodesToSpliceIn.entrySet().iterator();
    while (spliceItr.hasNext()) {
      Map.Entry me = (Map.Entry)spliceItr.next();
      FlatWriteDynamicVarNode fwdvn = (FlatWriteDynamicVarNode) me.getValue();
      fwdvn.spliceIntoIR();
    }
    State.logEvent("OoOJavaAnalysis 13th pass completed");

    if (state.OOODEBUG) {
      try {
        writeReports("");
        disjointAnalysisTaints.getEffectsAnalysis().writeEffects("effects.txt");
        writeConflictGraph();
      } catch (IOException e) {
      }
    }
    State.logEvent("OoOJavaAnalysis completed");
  }

  private void buildFlatNodeToFlatMethod(FlatMethod fm) {
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    Set<FlatNode> flatNodesVisited = new HashSet<FlatNode>();

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);
      flatNodesVisited.add(fn);

      fn2fm.put(fn, fm);

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);
        if (!flatNodesVisited.contains(nn)) {
          flatNodesToVisit.add(nn);
        }
      }
    }
  }

  // debug routine
  /*
   * Iterator iter = sese2conflictGraph.entrySet().iterator(); while
   * (iter.hasNext()) { Entry e = (Entry) iter.next(); FlatNode fn = (FlatNode)
   * e.getKey(); ConflictGraph conflictGraph = (ConflictGraph) e.getValue();
   * System.out.println("---------------------------------------");
   * System.out.println("CONFLICT GRAPH for " + fn); Set<String> keySet =
   * conflictGraph.id2cn.keySet(); for (Iterator iterator = keySet.iterator();
   * iterator.hasNext();) { String key = (String) iterator.next(); ConflictNode
   * node = conflictGraph.id2cn.get(key); System.out.println("key=" + key +
   * " \n" + node.toStringAllEffects()); } }
   */

  private void writeFile(Set<FlatNew> sitesToFlag) {

    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter("sitesToFlag.txt"));

      for (Iterator iterator = sitesToFlag.iterator(); iterator.hasNext(); ) {
        FlatNew fn = (FlatNew) iterator.next();
        bw.write(fn + "\n");
      }
      bw.close();
    } catch (IOException e) {

    }

  }

  private void livenessAnalysisBackward(FlatSESEEnterNode fsen) {

    // flow backward across nodes to compute liveness, and
    // take special care with sese enter/exit nodes that
    // alter this from normal liveness analysis
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    // flatNodesToVisit.add( fm.getFlatExit() );
    flatNodesToVisit.add(fsen.getFlatExit());

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Set<TempDescriptor> prev = livenessGlobalView.get(fn);

      // merge sets from control flow joins
      Set<TempDescriptor> livein = new HashSet<TempDescriptor>();
      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);
        Set<TempDescriptor> s = livenessGlobalView.get(nn);
        if (s != null) {
          livein.addAll(s);
        }
      }

      Set<TempDescriptor> curr = liveness_nodeActions(fn, livein);

      // if a new result, schedule backward nodes for analysis
      if (!curr.equals(prev)) {

        if (fn != fsen) {
          livenessGlobalView.put(fn, curr);
          for (int i = 0; i < fn.numPrev(); i++) {
            FlatNode nn = fn.getPrev(i);
            flatNodesToVisit.add(nn);
          }
        }
      }
    }
  }

  private Set<TempDescriptor> liveness_nodeActions(FlatNode fn, Set<TempDescriptor> liveIn) {
    switch (fn.kind()) {

    case FKind.FlatSESEEnterNode: {
      // add whatever is live-in at a task enter to that
      // task's in-var set
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
      if (liveIn != null) {
        fsen.addInVarSet(liveIn);
      }
      // no break, should also execute default actions
    }

    default: {
      // handle effects of statement in reverse, writes then reads
      TempDescriptor[] writeTemps = fn.writesTemps();
      for (int i = 0; i < writeTemps.length; ++i) {
        liveIn.remove(writeTemps[i]);

        // if we are analyzing code declared directly in a task,
        FlatSESEEnterNode fsen = rblockRel.getLocalInnerRBlock(fn);
        if (fsen != null) {
          // check to see if we are writing to variables that will
          // be live-out at the task's exit (and therefore should
          // go in the task's out-var set)
          FlatSESEExitNode fsexn = fsen.getFlatExit();
          // note: liveness analysis can have corresponding decisions
          Set<TempDescriptor> livetemps = liveness.getLiveInTemps(fsen.getfmEnclosing(), fsexn);
          if (livetemps != null && livetemps.contains(writeTemps[i])) {
            fsen.addOutVar(writeTemps[i]);
          }
        }
      }

      TempDescriptor[] readTemps = fn.readsTemps();
      for (int i = 0; i < readTemps.length; ++i) {
        liveIn.add(readTemps[i]);
      }

      Set<TempDescriptor> virtualReadTemps = livenessVirtualReads.get(fn);
      if (virtualReadTemps != null) {
        liveIn.addAll(virtualReadTemps);
      }
    }
    break;

    } // end switch

    return liveIn;
  }

  private void variableAnalysisForward(FlatMethod fm) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      VarSrcTokTable prev = variableResults.get(fn);

      // merge sets from control flow joins
      VarSrcTokTable curr = new VarSrcTokTable();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        VarSrcTokTable incoming = variableResults.get(nn);
        curr.merge(incoming);
      }

      FlatSESEEnterNode currentSESE = rblockRel.getLocalInnerRBlock(fn);
      if (currentSESE == null) {
        currentSESE = rblockRel.getCallerProxySESE();
      }

      variable_nodeActions(fn, curr, currentSESE);

      // if a new result, schedule forward nodes for analysis
      if (!curr.equals(prev)) {
        variableResults.put(fn, curr);

        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          flatNodesToVisit.add(nn);
        }
      }
    }
  }

  private void variable_nodeActions(FlatNode fn, VarSrcTokTable vstTable,
                                    FlatSESEEnterNode currentSESE) {
    switch (fn.kind()) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
      // ignore currently executing SESE, at this point
      // the analysis considers a new instance is becoming
      // the current SESE
      vstTable.age(fsen);
      vstTable.assertConsistency();
    }
    break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;

      // fsen is the child of currently executing tasks
      FlatSESEEnterNode fsen = fsexn.getFlatEnter();

      // remap all of this child's children tokens to be
      // from this child as the child exits
      vstTable.remapChildTokens(fsen);

      // liveness virtual reads are things that might be
      // written by an SESE and should be added to the in-set
      // anything virtually read by this SESE should be pruned
      // of parent or sibling sources
      Set<TempDescriptor> liveVars = liveness.getLiveInTemps(fsen.getfmEnclosing(), fn);

      Set<TempDescriptor> fsenVirtReads =
        vstTable.calcVirtReadsAndPruneParentAndSiblingTokens(fsen, liveVars);

      Set<TempDescriptor> fsenVirtReadsOld = livenessVirtualReads.get(fn);
      if (fsenVirtReadsOld != null) {
        fsenVirtReads.addAll(fsenVirtReadsOld);
      }
      livenessVirtualReads.put(fn, fsenVirtReads);

      // virtual reads are forced in-vars!
      fsen.addInVarSet(fsenVirtReads);

      // then all child out-set tokens are guaranteed
      // to be filled in, so clobber those entries with
      // the latest, clean sources
      Iterator<TempDescriptor> outVarItr = fsen.getOutVarSet().iterator();
      while (outVarItr.hasNext()) {
        TempDescriptor outVar = outVarItr.next();
        HashSet<TempDescriptor> ts = new HashSet<TempDescriptor>();
        ts.add(outVar);
        VariableSourceToken vst = new VariableSourceToken(ts, fsen, new Integer(0), outVar);
        vstTable.remove(outVar);
        vstTable.add(vst);
      }
      vstTable.assertConsistency();
    }
    break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;

      if (fon.getOp().getOp() == Operation.ASSIGN) {
        TempDescriptor lhs = fon.getDest();
        TempDescriptor rhs = fon.getLeft();

        vstTable.remove(lhs);

        Set<VariableSourceToken> forAddition = new HashSet<VariableSourceToken>();

        Iterator<VariableSourceToken> itr = vstTable.get(rhs).iterator();
        while (itr.hasNext()) {
          VariableSourceToken vst = itr.next();

          HashSet<TempDescriptor> ts = new HashSet<TempDescriptor>();
          ts.add(lhs);

          // when we do x = y for variables, just copy over from a child,
          // there are two cases:
          // 1. if the current task is the caller proxy, any local root is a
          // child
          boolean case1 =
            currentSESE.getIsCallerProxySESE()
            && rblockRel.getLocalRootSESEs().contains(vst.getSESE());

          // 2. if the child task is a locally-defined child of the current task
          boolean case2 = currentSESE.getLocalChildren().contains(vst.getSESE());

          if (case1 || case2) {
            // if the source comes from a child, copy it over
            forAddition.add(new VariableSourceToken(ts, vst.getSESE(), vst.getAge(), vst
                                                    .getAddrVar()));
          } else {
            // otherwise, stamp it as us as the source
            forAddition.add(new VariableSourceToken(ts, currentSESE, new Integer(0), lhs));
          }
        }

        vstTable.addAll(forAddition);

        // only break if this is an ASSIGN op node,
        // otherwise fall through to default case
        vstTable.assertConsistency();
        break;
      }
    }

    // note that FlatOpNode's that aren't ASSIGN
    // fall through to this default case
    default: {
      TempDescriptor[] writeTemps = fn.writesTemps();
      if (writeTemps.length > 0) {

        // for now, when writeTemps > 1, make sure
        // its a call node, programmer enforce only
        // doing stuff like calling a print routine
        if (writeTemps.length > 1) {
          assert fn.kind() == FKind.FlatCall || fn.kind() == FKind.FlatMethod;
          break;
        }

        vstTable.remove(writeTemps[0]);

        HashSet<TempDescriptor> ts = new HashSet<TempDescriptor>();
        ts.add(writeTemps[0]);

        vstTable.add(new VariableSourceToken(ts, currentSESE, new Integer(0), writeTemps[0]));
      }

      vstTable.assertConsistency();
    }
    break;

    } // end switch
  }

  private void notAvailableForward(FlatMethod fm) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Set<TempDescriptor> prev = notAvailableResults.get(fn);

      Set<TempDescriptor> curr = new HashSet<TempDescriptor>();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        Set<TempDescriptor> notAvailIn = notAvailableResults.get(nn);
        if (notAvailIn != null) {
          curr.addAll(notAvailIn);
        }
      }

      FlatSESEEnterNode currentSESE = rblockRel.getLocalInnerRBlock(fn);
      if (currentSESE == null) {
        currentSESE = rblockRel.getCallerProxySESE();
      }

      notAvailable_nodeActions(fn, curr, currentSESE);

      // if a new result, schedule forward nodes for analysis
      if (!curr.equals(prev)) {
        notAvailableResults.put(fn, curr);

        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          flatNodesToVisit.add(nn);
        }
      }
    }
  }

  private void notAvailable_nodeActions(FlatNode fn, Set<TempDescriptor> notAvailSet,
                                        FlatSESEEnterNode currentSESE) {

    // any temps that are removed from the not available set
    // at this node should be marked in this node's code plan
    // as temps to be grabbed at runtime!

    switch (fn.kind()) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;

      // keep a copy of what's not available into the SESE
      // and restore it at the matching exit node
      Set<TempDescriptor> notAvailCopy = new HashSet<TempDescriptor>();
      Iterator<TempDescriptor> tdItr = notAvailSet.iterator();
      while (tdItr.hasNext()) {
        notAvailCopy.add(tdItr.next());
      }
      notAvailableIntoSESE.put(fsen, notAvailCopy);

      notAvailSet.clear();
    }
    break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
      FlatSESEEnterNode fsen = fsexn.getFlatEnter();

      notAvailSet.addAll(fsen.getOutVarSet());

      Set<TempDescriptor> notAvailIn = notAvailableIntoSESE.get(fsen);
      assert notAvailIn != null;
      notAvailSet.addAll(notAvailIn);
    }
    break;

    case FKind.FlatMethod: {
      notAvailSet.clear();
    }
    break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;

      if (fon.getOp().getOp() == Operation.ASSIGN) {
        TempDescriptor lhs = fon.getDest();
        TempDescriptor rhs = fon.getLeft();

        // copy makes lhs same availability as rhs
        if (notAvailSet.contains(rhs)) {
          notAvailSet.add(lhs);
        } else {
          notAvailSet.remove(lhs);
        }

        // only break if this is an ASSIGN op node,
        // otherwise fall through to default case
        break;
      }
    }

    // note that FlatOpNode's that aren't ASSIGN
    // fall through to this default case
    default: {
      TempDescriptor[] writeTemps = fn.writesTemps();
      for (int i = 0; i < writeTemps.length; i++) {
        TempDescriptor wTemp = writeTemps[i];
        notAvailSet.remove(wTemp);
      }
      TempDescriptor[] readTemps = fn.readsTemps();
      for (int i = 0; i < readTemps.length; i++) {
        TempDescriptor rTemp = readTemps[i];
        notAvailSet.remove(rTemp);

        // if this variable has exactly one source, potentially
        // get other things from this source as well
        VarSrcTokTable vstTable = variableResults.get(fn);

        VSTWrapper vstIfStatic = new VSTWrapper();
        Integer srcType = vstTable.getRefVarSrcType(rTemp, currentSESE, vstIfStatic);

        if (srcType.equals(VarSrcTokTable.SrcType_STATIC)) {

          VariableSourceToken vst = vstIfStatic.vst;

          Iterator<VariableSourceToken> availItr =
            vstTable.get(vst.getSESE(), vst.getAge()).iterator();

          // look through things that are also available from same source
          while (availItr.hasNext()) {
            VariableSourceToken vstAlsoAvail = availItr.next();

            Iterator<TempDescriptor> refVarItr = vstAlsoAvail.getRefVars().iterator();
            while (refVarItr.hasNext()) {
              TempDescriptor refVarAlso = refVarItr.next();

              // if a variable is available from the same source, AND it ALSO
              // only comes from one statically known source, mark it available
              VSTWrapper vstIfStaticNotUsed = new VSTWrapper();
              Integer srcTypeAlso =
                vstTable.getRefVarSrcType(refVarAlso, currentSESE, vstIfStaticNotUsed);
              if (srcTypeAlso.equals(VarSrcTokTable.SrcType_STATIC)) {
                notAvailSet.remove(refVarAlso);
              }
            }
          }
        }
      }
    }
    break;

    } // end switch
  }

  private void codePlansForward(FlatMethod fm) {

    // start from flat method top, visit every node in
    // method exactly once
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    Set<FlatNode> visited = new HashSet<FlatNode>();

    while (!flatNodesToVisit.isEmpty()) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      flatNodesToVisit.remove(fn);
      visited.add(fn);

      // use incoming results as "dot statement" or just
      // before the current statement
      VarSrcTokTable dotSTtable = new VarSrcTokTable();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        dotSTtable.merge(variableResults.get(nn));
      }

      // find dt-st notAvailableSet also
      Set<TempDescriptor> dotSTnotAvailSet = new HashSet<TempDescriptor>();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        Set<TempDescriptor> notAvailIn = notAvailableResults.get(nn);
        if (notAvailIn != null) {
          dotSTnotAvailSet.addAll(notAvailIn);
        }
      }

      Set<TempDescriptor> dotSTlive = livenessGlobalView.get(fn);

      FlatSESEEnterNode currentSESE = rblockRel.getLocalInnerRBlock(fn);
      if (currentSESE == null) {
        currentSESE = rblockRel.getCallerProxySESE();
      }

      codePlans_nodeActions(fm, fn, dotSTtable, dotSTnotAvailSet, currentSESE);

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);

        if (!visited.contains(nn)) {
          flatNodesToVisit.add(nn);
        }
      }
    }
  }

  private void codePlans_nodeActions(FlatMethod fm, FlatNode fn, VarSrcTokTable vstTableIn,
                                     Set<TempDescriptor> notAvailSetIn, FlatSESEEnterNode currentSESE) {

    CodePlan plan = new CodePlan(currentSESE);

    switch (fn.kind()) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;

      // track the source types of the in-var set so generated
      // code at this SESE issue can compute the number of
      // dependencies properly
      Iterator<TempDescriptor> inVarItr = fsen.getInVarSet().iterator();
      while (inVarItr.hasNext()) {
        TempDescriptor inVar = inVarItr.next();

        // when we get to an SESE enter node we change the
        // currentSESE variable of this analysis to the
        // child that is declared by the enter node, so
        // in order to classify in-vars correctly, pass
        // the parent SESE in--at other FlatNode types just
        // use the currentSESE
        FlatSESEEnterNode parent = rblockRel.getLocalInnerRBlock(fn);
        if (parent == null) {
          parent = rblockRel.getCallerProxySESE();
        }

        VSTWrapper vstIfStatic = new VSTWrapper();
        Integer srcType = vstTableIn.getRefVarSrcType(inVar, parent, vstIfStatic);

        // the current SESE needs a local space to track the dynamic
        // variable and the child needs space in its SESE record
        if (srcType.equals(VarSrcTokTable.SrcType_DYNAMIC)) {
          fsen.addDynamicInVar(inVar);
          addDynamicVar(parent, fm, inVar);

        } else if (srcType.equals(VarSrcTokTable.SrcType_STATIC)) {
          fsen.addStaticInVar(inVar);
          VariableSourceToken vst = vstIfStatic.vst;
          fsen.putStaticInVar2src(inVar, vst);
          fsen.addStaticInVarSrc(new SESEandAgePair(vst.getSESE(), vst.getAge()));

        } else {
          assert srcType.equals(VarSrcTokTable.SrcType_READY);
          fsen.addReadyInVar(inVar);
        }
      }
    }
    break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;

      // Classify the sources of out-set variables so code
      // gen can acquire them from children if necessary
      // before this task exits
      FlatSESEEnterNode exiter = fsexn.getFlatEnter();

      Iterator<TempDescriptor> outVarItr = exiter.getOutVarSet().iterator();
      while (outVarItr.hasNext()) {
        TempDescriptor outVar = outVarItr.next();

        VSTWrapper vstIfStatic = new VSTWrapper();
        Integer srcType = vstTableIn.getRefVarSrcType(outVar, exiter, vstIfStatic);

        if (srcType.equals(VarSrcTokTable.SrcType_DYNAMIC)) {
          // if the out-var is dynamic, put it in the set of dyn out vars
          // so exiting code gen knows to look for the value, but also put
          // it in the set of dynamic vars the exiter must track!
          exiter.addDynamicOutVar(outVar);
          addDynamicVar(exiter, fm, outVar);

        } else if (srcType.equals(VarSrcTokTable.SrcType_STATIC)) {
          exiter.addStaticOutVar(outVar);
          VariableSourceToken vst = vstIfStatic.vst;
          exiter.putStaticOutVar2src(outVar, vst);
          exiter.addStaticOutVarSrc(new SESEandAgePair(vst.getSESE(), vst.getAge()));

        } else {
          assert srcType.equals(VarSrcTokTable.SrcType_READY);
          exiter.addReadyOutVar(outVar);
        }
      }
    }
    break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;

      if (fon.getOp().getOp() == Operation.ASSIGN) {
        TempDescriptor lhs = fon.getDest();
        TempDescriptor rhs = fon.getLeft();

        // if this is an op node, don't stall, copy
        // source and delay until we need to use value

        // ask whether lhs and rhs sources are dynamic, static, etc.
        VSTWrapper vstIfStatic = new VSTWrapper();
        Integer lhsSrcType = vstTableIn.getRefVarSrcType(lhs, currentSESE, vstIfStatic);
        Integer rhsSrcType = vstTableIn.getRefVarSrcType(rhs, currentSESE, vstIfStatic);

        if (rhsSrcType.equals(VarSrcTokTable.SrcType_DYNAMIC)) {
          // if rhs is dynamic going in, lhs will definitely be dynamic
          // going out of this node, so track that here
          plan.addDynAssign(lhs, rhs);
          addDynamicVar(currentSESE, fm, lhs);
          addDynamicVar(currentSESE, fm, rhs);

        } else if (lhsSrcType.equals(VarSrcTokTable.SrcType_DYNAMIC)) {
          // otherwise, if the lhs is dynamic, but the rhs is not, we
          // need to update the variable's dynamic source as "current SESE"
          plan.addDynAssign(lhs);
        }

        // only break if this is an ASSIGN op node,
        // otherwise fall through to default case
        break;
      }
    }

    // note that FlatOpNode's that aren't ASSIGN
    // fall through to this default case
    default: {

      // a node with no live set has nothing to stall for
      // note: no reason to check here, remove this....
      // if (liveSetIn == null) {
      // break;
      // }

      TempDescriptor[] readarray = fn.readsTemps();
      for (int i = 0; i < readarray.length; i++) {
        TempDescriptor readtmp = readarray[i];

        // ignore temps that are definitely available
        // when considering to stall on it
        if (!notAvailSetIn.contains(readtmp)) {
          continue;
        }

        // check the source type of this variable
        VSTWrapper vstIfStatic = new VSTWrapper();
        Integer srcType = vstTableIn.getRefVarSrcType(readtmp, currentSESE, vstIfStatic);

        if (srcType.equals(VarSrcTokTable.SrcType_DYNAMIC)) {
          // 1) It is not clear statically where this variable will
          // come from, so dynamically we must keep track
          // along various control paths, and therefore when we stall,
          // just stall for the exact thing we need and move on
          plan.addDynamicStall(readtmp);
          addDynamicVar(currentSESE, fm, readtmp);

        } else if (srcType.equals(VarSrcTokTable.SrcType_STATIC)) {
          // 2) Single token/age pair: Stall for token/age pair, and copy
          // all live variables with same token/age pair at the same
          // time. This is the same stuff that the notavaialable analysis
          // marks as now available.
          VariableSourceToken vst = vstIfStatic.vst;

          Iterator<VariableSourceToken> availItr =
            vstTableIn.get(vst.getSESE(), vst.getAge()).iterator();

          while (availItr.hasNext()) {
            VariableSourceToken vstAlsoAvail = availItr.next();

            // only grab additional stuff that is live
            Set<TempDescriptor> copySet = new HashSet<TempDescriptor>();

            Iterator<TempDescriptor> refVarItr = vstAlsoAvail.getRefVars().iterator();

            while (refVarItr.hasNext()) {
              TempDescriptor refVar = refVarItr.next();
              // note: this should just use normal liveness in...only want to
              // copy live variables...
              if (liveness.getLiveInTemps(fm, fn).contains(refVar)) {
                copySet.add(refVar);
              }
            }

            if (!copySet.isEmpty()) {
              plan.addStall2CopySet(vstAlsoAvail, copySet);
            }
          }

        } else {
          // the other case for srcs is READY, so do nothing
        }

        // assert that everything being stalled for is in the
        // "not available" set coming into this flat node and
        // that every VST identified is in the possible "stall set"
        // that represents VST's from children SESE's

      }
    }
    break;

    } // end switch

    // identify sese-age pairs that are statically useful
    // and should have an associated SESE variable in code
    // JUST GET ALL SESE/AGE NAMES FOR NOW, PRUNE LATER,
    // AND ALWAYS GIVE NAMES TO LOCAL PARENTS
    Set<VariableSourceToken> staticSet = vstTableIn.get();
    Iterator<VariableSourceToken> vstItr = staticSet.iterator();
    while (vstItr.hasNext()) {
      VariableSourceToken vst = vstItr.next();

      // the caller proxy generates useful analysis facts, but we
      // never need to generate another name for it in code (it is
      // ALWAYS the task executing the local method context)
      if (vst.getSESE().getIsCallerProxySESE()) {
        continue;
      }

      SESEandAgePair sap = new SESEandAgePair(vst.getSESE(), vst.getAge());
      sap.getSESE().mustTrackAtLeastAge(sap.getAge());

      FlatSESEEnterNode sese = currentSESE;
      while (sese != null) {
        addNeededStaticName(sese, fm, sap);
        sese = sese.getLocalParent();
      }
    }

    codePlans.put(fn, plan);

    // if any variables at this-node-*dot* have a static source (exactly one
    // vst)
    // but go to a dynamic source at next-node-*dot*, create a new IR graph
    // node on that edge to track the sources dynamically
    // NOTE: for this calculation use the currentSESE variable, except when the
    // FlatNode fn is an Exit--in that case currentSESE is for the exiting task,
    // be we want to consider that the parent is tracking a variable coming out
    // of the exiting task
    FlatSESEEnterNode fsenDoingTracking;
    if (fn instanceof FlatSESEExitNode) {
      fsenDoingTracking = currentSESE.getLocalParent();

      if (fsenDoingTracking == null) {
        // if there is no local parent, there are one of two cases
        // 1) the current task is main, in which case this FlatNode
        // is the main's exit, and doesn't need to do any of the
        // following dynamic tracking
        // 2) the current task is defined in a method, so use the
        // caller proxy in the variable source calcs below
        if (currentSESE.equals(rblockRel.getMainSESE())) {
          return;
        } else {
          fsenDoingTracking = rblockRel.getCallerProxySESE();
        }
      }
    } else {
      fsenDoingTracking = currentSESE;
    }

    VarSrcTokTable thisVstTable = variableResults.get(fn);
    for (int i = 0; i < fn.numNext(); i++) {
      FlatNode nn = fn.getNext(i);
      VarSrcTokTable nextVstTable = variableResults.get(nn);
      // note: using the result of liveness analysis regardless of task
      // structures
      Set<TempDescriptor> nextLiveIn = liveness.getLiveInTemps(fm, nn);

      // the table can be null if it is one of the few IR nodes
      // completely outside of the root SESE scope
      if (nextVstTable != null && nextLiveIn != null) {

        Hashtable<TempDescriptor, VSTWrapper> readyOrStatic2dynamicSet =
          thisVstTable.getReadyOrStatic2DynamicSet(nextVstTable, nextLiveIn, fsenDoingTracking);

        if (!readyOrStatic2dynamicSet.isEmpty()) {

          // either add these results to partial fixed-point result
          // or make a new one if we haven't made any here yet
          FlatEdge fe = new FlatEdge(fn, nn);
          FlatWriteDynamicVarNode fwdvn = wdvNodesToSpliceIn.get(fe);

          if (fwdvn == null) {
            fwdvn =
              new FlatWriteDynamicVarNode(fn, nn, readyOrStatic2dynamicSet, fsenDoingTracking);
            wdvNodesToSpliceIn.put(fe, fwdvn);
          } else {
            fwdvn.addMoreVar2Src(readyOrStatic2dynamicSet);
          }
        }
      }
    }
  }

  private void addDynamicVar(FlatSESEEnterNode fsen, FlatMethod fm, TempDescriptor var) {
    FlatNode fnContext;

    // note: dynamic variable declarations are always located in the flat method
    // that encloses task block
    // there is no need to set fnContext to fsen
    if (fsen.getIsCallerProxySESE()) {
      // attach the dynamic variable to track to
      // the flat method, so it can be declared at entry
      fnContext = fm;
    } else {
      // otherwise the code context is a task body
      fnContext = fsen;
    }
    // fnContext=fm;

    ContextTaskNames ctn = fn2contextTaskNames.get(fnContext);
    if (ctn == null) {
      ctn = new ContextTaskNames();
    }

    ctn.addDynamicVar(var);
    fn2contextTaskNames.put(fnContext, ctn);

  }

  private void addNeededStaticName(FlatSESEEnterNode fsen, FlatMethod fm, SESEandAgePair sap) {
    FlatNode fnContext;

    if (fsen.getIsCallerProxySESE()) {
      // attach the dynamic variable to track to
      // the flat method, so it can be declared at entry
      fnContext = fm;
    } else {
      // otherwise the code context is a task body
      fnContext = fsen;
    }

    ContextTaskNames ctn = fn2contextTaskNames.get(fnContext);
    if (ctn == null) {
      ctn = new ContextTaskNames();
    }

    ctn.addNeededStaticName(sap);

    fn2contextTaskNames.put(fnContext, ctn);
  }

  private void startConflictGraphs() {

    // first, for each task, consider whether it has any children, and if
    // effects analysis says they should be a conflict node in the that
    // parent's conflict graph
    Set<FlatSESEEnterNode> allSESEs = rblockRel.getAllSESEs();
    for (Iterator iterator = allSESEs.iterator(); iterator.hasNext(); ) {

      FlatSESEEnterNode parent = (FlatSESEEnterNode) iterator.next();
      if (parent.getIsLeafSESE()) {
        continue;
      }

      EffectsAnalysis effectsAnalysis = disjointAnalysisTaints.getEffectsAnalysis();
      ConflictGraph conflictGraph = sese2conflictGraph.get(parent);
      assert conflictGraph == null;
      conflictGraph = new ConflictGraph(state);

      Set<FlatSESEEnterNode> children = parent.getChildren();
      for (Iterator iterator2 = children.iterator(); iterator2.hasNext(); ) {
        FlatSESEEnterNode child = (FlatSESEEnterNode) iterator2.next();
        Hashtable<Taint, Set<Effect>> taint2Effects = effectsAnalysis.get(child);
        conflictGraph.addLiveIn(taint2Effects);
      }

      sese2conflictGraph.put(parent, conflictGraph);
    }

    // then traverse all methods looking for potential stall sites, and
    // add those stall sites as nodes in any task's conflict graph that
    // might be executing at the point of the stall site
    Iterator<MethodDescriptor> descItr = descriptorsToAnalyze.iterator();
    while (descItr.hasNext()) {
      MethodDescriptor md = descItr.next();
      FlatMethod fm = state.getMethodFlat(md);
      if (fm != null) {
        addStallSitesToConflictGraphs(fm);
      }
    }
  }

  private void addStallSitesToConflictGraphs(FlatMethod fm) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    Set<FlatNode> visited = new HashSet<FlatNode>();

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);
      visited.add(fn);

      Set<FlatSESEEnterNode> currentSESEs = rblockRel.getPossibleExecutingRBlocks(fn);

      conflictGraph_nodeAction(fn, currentSESEs, fm.getMethod().getClassDesc());

      // schedule forward nodes for analysis
      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);
        if (!visited.contains(nn)) {
          flatNodesToVisit.add(nn);
        }
      }
    }
  }

  private void conflictGraph_nodeAction(FlatNode fn, Set<FlatSESEEnterNode> currentSESEs,
                                        ClassDescriptor cd) {

    EffectsAnalysis effectsAnalysis = disjointAnalysisTaints.getEffectsAnalysis();

    Hashtable<Taint, Set<Effect>> taint2Effects = effectsAnalysis.get(fn);

    // repeat the process of adding a stall site to a conflict graph
    // for each task that might be executing at a possible stall site
    Iterator<FlatSESEEnterNode> seseItr = currentSESEs.iterator();
    while (seseItr.hasNext()) {
      FlatSESEEnterNode currentSESE = seseItr.next();

      ConflictGraph conflictGraph = sese2conflictGraph.get(currentSESE);
      if (conflictGraph == null) {
        assert currentSESE.getIsLeafSESE();
        continue;
      }

      TempDescriptor lhs;
      TempDescriptor rhs;

      switch (fn.kind()) {

      case FKind.FlatFieldNode:
      case FKind.FlatElementNode: {

        if (fn instanceof FlatFieldNode) {
          FlatFieldNode ffn = (FlatFieldNode) fn;
          rhs = ffn.getSrc();
        } else {
          FlatElementNode fen = (FlatElementNode) fn;
          rhs = fen.getSrc();
        }

        conflictGraph.addStallSite(taint2Effects, rhs, cd);
      }
      break;

      case FKind.FlatSetFieldNode:
      case FKind.FlatSetElementNode: {

        if (fn instanceof FlatSetFieldNode) {
          FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
          lhs = fsfn.getDst();
          rhs = fsfn.getSrc();
        } else {
          FlatSetElementNode fsen = (FlatSetElementNode) fn;
          lhs = fsen.getDst();
          rhs = fsen.getSrc();
        }

        conflictGraph.addStallSite(taint2Effects, rhs, cd);
        conflictGraph.addStallSite(taint2Effects, lhs, cd);
      }
      break;

      case FKind.FlatCall: {
        FlatCall fc = (FlatCall) fn;
        lhs = fc.getThis();

        conflictGraph.addStallSite(taint2Effects, lhs, cd);
      }
      break;

      }

      if (conflictGraph.id2cn.size() > 0) {
        sese2conflictGraph.put(currentSESE, conflictGraph);
      }
    }
  }

  private void calculateConflicts(Set<FlatNew> sitesToFlag, boolean useReachInfo) {

    // decide fine-grain edge or coarse-grain edge among all vertexes by
    // pair-wise comparison
    Iterator<FlatNode> seseIter = sese2conflictGraph.keySet().iterator();
    while (seseIter.hasNext()) {
      FlatSESEEnterNode sese = (FlatSESEEnterNode) seseIter.next();
      ConflictGraph conflictGraph = sese2conflictGraph.get(sese);

      if (useReachInfo) {
        // clear current conflict before recalculating with reachability info
        conflictGraph.clearAllConflictEdge();
        conflictGraph.setDisJointAnalysis(disjointAnalysisReach);
        conflictGraph.setFMEnclosing(sese.getfmEnclosing());
      }
      conflictGraph.analyzeConflicts(sitesToFlag, useReachInfo);
      sese2conflictGraph.put(sese, conflictGraph);
    }
  }

  private void writeConflictGraph() {
    Enumeration<FlatNode> keyEnum = sese2conflictGraph.keys();
    while (keyEnum.hasMoreElements()) {
      FlatNode key = (FlatNode) keyEnum.nextElement();
      ConflictGraph cg = sese2conflictGraph.get(key);
      try {
        if (cg.hasConflictEdge()) {
          cg.writeGraph("ConflictGraphFor" + key, false);
        }
      } catch (IOException e) {
        System.out.println("Error writing");
        System.exit(0);
      }
    }
  }

  // the traversal for pruning state machines and finding
  // machines that are weakly connected BOTH consider conflicting
  // effects between heap roots, so it is smart to compute all of
  // this together
  public void pruneMachinesAndFindWeaklyConnectedExaminers() {
    ProcessStateMachines psm = new ProcessStateMachines(buildStateMachines, rblockRel);
    psm.doProcess();
    buildStateMachines.writeStateMachines("pruned");
  }

  private void synthesizeLocks() {
    // for every conflict graph, generate a set of memory queues
    // (called SESELock in this code!) to cover the graph
    Set<Map.Entry<FlatNode, ConflictGraph>> graphEntrySet = sese2conflictGraph.entrySet();
    for (Iterator iterator = graphEntrySet.iterator(); iterator.hasNext(); ) {
      Map.Entry<FlatNode, ConflictGraph> graphEntry =
        (Map.Entry<FlatNode, ConflictGraph>)iterator.next();
      FlatNode sese = graphEntry.getKey();
      ConflictGraph conflictGraph = graphEntry.getValue();
      calculateCovering(conflictGraph);
    }
  }

  private void calculateCovering(ConflictGraph conflictGraph) {
    uniqueLockSetId = 0; // reset lock counter for every new conflict graph
    HashSet<ConflictEdge> fineToCover = new HashSet<ConflictEdge>();
    HashSet<ConflictEdge> coarseToCover = new HashSet<ConflictEdge>();
    HashSet<SESELock> lockSet = new HashSet<SESELock>();

    Set<ConflictEdge> tempCover = conflictGraph.getEdgeSet();
    for (Iterator iterator = tempCover.iterator(); iterator.hasNext(); ) {
      ConflictEdge conflictEdge = (ConflictEdge) iterator.next();
      if (conflictEdge.isCoarseEdge()) {
        coarseToCover.add(conflictEdge);
      } else {
        fineToCover.add(conflictEdge);
      }
    }

    HashSet<ConflictEdge> toCover = new HashSet<ConflictEdge>();
    toCover.addAll(fineToCover);
    toCover.addAll(coarseToCover);

    while (!toCover.isEmpty()) {

      SESELock seseLock = new SESELock();
      seseLock.setID(uniqueLockSetId++);

      boolean changed;

      do { // fine-grained edge

        changed = false;

        for (Iterator iterator = fineToCover.iterator(); iterator.hasNext(); ) {

          int type;
          ConflictEdge edge = (ConflictEdge) iterator.next();
          if (seseLock.getConflictNodeSet().size() == 0) {
            // initial setup
            if (seseLock.isWriteNode(edge.getVertexU())) {
              // mark as fine_write
              if (edge.getVertexU().isStallSiteNode()) {
                type = ConflictNode.PARENT_WRITE;
              } else {
                type = ConflictNode.FINE_WRITE;
              }
              seseLock.addConflictNode(edge.getVertexU(), type);
            } else {
              // mark as fine_read
              if (edge.getVertexU().isStallSiteNode()) {
                type = ConflictNode.PARENT_READ;
              } else {
                type = ConflictNode.FINE_READ;
              }
              seseLock.addConflictNode(edge.getVertexU(), type);
            }
            if (edge.getVertexV() != edge.getVertexU()) {
              if (seseLock.isWriteNode(edge.getVertexV())) {
                // mark as fine_write
                if (edge.getVertexV().isStallSiteNode()) {
                  type = ConflictNode.PARENT_WRITE;
                } else {
                  type = ConflictNode.FINE_WRITE;
                }
                seseLock.addConflictNode(edge.getVertexV(), type);
              } else {
                // mark as fine_read
                if (edge.getVertexV().isStallSiteNode()) {
                  type = ConflictNode.PARENT_READ;
                } else {
                  type = ConflictNode.FINE_READ;
                }
                seseLock.addConflictNode(edge.getVertexV(), type);
              }
            }
            changed = true;
            seseLock.addConflictEdge(edge);
            fineToCover.remove(edge);
            break; // exit iterator loop
          } // end of initial setup

          ConflictNode newNode;
          if ((newNode = seseLock.getNewNodeConnectedWithGroup(edge)) != null) {
            // new node has a fine-grained edge to all current node
            // If there is a coarse grained edge where need a fine edge, it's
            // okay to add the node
            // but the edge must remain uncovered.

            changed = true;

            if (seseLock.containsConflictNode(newNode)) {
              seseLock.addEdge(edge);
              fineToCover.remove(edge);
              break;
            }

            if (seseLock.isWriteNode(newNode)) {
              if (newNode.isStallSiteNode()) {
                type = ConflictNode.PARENT_WRITE;
              } else {
                type = ConflictNode.FINE_WRITE;
              }
              seseLock.setNodeType(newNode, type);
            } else {
              if (newNode.isStallSiteNode()) {
                type = ConflictNode.PARENT_READ;
              } else {
                type = ConflictNode.FINE_READ;
              }
              seseLock.setNodeType(newNode, type);
            }

            seseLock.addEdge(edge);
            Set<ConflictEdge> edgeSet = newNode.getEdgeSet();
            for (Iterator iterator2 = edgeSet.iterator(); iterator2.hasNext(); ) {
              ConflictEdge conflictEdge = (ConflictEdge) iterator2.next();

              // mark all fine edges between new node and nodes in the group as
              // covered
              if (!conflictEdge.getVertexU().equals(newNode)) {
                if (seseLock.containsConflictNode(conflictEdge.getVertexU())) {
                  changed = true;
                  seseLock.addConflictEdge(conflictEdge);
                  fineToCover.remove(conflictEdge);
                }
              } else if (!conflictEdge.getVertexV().equals(newNode)) {
                if (seseLock.containsConflictNode(conflictEdge.getVertexV())) {
                  changed = true;
                  seseLock.addConflictEdge(conflictEdge);
                  fineToCover.remove(conflictEdge);
                }
              }

            }

            break; // exit iterator loop
          }
        }

      } while (changed);
      HashSet<ConflictEdge> notCovered = new HashSet<ConflictEdge>();
      do { // coarse
        changed = false;
        int type;
        for (Iterator iterator = coarseToCover.iterator(); iterator.hasNext(); ) {

          ConflictEdge edge = (ConflictEdge) iterator.next();
          if (seseLock.getConflictNodeSet().size() == 0) {
            // initial setup
            if (seseLock.hasSelfCoarseEdge(edge.getVertexU())) {
              // node has a coarse-grained edge with itself
              if (!(edge.getVertexU().isStallSiteNode())) {
                // and it is not parent
                type = ConflictNode.SCC;
              } else {
                if (state.RCR) {
                  type = ConflictNode.PARENT_COARSE;
                } else {
                  type = ConflictNode.PARENT_WRITE;
                }
              }
              seseLock.addConflictNode(edge.getVertexU(), type);
            } else {
              if (edge.getVertexU().isStallSiteNode()) {
                if (state.RCR) {
                  type = ConflictNode.PARENT_COARSE;
                } else {
                  if (edge.getVertexU().getWriteEffectSet().isEmpty()) {
                    type = ConflictNode.PARENT_READ;
                  } else {
                    type = ConflictNode.PARENT_WRITE;
                  }
                }
              } else {
                type = ConflictNode.COARSE;
              }
              seseLock.addConflictNode(edge.getVertexU(), type);
            }
            if (seseLock.hasSelfCoarseEdge(edge.getVertexV())) {
              // node has a coarse-grained edge with itself
              if (!(edge.getVertexV().isStallSiteNode())) {
                // and it is not parent
                type = ConflictNode.SCC;
              } else {
                if (state.RCR) {
                  type = ConflictNode.PARENT_COARSE;
                } else {
                  type = ConflictNode.PARENT_WRITE;
                }
              }
              seseLock.addConflictNode(edge.getVertexV(), type);
            } else {
              if (edge.getVertexV().isStallSiteNode()) {
                if (state.RCR) {
                  type = ConflictNode.PARENT_COARSE;
                } else {
                  if (edge.getVertexV().getWriteEffectSet().isEmpty()) {
                    type = ConflictNode.PARENT_READ;
                  } else {
                    type = ConflictNode.PARENT_WRITE;
                  }
                }
              } else {
                type = ConflictNode.COARSE;
              }
              seseLock.addConflictNode(edge.getVertexV(), type);
            }
            changed = true;
            coarseToCover.remove(edge);
            seseLock.addConflictEdge(edge);
            break; // exit iterator loop
          } // end of initial setup

          ConflictNode newNode;
          if ((newNode = seseLock.getNewNodeConnectedWithGroup(edge)) != null) {
            // new node has a coarse-grained edge to all fine-read, fine-write,
            // parent
            changed = true;

            if (newNode.isInVarNode() && (!seseLock.hasSelfCoarseEdge(newNode))
                && seseLock.hasCoarseEdgeWithParentCoarse(newNode)) {
              // this case can't be covered by this queue
              coarseToCover.remove(edge);
              notCovered.add(edge);
              break;
            }

            if (seseLock.containsConflictNode(newNode)) {
              seseLock.addEdge(edge);
              coarseToCover.remove(edge);
              break;
            }

            if (seseLock.hasSelfCoarseEdge(newNode)) {
              // SCC
              if (newNode.isStallSiteNode()) {
                type = ConflictNode.PARENT_COARSE;
              } else {
                type = ConflictNode.SCC;
              }
              seseLock.setNodeType(newNode, type);
            } else {
              if (newNode.isStallSiteNode()) {
                type = ConflictNode.PARENT_COARSE;
              } else {
                type = ConflictNode.COARSE;
              }
              seseLock.setNodeType(newNode, type);
            }

            seseLock.addEdge(edge);
            Set<ConflictEdge> edgeSet = newNode.getEdgeSet();
            for (Iterator iterator2 = edgeSet.iterator(); iterator2.hasNext(); ) {
              ConflictEdge conflictEdge = (ConflictEdge) iterator2.next();
              // mark all coarse edges between new node and nodes in the group
              // as covered
              if (!conflictEdge.getVertexU().equals(newNode)) {
                if (seseLock.containsConflictNode(conflictEdge.getVertexU())) {
                  changed = true;
                  seseLock.addConflictEdge(conflictEdge);
                  coarseToCover.remove(conflictEdge);
                }
              } else if (!conflictEdge.getVertexV().equals(newNode)) {
                if (seseLock.containsConflictNode(conflictEdge.getVertexV())) {
                  changed = true;
                  seseLock.addConflictEdge(conflictEdge);
                  coarseToCover.remove(conflictEdge);
                }
              }

            }
            break; // exit iterator loop
          }

        }

      } while (changed);
      lockSet.add(seseLock);

      toCover.clear();
      coarseToCover.addAll(notCovered);
      toCover.addAll(fineToCover);
      toCover.addAll(coarseToCover);

    }

    conflictGraph2SESELock.put(conflictGraph, lockSet);
  }

  public ConflictGraph getConflictGraph(FlatNode sese) {
    return sese2conflictGraph.get(sese);
  }

  public Set<SESELock> getLockMappings(ConflictGraph graph) {
    return conflictGraph2SESELock.get(graph);
  }

  public Set<FlatSESEEnterNode> getAllSESEs() {
    return rblockRel.getAllSESEs();
  }

  public FlatSESEEnterNode getMainSESE() {
    return rblockRel.getMainSESE();
  }

  public FlatSESEEnterNode getCallerProxySESE() {
    return rblockRel.getCallerProxySESE();
  }

  public Set<FlatSESEEnterNode> getPossibleExecutingRBlocks(FlatNode fn) {
    return rblockRel.getPossibleExecutingRBlocks(fn);
  }

  public void writeReports(String timeReport) throws java.io.IOException {

    BufferedWriter bw = new BufferedWriter(new FileWriter("ooojReport_summary.txt"));
    bw.write("OoOJava Analysis Results\n\n");
    bw.write(timeReport + "\n\n");
    printSESEHierarchy(bw);
    bw.write("\n");
    printSESEInfo(bw);
    bw.close();

    Iterator<MethodDescriptor> methItr = descriptorsToAnalyze.iterator();
    while (methItr.hasNext()) {
      MethodDescriptor md = methItr.next();
      FlatMethod fm = state.getMethodFlat(md);
      if (fm != null) {
        bw =
          new BufferedWriter(new FileWriter("ooojReport_" + md.getClassMethodName()
                                            + md.getSafeMethodDescriptor() + ".txt"));
        bw.write("OoOJava Results for " + md + "\n-------------------\n");

        bw.write("Dynamic vars to manage:\n  " + getContextTaskNames(fm).getDynamicVarSet());

        bw.write("\n\nLive-In, Root View\n------------------\n"
                 + fm.printMethod(livenessGlobalView));
        bw.write("\n\nVariable Results-Out\n----------------\n" + fm.printMethod(variableResults));
        bw.write("\n\nNot Available Results-Out\n---------------------\n"
                 + fm.printMethod(notAvailableResults));
        bw.write("\n\nCode Plans\n----------\n" + fm.printMethod(codePlans));
        bw.close();
      }
    }
  }

  private void printSESEHierarchy(BufferedWriter bw) throws java.io.IOException {
    bw.write("SESE Local Hierarchy\n--------------\n");
    Iterator<FlatSESEEnterNode> rootItr = rblockRel.getLocalRootSESEs().iterator();
    while (rootItr.hasNext()) {
      FlatSESEEnterNode root = rootItr.next();
      printSESEHierarchyTree(bw, root, 0);
    }
  }

  private void printSESEHierarchyTree(BufferedWriter bw, FlatSESEEnterNode fsen, int depth)
  throws java.io.IOException {
    for (int i = 0; i < depth; ++i) {
      bw.write("  ");
    }
    bw.write("- " + fsen.getPrettyIdentifier() + "\n");

    Iterator<FlatSESEEnterNode> childItr = fsen.getLocalChildren().iterator();
    while (childItr.hasNext()) {
      FlatSESEEnterNode fsenChild = childItr.next();
      printSESEHierarchyTree(bw, fsenChild, depth + 1);
    }
  }

  private void printSESEInfo(BufferedWriter bw) throws java.io.IOException {
    bw.write("\nSESE info\n-------------\n");
    Iterator<FlatSESEEnterNode> fsenItr = rblockRel.getAllSESEs().iterator();
    while (fsenItr.hasNext()) {
      FlatSESEEnterNode fsen = fsenItr.next();

      bw.write("SESE " + fsen.getPrettyIdentifier());
      if (fsen.getIsLeafSESE()) {
        bw.write(" (leaf)");
      }
      bw.write(" {\n");

      bw.write("  in-set: " + fsen.getInVarSet() + "\n");
      Iterator<TempDescriptor> tItr = fsen.getInVarSet().iterator();
      while (tItr.hasNext()) {
        TempDescriptor inVar = tItr.next();
        if (fsen.getReadyInVarSet().contains(inVar)) {
          bw.write("    (ready)  " + inVar + "\n");
        }
        if (fsen.getStaticInVarSet().contains(inVar)) {
          bw.write("    (static) " + inVar + " from " + fsen.getStaticInVarSrc(inVar) + "\n");
        }
        if (fsen.getDynamicInVarSet().contains(inVar)) {
          bw.write("    (dynamic)" + inVar + "\n");
        }
      }

      bw.write("   Dynamic vars to manage: " + getContextTaskNames(fsen).getDynamicVarSet() + "\n");

      bw.write("  out-set: " + fsen.getOutVarSet() + "\n");
      tItr = fsen.getOutVarSet().iterator();
      while (tItr.hasNext()) {
        TempDescriptor outVar = tItr.next();
        if (fsen.getReadyOutVarSet().contains(outVar)) {
          bw.write("    (ready)  " + outVar + "\n");
        }
        if (fsen.getStaticOutVarSet().contains(outVar)) {
          bw.write("    (static) " + outVar + " from " + fsen.getStaticOutVarSrc(outVar) + "\n");
        }
        if (fsen.getDynamicOutVarSet().contains(outVar)) {
          bw.write("    (dynamic)" + outVar + "\n");
        }
      }

      bw.write("  local parent:   " + fsen.getLocalParent() + "\n");
      bw.write("  local children: " + fsen.getLocalChildren() + "\n");

      bw.write("  possible parents:  " + fsen.getParents() + "\n");
      bw.write("  possible children: " + fsen.getChildren() + "\n");

      bw.write("}\n");
    }
  }
}
