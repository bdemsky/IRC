package Analysis.OoOJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

import Analysis.ArrayReferencees;
import Analysis.Liveness;
import Analysis.CallGraph.CallGraph;
import Analysis.Disjoint.DisjointAnalysis;
import Analysis.OwnershipAnalysis.AllocationSite;
import Analysis.OwnershipAnalysis.MethodContext;
import IR.Descriptor;
import IR.Operation;
import IR.State;
import IR.TypeUtil;
import IR.Flat.FKind;
import IR.Flat.FlatEdge;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.FlatReturnNode;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.FlatSESEExitNode;
import IR.Flat.FlatWriteDynamicVarNode;
import IR.Flat.TempDescriptor;

public class OoOJavaAnalysis {

  // data from the compiler
  private State state;
  private TypeUtil typeUtil;
  private CallGraph callGraph;
  private DisjointAnalysis disjointAnalysis;

  // an implicit SESE is automatically spliced into
  // the IR graph around the C main before this analysis--it
  // is nothing special except that we can make assumptions
  // about it, such as the whole program ends when it ends
  private FlatSESEEnterNode mainSESE;

  // SESEs that are the root of an SESE tree belong to this
  // set--the main SESE is always a root, statically SESEs
  // inside methods are a root because we don't know how they
  // will fit into the runtime tree of SESEs
  private Set<FlatSESEEnterNode> rootSESEs;

  // simply a set of every reachable SESE in the program, not
  // including caller placeholder SESEs
  private Set<FlatSESEEnterNode> allSESEs;

  // A mapping of flat nodes to the stack of SESEs for that node, where
  // an SESE is the child of the SESE directly below it on the stack.
  // These stacks do not reflect the heirarchy over methods calls--whenever
  // there is an empty stack it means all variables are available.
  private Hashtable<FlatNode, Stack<FlatSESEEnterNode>> seseStacks;

  private Hashtable<FlatNode, Set<TempDescriptor>> livenessRootView;
  private Hashtable<FlatNode, Set<TempDescriptor>> livenessVirtualReads;
  private Hashtable<FlatNode, VarSrcTokTable> variableResults;
  private Hashtable<FlatNode, Set<TempDescriptor>> notAvailableResults;
  private Hashtable<FlatNode, CodePlan> codePlans;

  private Hashtable<FlatSESEEnterNode, Set<TempDescriptor>> notAvailableIntoSESE;

  private Hashtable<FlatEdge, FlatWriteDynamicVarNode> wdvNodesToSpliceIn;

  private Hashtable<MethodContext, HashSet<AllocationSite>> mapMethodContextToLiveInAllocationSiteSet;

//  private Hashtable<FlatNode, ParentChildConflictsMap> conflictsResults;
//  private Hashtable<FlatMethod, MethodSummary> methodSummaryResults;
//  private OwnershipAnalysis ownAnalysisForSESEConflicts;
//  private Hashtable<FlatNode, ConflictGraph> conflictGraphResults;

  // temporal data structures to track analysis progress.
  static private int uniqueLockSetId = 0;

  public static int maxSESEage = -1;

  // use these methods in BuildCode to have access to analysis results
  public FlatSESEEnterNode getMainSESE() {
    return mainSESE;
  }

  public Set<FlatSESEEnterNode> getRootSESEs() {
    return rootSESEs;
  }

  public Set<FlatSESEEnterNode> getAllSESEs() {
    return allSESEs;
  }

  public int getMaxSESEage() {
    return maxSESEage;
  }

  // may be null
  public CodePlan getCodePlan(FlatNode fn) {
    CodePlan cp = codePlans.get(fn);
    return cp;
  }

  public OoOJavaAnalysis(State state, TypeUtil tu, CallGraph callGraph,
      DisjointAnalysis disjointAnalysis, Liveness liveness, ArrayReferencees arrayReferencees) {

    double timeStartAnalysis = (double) System.nanoTime();

    this.state = state;
    this.typeUtil = tu;
    this.callGraph = callGraph;
    this.disjointAnalysis = disjointAnalysis;
    this.maxSESEage = state.MLP_MAXSESEAGE;

    rootSESEs = new HashSet<FlatSESEEnterNode>();
    allSESEs = new HashSet<FlatSESEEnterNode>();

    seseStacks = new Hashtable<FlatNode, Stack<FlatSESEEnterNode>>();
    livenessRootView = new Hashtable<FlatNode, Set<TempDescriptor>>();
    livenessVirtualReads = new Hashtable<FlatNode, Set<TempDescriptor>>();
    variableResults = new Hashtable<FlatNode, VarSrcTokTable>();
    notAvailableResults = new Hashtable<FlatNode, Set<TempDescriptor>>();
    codePlans = new Hashtable<FlatNode, CodePlan>();
    wdvNodesToSpliceIn = new Hashtable<FlatEdge, FlatWriteDynamicVarNode>();

    notAvailableIntoSESE = new Hashtable<FlatSESEEnterNode, Set<TempDescriptor>>();

    mapMethodContextToLiveInAllocationSiteSet = new Hashtable<MethodContext, HashSet<AllocationSite>>();

//    conflictsResults = new Hashtable<FlatNode, ParentChildConflictsMap>();
//    methodSummaryResults = new Hashtable<FlatMethod, MethodSummary>();
//    conflictGraphResults = new Hashtable<FlatNode, ConflictGraph>();

    // seseSummaryMap = new Hashtable<FlatNode, SESESummary>();
    // isAfterChildSESEIndicatorMap = new Hashtable<FlatNode, Boolean>();
    // conflictGraphLockMap = new Hashtable<ConflictGraph, HashSet<SESELock>>();

    FlatMethod fmMain = state.getMethodFlat(typeUtil.getMain());

    mainSESE = (FlatSESEEnterNode) fmMain.getNext(0);
    mainSESE.setfmEnclosing(fmMain);
    mainSESE.setmdEnclosing(fmMain.getMethod());
    mainSESE.setcdEnclosing(fmMain.getMethod().getClassDesc());

    // 1st pass
    // run analysis on each method that is actually called
    // reachability analysis already computed this so reuse
    Iterator<Descriptor> methItr = disjointAnalysis.getDescriptorsToAnalyze().iterator();
    while (methItr.hasNext()) {
      Descriptor d = methItr.next();
      FlatMethod fm = state.getMethodFlat(d);

      // find every SESE from methods that may be called
      // and organize them into roots and children
      buildForestForward(fm);
    }

    // 2nd pass, results are saved in FlatSESEEnterNode, so
    // intermediate results, for safety, are discarded
    Iterator<FlatSESEEnterNode> rootItr = rootSESEs.iterator();
    while (rootItr.hasNext()) {
      FlatSESEEnterNode root = rootItr.next();
      livenessAnalysisBackward(root, true, null);
    }

    // 3rd pass
    methItr = disjointAnalysis.getDescriptorsToAnalyze().iterator();
    while (methItr.hasNext()) {
      Descriptor d = methItr.next();
      FlatMethod fm = state.getMethodFlat(d);

      // starting from roots do a forward, fixed-point
      // variable analysis for refinement and stalls
      variableAnalysisForward(fm);
    }

    // 4th pass, compute liveness contribution from
    // virtual reads discovered in variable pass
    rootItr = rootSESEs.iterator();
    while (rootItr.hasNext()) {
      FlatSESEEnterNode root = rootItr.next();
      livenessAnalysisBackward(root, true, null);
    }

    /*
     * SOMETHING IS WRONG WITH THIS, DON'T USE IT UNTIL IT CAN BE FIXED
     * 
     * // 5th pass methItr = ownAnalysis.descriptorsToAnalyze.iterator(); while(
     * methItr.hasNext() ) { Descriptor d = methItr.next(); FlatMethod fm =
     * state.getMethodFlat( d );
     * 
     * // prune variable results in one traversal // by removing reference
     * variables that are not live pruneVariableResultsWithLiveness( fm ); }
     */

    // 6th pass
    methItr = disjointAnalysis.getDescriptorsToAnalyze().iterator();
    while (methItr.hasNext()) {
      Descriptor d = methItr.next();
      FlatMethod fm = state.getMethodFlat(d);

      // compute what is not available at every program
      // point, in a forward fixed-point pass
      notAvailableForward(fm);
    }

  }

  private void buildForestForward(FlatMethod fm) {

    // start from flat method top, visit every node in
    // method exactly once, find SESEs and remember
    // roots and child relationships
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    Set<FlatNode> visited = new HashSet<FlatNode>();

    Stack<FlatSESEEnterNode> seseStackFirst = new Stack<FlatSESEEnterNode>();
    seseStacks.put(fm, seseStackFirst);

    while (!flatNodesToVisit.isEmpty()) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get(fn);
      assert seseStack != null;

      flatNodesToVisit.remove(fn);
      visited.add(fn);

      buildForest_nodeActions(fn, seseStack, fm);

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);

        if (!visited.contains(nn)) {
          flatNodesToVisit.add(nn);

          // clone stack and send along each analysis path
          seseStacks.put(nn, (Stack<FlatSESEEnterNode>) seseStack.clone());
        }
      }
    }
  }

  private void buildForest_nodeActions(FlatNode fn, Stack<FlatSESEEnterNode> seseStack,
      FlatMethod fm) {
    switch (fn.kind()) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;

      if (!fsen.getIsCallerSESEplaceholder()) {
        allSESEs.add(fsen);
      }

      fsen.setfmEnclosing(fm);
      fsen.setmdEnclosing(fm.getMethod());
      fsen.setcdEnclosing(fm.getMethod().getClassDesc());

      if (seseStack.empty()) {
        rootSESEs.add(fsen);
        fsen.setParent(null);
      } else {
        seseStack.peek().addChild(fsen);
        fsen.setParent(seseStack.peek());
      }

      seseStack.push(fsen);
    }
      break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
      assert !seseStack.empty();
      FlatSESEEnterNode fsen = seseStack.pop();
    }
      break;

    case FKind.FlatReturnNode: {
      FlatReturnNode frn = (FlatReturnNode) fn;
      if (!seseStack.empty() && !seseStack.peek().getIsCallerSESEplaceholder()) {
        throw new Error("Error: return statement enclosed within SESE "
            + seseStack.peek().getPrettyIdentifier());
      }
    }
      break;

    }
  }

  private void livenessAnalysisBackward(FlatSESEEnterNode fsen, boolean toplevel,
      Hashtable<FlatSESEExitNode, Set<TempDescriptor>> liveout) {

    // start from an SESE exit, visit nodes in reverse up to
    // SESE enter in a fixed-point scheme, where children SESEs
    // should already be analyzed and therefore can be skipped
    // because child SESE enter node has all necessary info
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();

    if (toplevel) {
      flatNodesToVisit.add(fsen.getfmEnclosing().getFlatExit());
    } else {
      flatNodesToVisit.add(fsen.getFlatExit());
    }

    Hashtable<FlatNode, Set<TempDescriptor>> livenessResults = new Hashtable<FlatNode, Set<TempDescriptor>>();

    if (toplevel) {
      liveout = new Hashtable<FlatSESEExitNode, Set<TempDescriptor>>();
    }

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Set<TempDescriptor> prev = livenessResults.get(fn);

      // merge sets from control flow joins
      Set<TempDescriptor> u = new HashSet<TempDescriptor>();
      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);
        Set<TempDescriptor> s = livenessResults.get(nn);
        if (s != null) {
          u.addAll(s);
        }
      }

      Set<TempDescriptor> curr = liveness_nodeActions(fn, u, fsen, toplevel, liveout);

      // if a new result, schedule backward nodes for analysis
      if (!curr.equals(prev)) {
        livenessResults.put(fn, curr);

        // don't flow backwards past current SESE enter
        if (!fn.equals(fsen)) {
          for (int i = 0; i < fn.numPrev(); i++) {
            FlatNode nn = fn.getPrev(i);
            flatNodesToVisit.add(nn);
          }
        }
      }
    }

    Set<TempDescriptor> s = livenessResults.get(fsen);
    if (s != null) {
      fsen.addInVarSet(s);
    }

    // remember liveness per node from the root view as the
    // global liveness of variables for later passes to use
    if (toplevel) {
      livenessRootView.putAll(livenessResults);
    }

    // post-order traversal, so do children first
    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while (childItr.hasNext()) {
      FlatSESEEnterNode fsenChild = childItr.next();
      livenessAnalysisBackward(fsenChild, false, liveout);
    }
  }

  private Set<TempDescriptor> liveness_nodeActions(FlatNode fn, Set<TempDescriptor> liveIn,
      FlatSESEEnterNode currentSESE, boolean toplevel,
      Hashtable<FlatSESEExitNode, Set<TempDescriptor>> liveout) {
    switch (fn.kind()) {

    case FKind.FlatSESEExitNode:
      if (toplevel) {
        FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
        if (!liveout.containsKey(fsexn)) {
          liveout.put(fsexn, new HashSet<TempDescriptor>());
        }
        liveout.get(fsexn).addAll(liveIn);
      }
      // no break, sese exits should also execute default actions

    default: {
      // handle effects of statement in reverse, writes then reads
      TempDescriptor[] writeTemps = fn.writesTemps();
      for (int i = 0; i < writeTemps.length; ++i) {
        liveIn.remove(writeTemps[i]);

        if (!toplevel) {
          FlatSESEExitNode fsexn = currentSESE.getFlatExit();
          Set<TempDescriptor> livetemps = liveout.get(fsexn);
          if (livetemps != null && livetemps.contains(writeTemps[i])) {
            // write to a live out temp...
            // need to put in SESE liveout set
            currentSESE.addOutVar(writeTemps[i]);
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

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get(fn);
      assert seseStack != null;

      VarSrcTokTable prev = variableResults.get(fn);

      // merge sets from control flow joins
      VarSrcTokTable curr = new VarSrcTokTable();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        VarSrcTokTable incoming = variableResults.get(nn);
        curr.merge(incoming);
      }

      if (!seseStack.empty()) {
        variable_nodeActions(fn, curr, seseStack.peek());
      }

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
      assert fsen.equals(currentSESE);

      vstTable.age(currentSESE);
      vstTable.assertConsistency();
    }
      break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
      FlatSESEEnterNode fsen = fsexn.getFlatEnter();
      assert currentSESE.getChildren().contains(fsen);

      // remap all of this child's children tokens to be
      // from this child as the child exits
      vstTable.remapChildTokens(fsen);

      // liveness virtual reads are things that might be
      // written by an SESE and should be added to the in-set
      // anything virtually read by this SESE should be pruned
      // of parent or sibling sources
      Set<TempDescriptor> liveVars = livenessRootView.get(fn);
      Set<TempDescriptor> fsenVirtReads = vstTable.calcVirtReadsAndPruneParentAndSiblingTokens(
          fsen, liveVars);
      Set<TempDescriptor> fsenVirtReadsOld = livenessVirtualReads.get(fn);
      if (fsenVirtReadsOld != null) {
        fsenVirtReads.addAll(fsenVirtReadsOld);
      }
      livenessVirtualReads.put(fn, fsenVirtReads);

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

          if (currentSESE.getChildren().contains(vst.getSESE())) {
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
        // assert writeTemps.length == 1;
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

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get(fn);
      assert seseStack != null;

      Set<TempDescriptor> prev = notAvailableResults.get(fn);

      Set<TempDescriptor> curr = new HashSet<TempDescriptor>();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        Set<TempDescriptor> notAvailIn = notAvailableResults.get(nn);
        if (notAvailIn != null) {
          curr.addAll(notAvailIn);
        }
      }

      if (!seseStack.empty()) {
        notAvailable_nodeActions(fn, curr, seseStack.peek());
      }

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
      assert fsen.equals(currentSESE);

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
      assert currentSESE.getChildren().contains(fsen);

      notAvailSet.addAll(fsen.getOutVarSet());

      Set<TempDescriptor> notAvailIn = notAvailableIntoSESE.get(fsen);
      assert notAvailIn != null;
      notAvailSet.addAll(notAvailIn);

    }
      break;

    case FKind.FlatMethod: {
      notAvailSet.clear();
    }

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

          Iterator<VariableSourceToken> availItr = vstTable.get(vst.getSESE(), vst.getAge())
              .iterator();

          // look through things that are also available from same source
          while (availItr.hasNext()) {
            VariableSourceToken vstAlsoAvail = availItr.next();

            Iterator<TempDescriptor> refVarItr = vstAlsoAvail.getRefVars().iterator();
            while (refVarItr.hasNext()) {
              TempDescriptor refVarAlso = refVarItr.next();

              // if a variable is available from the same source, AND it ALSO
              // only comes from one statically known source, mark it available
              VSTWrapper vstIfStaticNotUsed = new VSTWrapper();
              Integer srcTypeAlso = vstTable.getRefVarSrcType(refVarAlso, currentSESE,
                  vstIfStaticNotUsed);
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

}
