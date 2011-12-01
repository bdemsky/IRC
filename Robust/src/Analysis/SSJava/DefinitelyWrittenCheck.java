package Analysis.SSJava;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

import Analysis.CallGraph.CallGraph;
import Analysis.Loops.LoopFinder;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.Operation;
import IR.State;
import IR.TypeDescriptor;
import IR.TypeExtension;
import IR.Flat.FKind;
import IR.Flat.FlatCall;
import IR.Flat.FlatElementNode;
import IR.Flat.FlatFieldNode;
import IR.Flat.FlatLiteralNode;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.FlatSetElementNode;
import IR.Flat.FlatSetFieldNode;
import IR.Flat.TempDescriptor;
import IR.Tree.Modifiers;
import Util.Pair;

public class DefinitelyWrittenCheck {

  SSJavaAnalysis ssjava;
  State state;
  CallGraph callGraph;

  int debugcount = 0;

  // maps a descriptor to its known dependents: namely
  // methods or tasks that call the descriptor's method
  // AND are part of this analysis (reachable from main)
  private Hashtable<Descriptor, Set<MethodDescriptor>> mapDescriptorToSetDependents;

  // maps a flat node to its WrittenSet: this keeps all heap path overwritten
  // previously.
  private Hashtable<FlatNode, Set<NTuple<Descriptor>>> mapFlatNodeToMustWriteSet;

  // maps a temp descriptor to its heap path
  // each temp descriptor has a unique heap path since we do not allow any
  // alias.
  private Hashtable<Descriptor, NTuple<Descriptor>> mapHeapPath;

  // maps a temp descriptor to its composite location
  private Hashtable<TempDescriptor, NTuple<Location>> mapDescriptorToLocationPath;

  // maps a flat method to the READ that is the set of heap path that is
  // expected to be written before method invocation
  private Hashtable<FlatMethod, Set<NTuple<Descriptor>>> mapFlatMethodToReadSet;

  // maps a flat method to the must-write set that is the set of heap path that
  // is overwritten on every possible path during method invocation
  private Hashtable<FlatMethod, Set<NTuple<Descriptor>>> mapFlatMethodToMustWriteSet;

  // maps a flat method to the DELETE SET that is a set of heap path to shared
  // locations that are
  // written to but not overwritten by the higher value
  private Hashtable<FlatMethod, SharedLocMap> mapFlatMethodToDeleteSet;

  // maps a flat method to the S SET that is a set of heap path to shared
  // locations that are overwritten by the higher value
  private Hashtable<FlatMethod, SharedLocMap> mapFlatMethodToSharedLocMap;

  // maps a flat method to the may-wirte set that is the set of heap path that
  // might be written to
  private Hashtable<FlatMethod, Set<NTuple<Descriptor>>> mapFlatMethodToMayWriteSet;

  // maps a call site to the read set contributed by all callees
  private Hashtable<FlatNode, Set<NTuple<Descriptor>>> mapFlatNodeToBoundReadSet;

  // maps a call site to the must write set contributed by all callees
  private Hashtable<FlatNode, Set<NTuple<Descriptor>>> mapFlatNodeToBoundMustWriteSet;

  // maps a call site to the may read set contributed by all callees
  private Hashtable<FlatNode, Set<NTuple<Descriptor>>> mapFlatNodeToBoundMayWriteSet;

  // points to method containing SSJAVA Loop
  private MethodDescriptor methodContainingSSJavaLoop;

  // maps a flatnode to definitely written analysis mapping M
  private Hashtable<FlatNode, Hashtable<NTuple<Descriptor>, Set<WriteAge>>> mapFlatNodetoEventLoopMap;

  // maps a method descriptor to its current summary during the analysis
  // then analysis reaches fixed-point, this mapping will have the final summary
  // for each method descriptor
  private Hashtable<MethodDescriptor, ClearingSummary> mapMethodDescriptorToCompleteClearingSummary;

  // maps a method descriptor to the merged incoming caller's current
  // overwritten status
  private Hashtable<MethodDescriptor, ClearingSummary> mapMethodDescriptorToInitialClearingSummary;

  // maps a flat node to current partial results
  private Hashtable<FlatNode, ClearingSummary> mapFlatNodeToClearingSummary;

  // maps shared location to the set of descriptors which belong to the shared
  // location

  // keep current descriptors to visit in fixed-point interprocedural analysis,
  private Stack<MethodDescriptor> methodDescriptorsToVisitStack;

  // when analyzing flatcall, need to re-schedule set of callee
  private Set<MethodDescriptor> calleesToEnqueue;

  private Set<ReadSummary> possibleCalleeReadSummarySetToCaller;

  public static final String arrayElementFieldName = "___element_";
  static protected Hashtable<TypeDescriptor, FieldDescriptor> mapTypeToArrayField;

  private Set<ClearingSummary> possibleCalleeCompleteSummarySetToCaller;

  // maps a method descriptor to the merged incoming caller's current
  // reading status
  // it is for setting clearance flag when all read set is overwritten
  private Hashtable<MethodDescriptor, ReadSummary> mapMethodDescriptorToReadSummary;

  private MultiSourceMap<Location, Descriptor> mapLocationPathToMayWrittenSet;

  private Hashtable<MethodDescriptor, MultiSourceMap<Location, Descriptor>> mapMethodToSharedWriteMapping;

  private Hashtable<FlatNode, SharedLocMap> mapFlatNodeToSharedLocMapping;
  private Hashtable<FlatNode, SharedLocMap> mapFlatNodeToDeleteSet;

  private Hashtable<Location, Set<Descriptor>> mapSharedLocationToCoverSet;

  private LinkedList<MethodDescriptor> sortedDescriptors;

  private FlatNode ssjavaLoopEntrance;
  private LoopFinder ssjavaLoop;
  private Set<FlatNode> loopIncElements;

  private Set<NTuple<Descriptor>> calleeUnionBoundReadSet;
  private Set<NTuple<Descriptor>> calleeIntersectBoundMustWriteSet;
  private Set<NTuple<Descriptor>> calleeUnionBoundMayWriteSet;
  private SharedLocMap calleeUnionBoundDeleteSet;
  private SharedLocMap calleeIntersectBoundSharedSet;

  private Hashtable<Descriptor, Location> mapDescToLocation;

  private TempDescriptor LOCAL;

  public static int MAXAGE = 1;

  public DefinitelyWrittenCheck(SSJavaAnalysis ssjava, State state) {
    this.state = state;
    this.ssjava = ssjava;
    this.callGraph = ssjava.getCallGraph();
    this.mapFlatNodeToMustWriteSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapDescriptorToSetDependents = new Hashtable<Descriptor, Set<MethodDescriptor>>();
    this.mapHeapPath = new Hashtable<Descriptor, NTuple<Descriptor>>();
    this.mapDescriptorToLocationPath = new Hashtable<TempDescriptor, NTuple<Location>>();
    this.mapFlatMethodToReadSet = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
    this.mapFlatMethodToMustWriteSet = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
    this.mapFlatMethodToMayWriteSet = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
    this.mapFlatNodetoEventLoopMap =
        new Hashtable<FlatNode, Hashtable<NTuple<Descriptor>, Set<WriteAge>>>();
    this.calleeUnionBoundReadSet = new HashSet<NTuple<Descriptor>>();
    this.calleeIntersectBoundMustWriteSet = new HashSet<NTuple<Descriptor>>();
    this.calleeUnionBoundMayWriteSet = new HashSet<NTuple<Descriptor>>();

    this.mapMethodDescriptorToCompleteClearingSummary =
        new Hashtable<MethodDescriptor, ClearingSummary>();
    this.mapMethodDescriptorToInitialClearingSummary =
        new Hashtable<MethodDescriptor, ClearingSummary>();
    this.methodDescriptorsToVisitStack = new Stack<MethodDescriptor>();
    this.calleesToEnqueue = new HashSet<MethodDescriptor>();
    this.possibleCalleeCompleteSummarySetToCaller = new HashSet<ClearingSummary>();
    this.mapTypeToArrayField = new Hashtable<TypeDescriptor, FieldDescriptor>();
    this.LOCAL = new TempDescriptor("LOCAL");
    this.mapDescToLocation = new Hashtable<Descriptor, Location>();
    this.possibleCalleeReadSummarySetToCaller = new HashSet<ReadSummary>();
    this.mapMethodDescriptorToReadSummary = new Hashtable<MethodDescriptor, ReadSummary>();
    this.mapFlatNodeToBoundReadSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapFlatNodeToBoundMustWriteSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapFlatNodeToBoundMayWriteSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapSharedLocationToCoverSet = new Hashtable<Location, Set<Descriptor>>();
    this.mapFlatNodeToSharedLocMapping = new Hashtable<FlatNode, SharedLocMap>();
    this.mapFlatMethodToDeleteSet = new Hashtable<FlatMethod, SharedLocMap>();
    this.calleeUnionBoundDeleteSet = new SharedLocMap();
    this.calleeIntersectBoundSharedSet = new SharedLocMap();
    this.mapFlatMethodToSharedLocMap = new Hashtable<FlatMethod, SharedLocMap>();
    this.mapLocationPathToMayWrittenSet = new MultiSourceMap<Location, Descriptor>();
    this.mapMethodToSharedWriteMapping =
        new Hashtable<MethodDescriptor, MultiSourceMap<Location, Descriptor>>();
    this.mapFlatNodeToDeleteSet = new Hashtable<FlatNode, SharedLocMap>();
  }

  public void definitelyWrittenCheck() {
    if (!ssjava.getAnnotationRequireSet().isEmpty()) {
      initialize();
      computeSharedCoverSet();

      // System.out.println("#");
      // System.out.println(mapLocationPathToMayWrittenSet);

      methodReadWriteSetAnalysis();
      sharedLocAnalysis();

      eventLoopAnalysis();

    }
  }

  private void sharedLocAnalysis() {

    // perform method READ/OVERWRITE analysis
    LinkedList<MethodDescriptor> descriptorListToAnalyze =
        (LinkedList<MethodDescriptor>) sortedDescriptors.clone();

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by
    // dependency in the call graph
    methodDescriptorsToVisitStack.clear();

    descriptorListToAnalyze.removeFirst();

    Set<MethodDescriptor> methodDescriptorToVistSet = new HashSet<MethodDescriptor>();
    methodDescriptorToVistSet.addAll(descriptorListToAnalyze);

    while (!descriptorListToAnalyze.isEmpty()) {
      MethodDescriptor md = descriptorListToAnalyze.removeFirst();
      methodDescriptorsToVisitStack.add(md);
    }

    // analyze scheduled methods until there are no more to visit
    while (!methodDescriptorsToVisitStack.isEmpty()) {
      // start to analyze leaf node
      MethodDescriptor md = methodDescriptorsToVisitStack.pop();
      FlatMethod fm = state.getMethodFlat(md);

      SharedLocMap sharedLocMap = new SharedLocMap();
      SharedLocMap deleteSet = new SharedLocMap();

      sharedLoc_analyzeMethod(fm, sharedLocMap, deleteSet);
      SharedLocMap prevSharedLocMap = mapFlatMethodToSharedLocMap.get(fm);
      SharedLocMap prevDeleteSet = mapFlatMethodToDeleteSet.get(fm);

      if (!(deleteSet.equals(prevDeleteSet) && sharedLocMap.equals(prevSharedLocMap))) {
        mapFlatMethodToSharedLocMap.put(fm, sharedLocMap);
        mapFlatMethodToDeleteSet.put(fm, deleteSet);

        // results for callee changed, so enqueue dependents caller for
        // further
        // analysis
        Iterator<MethodDescriptor> depsItr = getDependents(md).iterator();
        while (depsItr.hasNext()) {
          MethodDescriptor methodNext = depsItr.next();
          if (!methodDescriptorsToVisitStack.contains(methodNext)
              && methodDescriptorToVistSet.contains(methodNext)) {
            methodDescriptorsToVisitStack.add(methodNext);
          }

        }

      }

    }

    sharedLoc_analyzeEventLoop();

  }

  private void sharedLoc_analyzeEventLoop() {
    if (state.SSJAVADEBUG) {
      System.out.println("SSJAVA: Definite clearance for shared locations Analyzing: eventloop");
    }
    SharedLocMap sharedLocMap = new SharedLocMap();
    SharedLocMap deleteSet = new SharedLocMap();
    sharedLoc_analyzeBody(state.getMethodFlat(methodContainingSSJavaLoop), ssjavaLoopEntrance,
        sharedLocMap, deleteSet, true);
  }

  private void sharedLoc_analyzeMethod(FlatMethod fm, SharedLocMap sharedLocMap,
      SharedLocMap deleteSet) {
    if (state.SSJAVADEBUG) {
      System.out.println("SSJAVA: Definite clearance for shared locations Analyzing: " + fm);
    }

    sharedLoc_analyzeBody(fm, fm, sharedLocMap, deleteSet, false);

  }

  private void sharedLoc_analyzeBody(FlatMethod fm, FlatNode startNode, SharedLocMap sharedLocMap,
      SharedLocMap deleteSet, boolean isEventLoopBody) {

    // intraprocedural analysis
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(startNode);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      SharedLocMap currSharedSet = new SharedLocMap();
      SharedLocMap currDeleteSet = new SharedLocMap();

      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode prevFn = fn.getPrev(i);
        SharedLocMap inSharedLoc = mapFlatNodeToSharedLocMapping.get(prevFn);
        if (inSharedLoc != null) {
          mergeSharedLocMap(currSharedSet, inSharedLoc);
        }

        SharedLocMap inDeleteLoc = mapFlatNodeToDeleteSet.get(prevFn);
        if (inDeleteLoc != null) {
          mergeDeleteSet(currDeleteSet, inDeleteLoc);
        }
      }

      sharedLoc_nodeActions(fm, fn, currSharedSet, currDeleteSet, sharedLocMap, deleteSet,
          isEventLoopBody);

      SharedLocMap prevSharedSet = mapFlatNodeToSharedLocMapping.get(fn);
      SharedLocMap prevDeleteSet = mapFlatNodeToDeleteSet.get(fn);

      if (!(currSharedSet.equals(prevSharedSet) && currDeleteSet.equals(prevDeleteSet))) {
        mapFlatNodeToSharedLocMapping.put(fn, currSharedSet);
        mapFlatNodeToDeleteSet.put(fn, currDeleteSet);
        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          if ((!isEventLoopBody) || loopIncElements.contains(nn)) {
            flatNodesToVisit.add(nn);
          }

        }
      }

    }

  }

  private void sharedLoc_nodeActions(FlatMethod fm, FlatNode fn, SharedLocMap curr,
      SharedLocMap currDeleteSet, SharedLocMap sharedLocMap, SharedLocMap deleteSet,
      boolean isEventLoopBody) {

    SharedLocMap killSet = new SharedLocMap();
    SharedLocMap genSet = new SharedLocMap();

    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;

    switch (fn.kind()) {

    case FKind.FlatOpNode: {

      if (isEventLoopBody) {
        FlatOpNode fon = (FlatOpNode) fn;

        if (fon.getOp().getOp() == Operation.ASSIGN) {
          lhs = fon.getDest();
          rhs = fon.getLeft();

          if (!lhs.getSymbol().startsWith("neverused") && rhs.getType().isImmutable()) {

            Location dstLoc = getLocation(lhs);
            if (dstLoc != null && ssjava.isSharedLocation(dstLoc)) {
              NTuple<Descriptor> lhsHeapPath = computePath(lhs);
              NTuple<Location> lhsLocTuple = mapDescriptorToLocationPath.get(lhs);

              Location srcLoc = getLocation(lhs);

              // computing gen/kill set
              computeKILLSetForWrite(curr, killSet, lhsLocTuple, lhsHeapPath);
              if (!dstLoc.equals(srcLoc)) {
                computeGENSetForHigherWrite(curr, killSet, lhsLocTuple, lhsHeapPath);
                updateDeleteSetForHigherWrite(currDeleteSet, lhsLocTuple, lhsHeapPath);
              } else {
                computeGENSetForSameHeightWrite(curr, killSet, lhsLocTuple, lhsHeapPath);
                updateDeleteSetForSameHeightWrite(currDeleteSet, lhsLocTuple, lhsHeapPath);
              }

              // System.out.println("VAR WRITE:" + fn);
              // System.out.println("lhsLocTuple=" + lhsLocTuple +
              // " lhsHeapPath="
              // + lhsHeapPath);
              // System.out.println("dstLoc=" + dstLoc + " srcLoc=" + srcLoc);
              // System.out.println("KILLSET=" + killSet);
              // System.out.println("GENSet=" + genSet);
              // System.out.println("DELETESET=" + currDeleteSet);

            }

          }
        }

      }

    }
      break;

    case FKind.FlatSetFieldNode:
    case FKind.FlatSetElementNode: {

      if (fn.kind() == FKind.FlatSetFieldNode) {
        FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
        lhs = fsfn.getDst();
        fld = fsfn.getField();
        rhs = fsfn.getSrc();
      } else {
        FlatSetElementNode fsen = (FlatSetElementNode) fn;
        lhs = fsen.getDst();
        rhs = fsen.getSrc();
        TypeDescriptor td = lhs.getType().dereference();
        fld = getArrayField(td);
      }

      // shared loc extension
      Location srcLoc = getLocation(rhs);
      Location fieldLoc = (Location) fld.getType().getExtension();
      if (ssjava.isSharedLocation(fieldLoc)) {
        // only care the case that loc(f) is shared location
        // write(field)

        NTuple<Location> fieldLocTuple = new NTuple<Location>();
        fieldLocTuple.addAll(mapDescriptorToLocationPath.get(lhs));
        fieldLocTuple.add(fieldLoc);

        NTuple<Descriptor> fldHeapPath = computePath(fld);

        // computing gen/kill set
        computeKILLSetForWrite(curr, killSet, fieldLocTuple, fldHeapPath);
        if (!fieldLoc.equals(srcLoc)) {
          computeGENSetForHigherWrite(curr, genSet, fieldLocTuple, fldHeapPath);
          updateDeleteSetForHigherWrite(currDeleteSet, fieldLocTuple, fldHeapPath);
        } else {
          computeGENSetForSameHeightWrite(curr, genSet, fieldLocTuple, fldHeapPath);
          updateDeleteSetForSameHeightWrite(currDeleteSet, fieldLocTuple, fldHeapPath);
        }

        // System.out.println("################");
        // System.out.println("FIELD WRITE:" + fn);
        // System.out.println("FldHeapPath=" + fldHeapPath);
        // System.out.println("fieldLocTuple=" + fieldLocTuple + " srcLoc=" +
        // srcLoc);
        // System.out.println("KILLSET=" + killSet);
        // System.out.println("GENSet=" + genSet);
        // System.out.println("DELETESET=" + currDeleteSet);
      }

    }
      break;

    case FKind.FlatCall: {
      FlatCall fc = (FlatCall) fn;

      bindHeapPathCallerArgWithCaleeParamForSharedLoc(fm.getMethod(), fc);

      // computing gen/kill set
      generateKILLSetForFlatCall(curr, killSet);
      generateGENSetForFlatCall(curr, genSet);

      // System.out.println("#FLATCALL=" + fc);
      // System.out.println("KILLSET=" + killSet);
      // System.out.println("GENSet=" + genSet);
      // System.out.println("bound DELETE Set=" + calleeUnionBoundDeleteSet);

    }
      break;

    case FKind.FlatExit: {
      // merge the current delete/shared loc mapping
      mergeSharedLocMap(sharedLocMap, curr);
      mergeDeleteSet(deleteSet, currDeleteSet);

      // System.out.println("#FLATEXIT sharedLocMap=" + sharedLocMap);
    }
      break;

    }

    computeNewMapping(curr, killSet, genSet);
    // System.out.println("#######" + curr);

  }

  private void generateGENSetForFlatCall(SharedLocMap curr, SharedLocMap genSet) {

    Set<NTuple<Location>> locTupleSet = calleeIntersectBoundSharedSet.keySet();
    for (Iterator iterator = locTupleSet.iterator(); iterator.hasNext();) {
      NTuple<Location> locTupleKey = (NTuple<Location>) iterator.next();
      genSet.addWrite(locTupleKey, curr.get(locTupleKey));
      genSet.addWrite(locTupleKey, calleeIntersectBoundSharedSet.get(locTupleKey));

      genSet.removeWriteAll(locTupleKey, calleeUnionBoundDeleteSet.get(locTupleKey));
    }

  }

  private void generateKILLSetForFlatCall(SharedLocMap curr, SharedLocMap killSet) {

    Set<NTuple<Location>> locTupleSet = calleeIntersectBoundSharedSet.keySet();
    for (Iterator iterator = locTupleSet.iterator(); iterator.hasNext();) {
      NTuple<Location> locTupleKey = (NTuple<Location>) iterator.next();
      killSet.addWrite(locTupleKey, curr.get(locTupleKey));
    }

  }

  private void mergeDeleteSet(SharedLocMap currDeleteSet, SharedLocMap inDeleteLoc) {

    Set<NTuple<Location>> locTupleKeySet = inDeleteLoc.keySet();

    for (Iterator iterator = locTupleKeySet.iterator(); iterator.hasNext();) {
      NTuple<Location> locTupleKey = (NTuple<Location>) iterator.next();

      Set<NTuple<Descriptor>> inSet = inDeleteLoc.get(locTupleKey);
      currDeleteSet.addWrite(locTupleKey, inSet);

    }
  }

  private void computeNewMapping(SharedLocMap curr, SharedLocMap killSet, SharedLocMap genSet) {
    curr.kill(killSet);
    curr.gen(genSet);
  }

  private void updateDeleteSetForHigherWrite(SharedLocMap currDeleteSet, NTuple<Location> locTuple,
      NTuple<Descriptor> hp) {
    currDeleteSet.removeWrite(locTuple, hp);
  }

  private void updateDeleteSetForSameHeightWrite(SharedLocMap currDeleteSet,
      NTuple<Location> locTuple, NTuple<Descriptor> hp) {
    currDeleteSet.addWrite(locTuple, hp);
  }

  private void computeGENSetForHigherWrite(SharedLocMap curr, SharedLocMap genSet,
      NTuple<Location> locTuple, NTuple<Descriptor> hp) {
    Set<NTuple<Descriptor>> currWriteSet = curr.get(locTuple);

    if (currWriteSet != null) {
      genSet.addWrite(locTuple, currWriteSet);
    }

    genSet.addWrite(locTuple, hp);
  }

  private void computeGENSetForSameHeightWrite(SharedLocMap curr, SharedLocMap genSet,
      NTuple<Location> locTuple, NTuple<Descriptor> hp) {
    Set<NTuple<Descriptor>> currWriteSet = curr.get(locTuple);

    if (currWriteSet != null) {
      genSet.addWrite(locTuple, currWriteSet);
    }
    genSet.removeWrite(locTuple, hp);
  }

  private void computeKILLSetForWrite(SharedLocMap curr, SharedLocMap killSet,
      NTuple<Location> locTuple, NTuple<Descriptor> hp) {

    Set<NTuple<Descriptor>> writeSet = curr.get(locTuple);
    if (writeSet != null) {
      killSet.addWrite(locTuple, writeSet);
    }

  }

  private void mergeSharedLocMap(SharedLocMap currSharedSet, SharedLocMap in) {

    Set<NTuple<Location>> locTupleKeySet = in.keySet();
    for (Iterator iterator = locTupleKeySet.iterator(); iterator.hasNext();) {
      NTuple<Location> locTupleKey = (NTuple<Location>) iterator.next();

      Set<NTuple<Descriptor>> inSet = in.get(locTupleKey);
      Set<NTuple<Descriptor>> currSet = currSharedSet.get(locTupleKey);
      if (currSet == null) {
        currSet = new HashSet<NTuple<Descriptor>>();
        currSet.addAll(inSet);
        currSharedSet.addWrite(locTupleKey, currSet);
      }
      currSet.retainAll(inSet);
    }

  }

  private void checkSharedLocationResult() {

    // mapping of method containing ssjava loop has the final result of
    // shared location analysis

    ClearingSummary result =
        mapMethodDescriptorToCompleteClearingSummary.get(methodContainingSSJavaLoop);

    String str = generateNotClearedResult(result);
    if (str.length() > 0) {
      throw new Error(
          "Following concrete locations of the shared abstract location are not cleared at the same time:\n"
              + str);
    }

  }

  private String generateNotClearedResult(ClearingSummary result) {
    Set<NTuple<Descriptor>> keySet = result.keySet();

    StringBuffer str = new StringBuffer();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
      SharedStatus status = result.get(hpKey);
      Hashtable<Location, Pair<Set<Descriptor>, Boolean>> map = status.getMap();
      Set<Location> locKeySet = map.keySet();
      for (Iterator iterator2 = locKeySet.iterator(); iterator2.hasNext();) {
        Location locKey = (Location) iterator2.next();
        if (status.haveWriteEffect(locKey)) {
          Pair<Set<Descriptor>, Boolean> pair = map.get(locKey);
          if (!pair.getSecond().booleanValue()) {
            // not cleared!
            str.append("- Concrete locations of the shared location '" + locKey
                + "' are not cleared out, which are reachable through the heap path '" + hpKey
                + ".\n");
          }
        }
      }
    }

    return str.toString();

  }

  private void writeReadMapFile() {

    String fileName = "SharedLocationReadMap";

    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(fileName + ".txt"));

      Set<MethodDescriptor> keySet = mapMethodDescriptorToReadSummary.keySet();
      for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
        MethodDescriptor mdKey = (MethodDescriptor) iterator.next();
        ReadSummary summary = mapMethodDescriptorToReadSummary.get(mdKey);
        bw.write("Method " + mdKey + "::\n");
        bw.write(summary + "\n\n");
      }
      bw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  private void sharedLocationAnalysis() {
    // verify that all concrete locations of shared location are cleared out at
    // the same time once per the out-most loop

    computeSharedCoverSet();

    if (state.SSJAVADEBUG) {
      writeReadMapFile();
    }

    // methodDescriptorsToVisitStack.clear();
    // methodDescriptorsToVisitStack.add(sortedDescriptors.peekFirst());

    LinkedList<MethodDescriptor> descriptorListToAnalyze =
        (LinkedList<MethodDescriptor>) sortedDescriptors.clone();

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by
    // dependency in the call graph
    methodDescriptorsToVisitStack.clear();

    Set<MethodDescriptor> methodDescriptorToVistSet = new HashSet<MethodDescriptor>();
    methodDescriptorToVistSet.addAll(descriptorListToAnalyze);

    while (!descriptorListToAnalyze.isEmpty()) {
      MethodDescriptor md = descriptorListToAnalyze.removeFirst();
      methodDescriptorsToVisitStack.add(md);
    }

    // analyze scheduled methods until there are no more to visit
    while (!methodDescriptorsToVisitStack.isEmpty()) {
      MethodDescriptor md = methodDescriptorsToVisitStack.pop();

      ClearingSummary completeSummary =
          sharedLocation_analyzeMethod(md, (md.equals(methodContainingSSJavaLoop)));

      ClearingSummary prevCompleteSummary = mapMethodDescriptorToCompleteClearingSummary.get(md);

      if (!completeSummary.equals(prevCompleteSummary)) {

        mapMethodDescriptorToCompleteClearingSummary.put(md, completeSummary);

        // results for callee changed, so enqueue dependents caller for
        // further analysis
        Iterator<MethodDescriptor> depsItr = getDependents(md).iterator();
        while (depsItr.hasNext()) {
          MethodDescriptor methodNext = depsItr.next();
          if (!methodDescriptorsToVisitStack.contains(methodNext)) {
            methodDescriptorsToVisitStack.add(methodNext);
          }
        }

        // if there is set of callee to be analyzed,
        // add this set into the top of stack
        Iterator<MethodDescriptor> calleeIter = calleesToEnqueue.iterator();
        while (calleeIter.hasNext()) {
          MethodDescriptor mdNext = calleeIter.next();
          if (!methodDescriptorsToVisitStack.contains(mdNext)) {
            methodDescriptorsToVisitStack.add(mdNext);
          }
        }
        calleesToEnqueue.clear();

      }

    }

  }

  private ClearingSummary sharedLocation_analyzeMethod(MethodDescriptor md,
      boolean onlyVisitSSJavaLoop) {

    if (state.SSJAVADEBUG) {
      System.out.println("SSJAVA: Definite clearance for shared locations Analyzing: " + md);
    }

    FlatMethod fm = state.getMethodFlat(md);

    // intraprocedural analysis
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();

    // start a new mapping of partial results for each flat node
    mapFlatNodeToClearingSummary = new Hashtable<FlatNode, ClearingSummary>();

    if (onlyVisitSSJavaLoop) {
      flatNodesToVisit.add(ssjavaLoopEntrance);
    } else {
      flatNodesToVisit.add(fm);
    }

    Set<FlatNode> returnNodeSet = new HashSet<FlatNode>();

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      ClearingSummary curr = new ClearingSummary();

      Set<ClearingSummary> prevSet = new HashSet<ClearingSummary>();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode prevFn = fn.getPrev(i);
        ClearingSummary in = mapFlatNodeToClearingSummary.get(prevFn);
        if (in != null) {
          prevSet.add(in);
        }
      }
      mergeSharedLocationAnaylsis(curr, prevSet);

      sharedLocation_nodeActions(md, fn, curr, returnNodeSet, onlyVisitSSJavaLoop);
      ClearingSummary clearingPrev = mapFlatNodeToClearingSummary.get(fn);

      if (!curr.equals(clearingPrev)) {
        mapFlatNodeToClearingSummary.put(fn, curr);

        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);

          if (!onlyVisitSSJavaLoop || (onlyVisitSSJavaLoop && loopIncElements.contains(nn))) {
            flatNodesToVisit.add(nn);
          }

        }
      }

    }

    ClearingSummary completeSummary = new ClearingSummary();
    Set<ClearingSummary> summarySet = new HashSet<ClearingSummary>();

    if (onlyVisitSSJavaLoop) {
      // when analyzing ssjava loop,
      // complete summary is merging of all previous nodes of ssjava loop
      // entrance
      for (int i = 0; i < ssjavaLoopEntrance.numPrev(); i++) {
        ClearingSummary frnSummary =
            mapFlatNodeToClearingSummary.get(ssjavaLoopEntrance.getPrev(i));
        if (frnSummary != null) {
          summarySet.add(frnSummary);
        }
      }
    } else {
      // merging all exit node summary into the complete summary
      if (!returnNodeSet.isEmpty()) {
        for (Iterator iterator = returnNodeSet.iterator(); iterator.hasNext();) {
          FlatNode frn = (FlatNode) iterator.next();
          ClearingSummary frnSummary = mapFlatNodeToClearingSummary.get(frn);
          summarySet.add(frnSummary);
        }
      }
    }
    mergeSharedLocationAnaylsis(completeSummary, summarySet);

    return completeSummary;
  }

  private void sharedLocation_nodeActions(MethodDescriptor md, FlatNode fn, ClearingSummary curr,
      Set<FlatNode> returnNodeSet, boolean isSSJavaLoop) {

    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;
    switch (fn.kind()) {

    case FKind.FlatMethod: {
      FlatMethod fm = (FlatMethod) fn;

      ClearingSummary summaryFromCaller =
          mapMethodDescriptorToInitialClearingSummary.get(fm.getMethod());

      Set<ClearingSummary> inSet = new HashSet<ClearingSummary>();
      if (summaryFromCaller != null) {
        inSet.add(summaryFromCaller);
        mergeSharedLocationAnaylsis(curr, inSet);
      }

    }
      break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;
      lhs = fon.getDest();
      rhs = fon.getLeft();

      if (fon.getOp().getOp() == Operation.ASSIGN) {
        if (rhs.getType().isImmutable() && isSSJavaLoop) {
          // in ssjavaloop, we need to take care about reading local variables!
          NTuple<Descriptor> rhsHeapPath = new NTuple<Descriptor>();
          NTuple<Descriptor> lhsHeapPath = new NTuple<Descriptor>();
          rhsHeapPath.add(LOCAL);
          lhsHeapPath.add(LOCAL);
          if (!lhs.getSymbol().startsWith("neverused")) {
            readLocation(md, curr, rhsHeapPath, getLocation(rhs), rhs);
            writeLocation(md, curr, lhsHeapPath, getLocation(lhs), lhs);
          }
        }
      }

    }
      break;

    case FKind.FlatSetFieldNode:
    case FKind.FlatSetElementNode: {

      // x.f=y

      if (fn.kind() == FKind.FlatSetFieldNode) {
        FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
        lhs = fsfn.getDst();
        fld = fsfn.getField();
        rhs = fsfn.getSrc();
      } else {
        FlatSetElementNode fsen = (FlatSetElementNode) fn;
        lhs = fsen.getDst();
        rhs = fsen.getSrc();
        TypeDescriptor td = lhs.getType().dereference();
        fld = getArrayField(td);
      }

      // write(field)
      NTuple<Descriptor> lhsHeapPath = computePath(lhs);
      NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>(lhsHeapPath.getList());
      if (fld.getType().isImmutable()) {

        writeLocation(md, curr, fldHeapPath, getLocation(fld), fld);

        Descriptor desc = fldHeapPath.get(fldHeapPath.size() - 1);
        if (desc instanceof FieldDescriptor) {
          NTuple<Descriptor> arrayPath = new NTuple<Descriptor>();
          for (int i = 0; i < fldHeapPath.size() - 1; i++) {
            arrayPath.add(fldHeapPath.get(i));
          }
          SharedStatus state = getState(curr, arrayPath);
          state.setWriteEffect(getLocation(desc));
        }

      } else {
        // updates reference field case:
        fldHeapPath.add(fld);
        updateWriteEffectOnReferenceField(curr, fldHeapPath);
      }

    }
      break;

    case FKind.FlatCall: {

      FlatCall fc = (FlatCall) fn;

      if (ssjava.isSSJavaUtil(fc.getMethod().getClassDesc())) {
        // ssjava util case!
        // have write effects on the first argument

        if (fc.getArg(0).getType().isArray()) {
          // updates reference field case:
          // 2. if there exists a tuple t in sharing summary that starts with
          // hp(x) then, set flag of tuple t to 'true'
          NTuple<Descriptor> argHeapPath = computePath(fc.getArg(0));

          Location loc = getLocation(fc.getArg(0));
          NTuple<Descriptor> newHeapPath = new NTuple<Descriptor>();
          for (int i = 0; i < argHeapPath.size() - 1; i++) {
            newHeapPath.add(argHeapPath.get(i));
          }
          fld = (FieldDescriptor) argHeapPath.get(argHeapPath.size() - 1);
          argHeapPath = newHeapPath;

          writeLocation(md, curr, argHeapPath, loc, fld);
        }

      } else {
        // find out the set of callees
        MethodDescriptor mdCallee = fc.getMethod();
        FlatMethod fmCallee = state.getMethodFlat(mdCallee);
        Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
        setPossibleCallees.addAll(callGraph.getMethods(mdCallee));

        possibleCalleeCompleteSummarySetToCaller.clear();

        for (Iterator iterator = setPossibleCallees.iterator(); iterator.hasNext();) {
          MethodDescriptor mdPossibleCallee = (MethodDescriptor) iterator.next();
          FlatMethod calleeFlatMethod = state.getMethodFlat(mdPossibleCallee);

          addDependent(mdPossibleCallee, // callee
              md); // caller

          calleesToEnqueue.add(mdPossibleCallee);

          // updates possible callee's initial summary using caller's current
          // writing status
          ClearingSummary prevCalleeInitSummary =
              mapMethodDescriptorToInitialClearingSummary.get(mdPossibleCallee);

          ClearingSummary calleeInitSummary =
              bindHeapPathOfCalleeCallerEffects(fc, calleeFlatMethod, curr);

          Set<ClearingSummary> inSet = new HashSet<ClearingSummary>();
          if (prevCalleeInitSummary != null) {
            inSet.add(prevCalleeInitSummary);
            mergeSharedLocationAnaylsis(calleeInitSummary, inSet);
          }

          // if changes, update the init summary
          // and reschedule the callee for analysis
          if (!calleeInitSummary.equals(prevCalleeInitSummary)) {

            if (!methodDescriptorsToVisitStack.contains(mdPossibleCallee)) {
              methodDescriptorsToVisitStack.add(mdPossibleCallee);
            }

            mapMethodDescriptorToInitialClearingSummary.put(mdPossibleCallee, calleeInitSummary);
          }

        }

        // contribute callee's writing effects to the caller
        mergeSharedLocationAnaylsis(curr, possibleCalleeCompleteSummarySetToCaller);

      }

    }
      break;

    case FKind.FlatReturnNode: {
      returnNodeSet.add(fn);
    }
      break;

    }

  }

  private void updateWriteEffectOnReferenceField(ClearingSummary curr, NTuple<Descriptor> heapPath) {

    // 2. if there exists a tuple t in sharing summary that starts with
    // hp(x) then, set flag of tuple t to 'true'
    Set<NTuple<Descriptor>> hpKeySet = curr.keySet();
    for (Iterator iterator = hpKeySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
      if (hpKey.startsWith(heapPath)) {
        curr.get(hpKey).updateFlag(true);
      }
    }

  }

  private ClearingSummary bindHeapPathOfCalleeCallerEffects(FlatCall fc,
      FlatMethod calleeFlatMethod, ClearingSummary curr) {

    ClearingSummary boundSet = new ClearingSummary();

    // create mapping from arg idx to its heap paths
    Hashtable<Integer, NTuple<Descriptor>> mapArgIdx2CallerArgHeapPath =
        new Hashtable<Integer, NTuple<Descriptor>>();

    if (fc.getThis() != null) {
      // arg idx is starting from 'this' arg
      NTuple<Descriptor> thisHeapPath = mapHeapPath.get(fc.getThis());
      if (thisHeapPath == null) {
        // method is called without creating new flat node representing 'this'
        thisHeapPath = new NTuple<Descriptor>();
        thisHeapPath.add(fc.getThis());
      }

      mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(0), thisHeapPath);
    }

    for (int i = 0; i < fc.numArgs(); i++) {
      TempDescriptor arg = fc.getArg(i);
      NTuple<Descriptor> argHeapPath = computePath(arg);
      mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(i + 1), argHeapPath);
    }

    Hashtable<Integer, TempDescriptor> mapParamIdx2ParamTempDesc =
        new Hashtable<Integer, TempDescriptor>();
    int offset = 0;
    if (calleeFlatMethod.getMethod().isStatic()) {
      // static method does not have implicit 'this' arg
      offset = 1;
    }
    for (int i = 0; i < calleeFlatMethod.numParameters(); i++) {
      TempDescriptor param = calleeFlatMethod.getParameter(i);
      mapParamIdx2ParamTempDesc.put(Integer.valueOf(i + offset), param);
    }

    // binding caller's writing effects to callee's params
    for (int i = 0; i < calleeFlatMethod.numParameters(); i++) {
      NTuple<Descriptor> argHeapPath = mapArgIdx2CallerArgHeapPath.get(Integer.valueOf(i));

      if (argHeapPath != null) {
        // if method is static, the first argument is nulll because static
        // method does not have implicit "THIS" arg
        TempDescriptor calleeParamHeapPath = mapParamIdx2ParamTempDesc.get(Integer.valueOf(i));

        // iterate over caller's writing effect set
        Set<NTuple<Descriptor>> hpKeySet = curr.keySet();
        for (Iterator iterator = hpKeySet.iterator(); iterator.hasNext();) {
          NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
          // current element is reachable caller's arg
          // so need to bind it to the caller's side and add it to the
          // callee's
          // init summary
          if (hpKey.startsWith(argHeapPath)) {
            NTuple<Descriptor> boundHeapPath = replace(hpKey, argHeapPath, calleeParamHeapPath);
            boundSet.put(boundHeapPath, curr.get(hpKey).clone());
          }

        }
      }

    }

    // contribute callee's complete summary into the caller's current summary
    ClearingSummary calleeCompleteSummary =
        mapMethodDescriptorToCompleteClearingSummary.get(calleeFlatMethod.getMethod());
    if (calleeCompleteSummary != null) {
      ClearingSummary boundCalleeEfffects = new ClearingSummary();
      for (int i = 0; i < calleeFlatMethod.numParameters(); i++) {
        NTuple<Descriptor> argHeapPath = mapArgIdx2CallerArgHeapPath.get(Integer.valueOf(i));

        if (argHeapPath != null) {
          // if method is static, the first argument is nulll because static
          // method does not have implicit "THIS" arg
          TempDescriptor calleeParamHeapPath = mapParamIdx2ParamTempDesc.get(Integer.valueOf(i));

          // iterate over callee's writing effect set
          Set<NTuple<Descriptor>> hpKeySet = calleeCompleteSummary.keySet();
          for (Iterator iterator = hpKeySet.iterator(); iterator.hasNext();) {
            NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
            // current element is reachable caller's arg
            // so need to bind it to the caller's side and add it to the
            // callee's
            // init summary
            if (hpKey.startsWith(calleeParamHeapPath)) {

              NTuple<Descriptor> boundHeapPathForCaller = replace(hpKey, argHeapPath);

              boundCalleeEfffects.put(boundHeapPathForCaller, calleeCompleteSummary.get(hpKey)
                  .clone());

            }
          }

        }

      }
      possibleCalleeCompleteSummarySetToCaller.add(boundCalleeEfffects);
    }

    return boundSet;
  }

  private NTuple<Descriptor> replace(NTuple<Descriptor> hpKey, NTuple<Descriptor> argHeapPath) {

    // replace the head of heap path with caller's arg path
    // for example, heap path 'param.a.b' in callee's side will be replaced with
    // (corresponding arg heap path).a.b for caller's side

    NTuple<Descriptor> bound = new NTuple<Descriptor>();

    for (int i = 0; i < argHeapPath.size(); i++) {
      bound.add(argHeapPath.get(i));
    }

    for (int i = 1; i < hpKey.size(); i++) {
      bound.add(hpKey.get(i));
    }

    return bound;
  }

  private NTuple<Descriptor> replace(NTuple<Descriptor> effectHeapPath,
      NTuple<Descriptor> argHeapPath, TempDescriptor calleeParamHeapPath) {
    // replace the head of caller's heap path with callee's param heap path

    NTuple<Descriptor> boundHeapPath = new NTuple<Descriptor>();
    boundHeapPath.add(calleeParamHeapPath);

    for (int i = argHeapPath.size(); i < effectHeapPath.size(); i++) {
      boundHeapPath.add(effectHeapPath.get(i));
    }

    return boundHeapPath;
  }

  private void computeSharedCoverSet() {
    LinkedList<MethodDescriptor> descriptorListToAnalyze =
        (LinkedList<MethodDescriptor>) sortedDescriptors.clone();

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by
    // dependency in the call graph
    methodDescriptorsToVisitStack.clear();

    descriptorListToAnalyze.removeFirst();

    Set<MethodDescriptor> methodDescriptorToVistSet = new HashSet<MethodDescriptor>();
    methodDescriptorToVistSet.addAll(descriptorListToAnalyze);

    while (!descriptorListToAnalyze.isEmpty()) {
      MethodDescriptor md = descriptorListToAnalyze.removeFirst();
      methodDescriptorsToVisitStack.add(md);
    }

    // analyze scheduled methods until there are no more to visit
    while (!methodDescriptorsToVisitStack.isEmpty()) {
      MethodDescriptor md = methodDescriptorsToVisitStack.pop();
      FlatMethod fm = state.getMethodFlat(md);
      computeSharedCoverSet_analyzeMethod(fm, md.equals(methodContainingSSJavaLoop));
    }

    computeSharedCoverSetForEventLoop();

  }

  private void computeSharedCoverSetForEventLoop() {
    computeSharedCoverSet_analyzeMethod(state.getMethodFlat(methodContainingSSJavaLoop), true);
  }

  private void computeSharedCoverSet_analyzeMethod(FlatMethod fm, boolean onlyVisitSSJavaLoop) {

    MethodDescriptor md = fm.getMethod();
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();

    Set<FlatNode> visited = new HashSet<FlatNode>();

    if (onlyVisitSSJavaLoop) {
      flatNodesToVisit.add(ssjavaLoopEntrance);
    } else {
      flatNodesToVisit.add(fm);
    }

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);
      visited.add(fn);

      computeSharedCoverSet_nodeActions(md, fn);

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);

        if (!visited.contains(nn)) {
          if (!onlyVisitSSJavaLoop || (onlyVisitSSJavaLoop && loopIncElements.contains(nn))) {
            flatNodesToVisit.add(nn);
          }
        }

      }

    }

  }

  private void computeSharedCoverSet_nodeActions(MethodDescriptor md, FlatNode fn) {
    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;

    switch (fn.kind()) {

    case FKind.FlatLiteralNode: {
      FlatLiteralNode fln = (FlatLiteralNode) fn;
      lhs = fln.getDst();

      if (lhs.getType().isPrimitive() && !lhs.getSymbol().startsWith("neverused")
          && !lhs.getSymbol().startsWith("srctmp")) {
        // only need to care about composite location case here
        if (lhs.getType().getExtension() instanceof SSJavaType) {
          CompositeLocation compLoc = ((SSJavaType) lhs.getType().getExtension()).getCompLoc();
          Location lastLocElement = compLoc.get(compLoc.getSize() - 1);
          // check if the last one is shared loc
          if (ssjava.isSharedLocation(lastLocElement)) {
            addSharedLocDescriptor(lastLocElement, lhs);
          }
        }
      }

    }
      break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;
      // for a normal assign node, need to propagate lhs's location path to
      // rhs
      if (fon.getOp().getOp() == Operation.ASSIGN) {
        rhs = fon.getLeft();
        lhs = fon.getDest();

        if (lhs.getType().isPrimitive() && !lhs.getSymbol().startsWith("neverused")
            && !lhs.getSymbol().startsWith("srctmp") && !lhs.getSymbol().startsWith("leftop")
            && !lhs.getSymbol().startsWith("rightop")) {

          NTuple<Location> lhsLocTuple = new NTuple<Location>();
          lhsLocTuple.addAll(deriveLocationTuple(md, rhs));

          mapLocationPathToMayWrittenSet.put(lhsLocTuple, null, lhs);
          addMayWrittenSet(md, lhsLocTuple, lhs);

        }

        if (mapDescriptorToLocationPath.containsKey(rhs)) {
          mapDescriptorToLocationPath.put(lhs, mapDescriptorToLocationPath.get(rhs));
        } else {
          if (rhs.getType().getExtension() instanceof SSJavaType) {
            NTuple<Location> rhsLocTuple =
                ((SSJavaType) rhs.getType().getExtension()).getCompLoc().getTuple();

            NTuple<Location> lhsLocTuple = new NTuple<Location>();
            lhsLocTuple.addAll(rhsLocTuple);

            mapDescriptorToLocationPath.put(rhs, rhsLocTuple);
            mapDescriptorToLocationPath.put(lhs, lhsLocTuple);

          }
        }

      }
    }
      break;

    case FKind.FlatSetFieldNode:
    case FKind.FlatSetElementNode: {

      // x.f=y;

      if (fn.kind() == FKind.FlatSetFieldNode) {
        FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
        lhs = fsfn.getDst();
        fld = fsfn.getField();
        rhs = fsfn.getSrc();
      } else {
        FlatSetElementNode fsen = (FlatSetElementNode) fn;
        lhs = fsen.getDst();
        rhs = fsen.getSrc();
        TypeDescriptor td = lhs.getType().dereference();
        fld = getArrayField(td);
      }

      Location fieldLocation = (Location) fld.getType().getExtension();
      if (ssjava.isSharedLocation(fieldLocation)) {
        addSharedLocDescriptor(fieldLocation, fld);

        NTuple<Location> locTuple = new NTuple<Location>();
        locTuple.addAll(deriveLocationTuple(md, lhs));
        locTuple.add(fieldLocation);

        // mapLocationPathToMayWrittenSet.put(locTuple, null, fld);
        addMayWrittenSet(md, locTuple, fld);

      }

    }
      break;

    case FKind.FlatElementNode:
    case FKind.FlatFieldNode: {

      // x=y.f;

      if (fn.kind() == FKind.FlatFieldNode) {
        FlatFieldNode ffn = (FlatFieldNode) fn;
        lhs = ffn.getDst();
        rhs = ffn.getSrc();
        fld = ffn.getField();
      } else {
        FlatElementNode fen = (FlatElementNode) fn;
        lhs = fen.getDst();
        rhs = fen.getSrc();
        TypeDescriptor td = rhs.getType().dereference();
        fld = getArrayField(td);
      }

      if (fld.isFinal()) {
        // if field is final no need to check
        break;
      }

      NTuple<Location> locTuple = new NTuple<Location>();
      locTuple.addAll(deriveLocationTuple(md, rhs));
      locTuple.add((Location) fld.getType().getExtension());

      mapDescriptorToLocationPath.put(lhs, locTuple);

    }
      break;

    case FKind.FlatCall: {

      FlatCall fc = (FlatCall) fn;
      bindLocationPathCallerArgWithCalleeParam(md, fc);

    }
      break;

    }
  }

  private void addMayWrittenSet(MethodDescriptor md, NTuple<Location> locTuple, Descriptor d) {

    MultiSourceMap<Location, Descriptor> map = mapMethodToSharedWriteMapping.get(md);
    if (map == null) {
      map = new MultiSourceMap<Location, Descriptor>();
      mapMethodToSharedWriteMapping.put(md, map);
    }

    Set<Descriptor> writeSet = map.get(locTuple);
    if (writeSet == null) {
      writeSet = new HashSet<Descriptor>();
      map.put(locTuple, writeSet);
    }
    writeSet.add(d);

    // System.out.println("ADD WRITE DESC=" + d + " TO locTuple=" + locTuple);
  }

  private void bindLocationPathCallerArgWithCalleeParam(MethodDescriptor mdCaller, FlatCall fc) {

    if (ssjava.isSSJavaUtil(fc.getMethod().getClassDesc())) {
      // ssjava util case!
      // have write effects on the first argument
      TempDescriptor arg = fc.getArg(0);
      NTuple<Location> argLocationPath = deriveLocationTuple(mdCaller, arg);
      NTuple<Descriptor> argHeapPath = computePath(arg);
      mapLocationPathToMayWrittenSet.put(argLocationPath, null,
          argHeapPath.get(argHeapPath.size() - 1));

    } else {

      // if arg is not primitive type, we need to propagate maywritten set to
      // the caller's location path

      MethodDescriptor mdCallee = fc.getMethod();
      Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
      setPossibleCallees.addAll(callGraph.getMethods(mdCallee));

      // create mapping from arg idx to its heap paths
      Hashtable<Integer, NTuple<Location>> mapArgIdx2CallerAgLocationStrPath =
          new Hashtable<Integer, NTuple<Location>>();

      // arg idx is starting from 'this' arg
      if (fc.getThis() != null) {
        NTuple<Location> thisLocationPath = deriveLocationTuple(mdCaller, fc.getThis());
        mapArgIdx2CallerAgLocationStrPath.put(Integer.valueOf(0), thisLocationPath);
      }

      Hashtable<Integer, Set<Descriptor>> mapParamIdx2WriteSet =
          new Hashtable<Integer, Set<Descriptor>>();

      for (int i = 0; i < fc.numArgs() + 1; i++) {
        mapParamIdx2WriteSet.put(Integer.valueOf(i), new HashSet<Descriptor>());
      }

      for (int i = 0; i < fc.numArgs(); i++) {
        TempDescriptor arg = fc.getArg(i);
        NTuple<Location> argLocationPath = deriveLocationTuple(mdCaller, arg);
        mapArgIdx2CallerAgLocationStrPath.put(Integer.valueOf(i + 1), argLocationPath);
      }

      for (Iterator iterator = setPossibleCallees.iterator(); iterator.hasNext();) {
        MethodDescriptor callee = (MethodDescriptor) iterator.next();
        FlatMethod calleeFlatMethod = state.getMethodFlat(callee);

        // binding caller's args and callee's params

        Hashtable<Integer, TempDescriptor> mapParamIdx2ParamTempDesc =
            new Hashtable<Integer, TempDescriptor>();
        int offset = 0;
        if (calleeFlatMethod.getMethod().isStatic()) {
          // static method does not have implicit 'this' arg
          offset = 1;
        }
        for (int i = 0; i < calleeFlatMethod.numParameters(); i++) {
          TempDescriptor param = calleeFlatMethod.getParameter(i);
          mapParamIdx2ParamTempDesc.put(Integer.valueOf(i + offset), param);
        }

        Set<Integer> keySet = mapArgIdx2CallerAgLocationStrPath.keySet();
        for (Iterator iterator2 = keySet.iterator(); iterator2.hasNext();) {
          Integer idx = (Integer) iterator2.next();
          NTuple<Location> callerArgLocationStrPath = mapArgIdx2CallerAgLocationStrPath.get(idx);

          TempDescriptor calleeParam = mapParamIdx2ParamTempDesc.get(idx);
          NTuple<Location> calleeLocationPath = deriveLocationTuple(mdCallee, calleeParam);

          // System.out.println("#createNewMappingOfMayWrittenSet callee=" +
          // callee
          // + " callerArgLocationStrPath=" + callerArgLocationStrPath +
          // "calleeLocationPath="
          // + calleeLocationPath + " idx=" + idx + " writeset=" +
          // mapParamIdx2WriteSet.get(idx));
          createNewMappingOfMayWrittenSet(callee, callerArgLocationStrPath, calleeLocationPath,
              mapParamIdx2WriteSet.get(idx));

        }

      }

    }

  }

  private void createNewMappingOfMayWrittenSet(MethodDescriptor callee,
      NTuple<Location> callerPath, NTuple<Location> calleeParamPath, Set<Descriptor> writeSet) {

    // propagate may-written-set associated with the key that is started with
    // calleepath to the caller
    // 1) makes a new key by combining caller path and callee path(except local
    // loc element of param)
    // 2) create new mapping of may-written-set of callee path to caller path

    // extract all may written effect accessed through callee param path
    MultiSourceMap<Location, Descriptor> mapping = mapMethodToSharedWriteMapping.get(callee);

    if (mapping == null) {
      return;
    }

    Hashtable<NTuple<Location>, Set<Descriptor>> paramMapping =
        mapping.getMappingByStartedWith(calleeParamPath);

    Set<NTuple<Location>> calleeKeySet = mapping.keySet();
    for (Iterator iterator = calleeKeySet.iterator(); iterator.hasNext();) {
      NTuple<Location> calleeKey = (NTuple<Location>) iterator.next();
      Set<Descriptor> calleeMayWriteSet = paramMapping.get(calleeKey);

      if (calleeMayWriteSet != null) {
        writeSet.addAll(calleeMayWriteSet);

        NTuple<Location> newKey = new NTuple<Location>();
        newKey.addAll(callerPath);
        // need to replace the local location with the caller's path so skip the
        // local location of the parameter
        for (int i = 1; i < calleeKey.size(); i++) {
          newKey.add(calleeKey.get(i));
        }

        mapLocationPathToMayWrittenSet.put(calleeKey, newKey, writeSet);
      }

    }

  }

  private void addSharedLocDescriptor(Location sharedLoc, Descriptor desc) {

    Set<Descriptor> descSet = mapSharedLocationToCoverSet.get(sharedLoc);
    if (descSet == null) {
      descSet = new HashSet<Descriptor>();
      mapSharedLocationToCoverSet.put(sharedLoc, descSet);
    }

    descSet.add(desc);

  }

  private boolean hasReadingEffectOnSharedLocation(MethodDescriptor md, NTuple<Descriptor> hp,
      Location loc, Descriptor d) {

    ReadSummary summary = mapMethodDescriptorToReadSummary.get(md);

    if (summary != null) {
      Hashtable<Location, Set<Descriptor>> map = summary.get(hp);
      if (map != null) {
        Set<Descriptor> descSec = map.get(loc);
        if (descSec != null) {
          return descSec.contains(d);
        }
      }
    }
    return false;

  }

  private Location getLocation(Descriptor d) {

    if (d instanceof FieldDescriptor) {
      TypeExtension te = ((FieldDescriptor) d).getType().getExtension();
      if (te != null) {
        return (Location) te;
      }
    } else {
      assert d instanceof TempDescriptor;
      TempDescriptor td = (TempDescriptor) d;

      TypeExtension te = td.getType().getExtension();
      if (te != null) {
        if (te instanceof SSJavaType) {
          SSJavaType ssType = (SSJavaType) te;
          CompositeLocation comp = ssType.getCompLoc();
          return comp.get(comp.getSize() - 1);
        } else {
          return (Location) te;
        }
      }
    }

    return mapDescToLocation.get(d);
  }

  private void writeLocation(MethodDescriptor md, ClearingSummary curr, NTuple<Descriptor> hp,
      Location loc, Descriptor d) {

    SharedStatus state = getState(curr, hp);
    if (loc != null && hasReadingEffectOnSharedLocation(md, hp, loc, d)) {
      // 1. add field x to the clearing set

      state.addVar(loc, d);

      // 3. if the set v contains all of variables belonging to the shared
      // location, set flag to true
      if (isOverWrittenAllDescsOfSharedLoc(md, hp, loc, state.getVarSet(loc))) {
        state.updateFlag(loc, true);
      }
    }
    state.setWriteEffect(loc);

  }

  private boolean isOverWrittenAllDescsOfSharedLoc(MethodDescriptor md, NTuple<Descriptor> hp,
      Location loc, Set<Descriptor> writtenSet) {

    ReadSummary summary = mapMethodDescriptorToReadSummary.get(md);

    if (summary != null) {
      Hashtable<Location, Set<Descriptor>> map = summary.get(hp);
      if (map != null) {
        Set<Descriptor> descSet = map.get(loc);
        if (descSet != null) {
          return writtenSet.containsAll(descSet);
        }
      }
    }
    return false;
  }

  private void readLocation(MethodDescriptor md, ClearingSummary curr, NTuple<Descriptor> hp,
      Location loc, Descriptor d) {
    // remove reading var x from written set
    if (loc != null && hasReadingEffectOnSharedLocation(md, hp, loc, d)) {
      SharedStatus state = getState(curr, hp);
      state.removeVar(loc, d);
    }
  }

  private SharedStatus getState(ClearingSummary curr, NTuple<Descriptor> hp) {
    SharedStatus state = curr.get(hp);
    if (state == null) {
      state = new SharedStatus();
      curr.put(hp, state);
    }
    return state;
  }

  private void eventLoopAnalysis() {
    // perform second stage analysis: intraprocedural analysis ensure that
    // all
    // variables are definitely written in-between the same read

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(ssjavaLoopEntrance);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Hashtable<NTuple<Descriptor>, Set<WriteAge>> prev = mapFlatNodetoEventLoopMap.get(fn);

      Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr =
          new Hashtable<NTuple<Descriptor>, Set<WriteAge>>();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        Hashtable<NTuple<Descriptor>, Set<WriteAge>> in = mapFlatNodetoEventLoopMap.get(nn);
        if (in != null) {
          union(curr, in);
        }
      }

      eventLoopAnalysis_nodeAction(fn, curr, ssjavaLoopEntrance);

      // if a new result, schedule forward nodes for analysis
      if (!curr.equals(prev)) {
        mapFlatNodetoEventLoopMap.put(fn, curr);

        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          if (loopIncElements.contains(nn)) {
            flatNodesToVisit.add(nn);
          }

        }
      }
    }
  }

  private void union(Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> in) {

    Set<NTuple<Descriptor>> inKeySet = in.keySet();
    for (Iterator iterator = inKeySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> inKey = (NTuple<Descriptor>) iterator.next();
      Set<WriteAge> inSet = in.get(inKey);

      Set<WriteAge> currSet = curr.get(inKey);

      if (currSet == null) {
        currSet = new HashSet<WriteAge>();
        curr.put(inKey, currSet);
      }
      currSet.addAll(inSet);
    }

  }

  private void eventLoopAnalysis_nodeAction(FlatNode fn,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr, FlatNode loopEntrance) {

    Hashtable<NTuple<Descriptor>, Set<WriteAge>> readWriteKillSet =
        new Hashtable<NTuple<Descriptor>, Set<WriteAge>>();
    Hashtable<NTuple<Descriptor>, Set<WriteAge>> readWriteGenSet =
        new Hashtable<NTuple<Descriptor>, Set<WriteAge>>();

    if (fn.equals(loopEntrance)) {
      // it reaches loop entrance: changes all flag to true
      Set<NTuple<Descriptor>> keySet = curr.keySet();
      for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> key = (NTuple<Descriptor>) iterator.next();
        Set<WriteAge> writeAgeSet = curr.get(key);

        Set<WriteAge> incSet = new HashSet<WriteAge>();
        incSet.addAll(writeAgeSet);
        writeAgeSet.clear();

        for (Iterator iterator2 = incSet.iterator(); iterator2.hasNext();) {
          WriteAge writeAge = (WriteAge) iterator2.next();
          WriteAge newWriteAge = writeAge.copy();
          newWriteAge.inc();
          writeAgeSet.add(newWriteAge);
        }

      }
      // System.out.println("EVENT LOOP ENTRY=" + curr);

    } else {
      TempDescriptor lhs;
      TempDescriptor rhs;
      FieldDescriptor fld;

      switch (fn.kind()) {

      case FKind.FlatOpNode: {
        FlatOpNode fon = (FlatOpNode) fn;
        lhs = fon.getDest();
        rhs = fon.getLeft();

        if (!lhs.getSymbol().startsWith("neverused")) {
          NTuple<Descriptor> rhsHeapPath = computePath(rhs);
          if (!rhs.getType().isImmutable()) {
            mapHeapPath.put(lhs, rhsHeapPath);
          } else {
            // write(lhs)
            // NTuple<Descriptor> lhsHeapPath = computePath(lhs);
            NTuple<Descriptor> path = new NTuple<Descriptor>();
            path.add(lhs);

            // System.out.println("WRITE VARIABLE=" + path + " from=" + lhs);

            computeKILLSetForWrite(curr, path, readWriteKillSet);
            computeGENSetForWrite(path, readWriteGenSet);

            // System.out.println("#VARIABLE WRITE:" + fn);
            // System.out.println("#KILLSET=" + KILLSet);
            // System.out.println("#GENSet=" + GENSet);

          }
        }

      }
        break;

      case FKind.FlatFieldNode:
      case FKind.FlatElementNode: {

        if (fn.kind() == FKind.FlatFieldNode) {
          FlatFieldNode ffn = (FlatFieldNode) fn;
          lhs = ffn.getDst();
          rhs = ffn.getSrc();
          fld = ffn.getField();
        } else {
          FlatElementNode fen = (FlatElementNode) fn;
          lhs = fen.getDst();
          rhs = fen.getSrc();
          TypeDescriptor td = rhs.getType().dereference();
          fld = getArrayField(td);
        }

        // read field
        NTuple<Descriptor> srcHeapPath = mapHeapPath.get(rhs);
        NTuple<Descriptor> fldHeapPath;
        if (srcHeapPath != null) {
          fldHeapPath = new NTuple<Descriptor>(srcHeapPath.getList());
        } else {
          // if srcHeapPath is null, it is static reference
          fldHeapPath = new NTuple<Descriptor>();
          fldHeapPath.add(rhs);
        }
        fldHeapPath.add(fld);

        Set<WriteAge> writeAgeSet = curr.get(fldHeapPath);
        checkWriteAgeSet(writeAgeSet, fldHeapPath, fn);

      }
        break;

      case FKind.FlatSetFieldNode:
      case FKind.FlatSetElementNode: {

        if (fn.kind() == FKind.FlatSetFieldNode) {
          FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
          lhs = fsfn.getDst();
          fld = fsfn.getField();
        } else {
          FlatSetElementNode fsen = (FlatSetElementNode) fn;
          lhs = fsen.getDst();
          rhs = fsen.getSrc();
          TypeDescriptor td = lhs.getType().dereference();
          fld = getArrayField(td);
        }

        // write(field)
        NTuple<Descriptor> lhsHeapPath = computePath(lhs);
        NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>(lhsHeapPath.getList());
        fldHeapPath.add(fld);

        computeKILLSetForWrite(curr, fldHeapPath, readWriteKillSet);
        computeGENSetForWrite(fldHeapPath, readWriteGenSet);

        // System.out.println("FIELD WRITE:" + fn);
        // System.out.println("KILLSET=" + KILLSet);
        // System.out.println("GENSet=" + GENSet);

      }
        break;

      case FKind.FlatCall: {
        FlatCall fc = (FlatCall) fn;

        // System.out.println("FLATCALL:" + fn);

        generateKILLSetForFlatCall(fc, curr, readWriteKillSet);
        generateGENSetForFlatCall(fc, readWriteGenSet);

        checkManyRead(fc, curr);

        // System.out.println("KILLSET=" + readWriteKillSet);
        // System.out.println("GENSet=" + readWriteGenSet);

      }
        break;

      }

      computeNewMapping(curr, readWriteKillSet, readWriteGenSet);
      // System.out.println("#######" + curr);

    }

  }

  private void checkManyRead(FlatCall fc, Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr) {

    Set<NTuple<Descriptor>> boundReadSet = mapFlatNodeToBoundReadSet.get(fc);

    for (Iterator iterator = boundReadSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> readHeapPath = (NTuple<Descriptor>) iterator.next();
      Set<WriteAge> writeAgeSet = curr.get(readHeapPath);
      checkWriteAgeSet(writeAgeSet, readHeapPath, fc);
    }

  }

  private void checkWriteAgeSet(Set<WriteAge> writeAgeSet, NTuple<Descriptor> path, FlatNode fn) {
    if (writeAgeSet != null) {
      for (Iterator iterator = writeAgeSet.iterator(); iterator.hasNext();) {
        WriteAge writeAge = (WriteAge) iterator.next();
        if (writeAge.getAge() >= MAXAGE) {
          throw new Error(
              "Memory location, which is reachable through references "
                  + path
                  + ", who comes back to the same read statement without being overwritten at the out-most iteration at "
                  + methodContainingSSJavaLoop.getClassDesc().getSourceFileName() + "::"
                  + fn.getNumLine());
        }
      }
    }
  }

  private void generateGENSetForFlatCall(FlatCall fc,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> GENSet) {

    Set<NTuple<Descriptor>> boundMayWriteSet = mapFlatNodeToBoundMayWriteSet.get(fc);

    for (Iterator iterator = boundMayWriteSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> key = (NTuple<Descriptor>) iterator.next();
      // TODO: shared location
      Set<WriteAge> set = new HashSet<WriteAge>();
      set.add(new WriteAge(0));
      GENSet.put(key, set);
    }

  }

  private void generateKILLSetForFlatCall(FlatCall fc,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> KILLSet) {

    Set<NTuple<Descriptor>> boundMustWriteSet = mapFlatNodeToBoundMustWriteSet.get(fc);

    for (Iterator iterator = boundMustWriteSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> key = (NTuple<Descriptor>) iterator.next();
      // TODO: shared location
      if (curr.get(key) != null) {
        KILLSet.put(key, curr.get(key));
      }
    }

  }

  private void computeNewMapping(Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> KILLSet,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> GENSet) {

    for (Enumeration<NTuple<Descriptor>> e = KILLSet.keys(); e.hasMoreElements();) {
      NTuple<Descriptor> key = e.nextElement();

      Set<WriteAge> writeAgeSet = curr.get(key);
      if (writeAgeSet == null) {
        writeAgeSet = new HashSet<WriteAge>();
        curr.put(key, writeAgeSet);
      }
      writeAgeSet.removeAll(KILLSet.get(key));
    }

    for (Enumeration<NTuple<Descriptor>> e = GENSet.keys(); e.hasMoreElements();) {
      NTuple<Descriptor> key = e.nextElement();

      Set<WriteAge> currWriteAgeSet = curr.get(key);
      if (currWriteAgeSet == null) {
        currWriteAgeSet = new HashSet<WriteAge>();
        curr.put(key, currWriteAgeSet);
      }
      currWriteAgeSet.addAll(GENSet.get(key));
    }

  }

  private void computeGENSetForWrite(NTuple<Descriptor> fldHeapPath,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> GENSet) {

    // generate write age 0 for the field being written to
    Set<WriteAge> writeAgeSet = new HashSet<WriteAge>();
    writeAgeSet.add(new WriteAge(0));
    GENSet.put(fldHeapPath, writeAgeSet);

  }

  private void readValue(FlatNode fn, NTuple<Descriptor> hp,
      Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>> curr) {
    Hashtable<FlatNode, Boolean> gen = curr.get(hp);
    if (gen == null) {
      gen = new Hashtable<FlatNode, Boolean>();
      curr.put(hp, gen);
    }
    Boolean currentStatus = gen.get(fn);
    if (currentStatus == null) {
      gen.put(fn, Boolean.FALSE);
    } else {
      checkFlag(currentStatus.booleanValue(), fn, hp);
    }

  }

  private void computeKILLSetForWrite(Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr,
      NTuple<Descriptor> hp, Hashtable<NTuple<Descriptor>, Set<WriteAge>> KILLSet) {

    // removes all of heap path that starts with prefix 'hp'
    // since any reference overwrite along heap path gives overwriting side
    // effects on the value

    Set<NTuple<Descriptor>> keySet = curr.keySet();
    for (Iterator<NTuple<Descriptor>> iter = keySet.iterator(); iter.hasNext();) {
      NTuple<Descriptor> key = iter.next();
      if (key.startsWith(hp)) {
        KILLSet.put(key, curr.get(key));
      }
    }

  }

  private void bindHeapPathCallerArgWithCalleeParam(FlatCall fc) {
    // compute all possible callee set
    // transform all READ/WRITE set from the any possible
    // callees to the caller
    calleeUnionBoundReadSet.clear();
    calleeIntersectBoundMustWriteSet.clear();
    calleeUnionBoundMayWriteSet.clear();

    if (ssjava.isSSJavaUtil(fc.getMethod().getClassDesc())) {
      // ssjava util case!
      // have write effects on the first argument
      TempDescriptor arg = fc.getArg(0);
      NTuple<Descriptor> argHeapPath = computePath(arg);
      calleeIntersectBoundMustWriteSet.add(argHeapPath);
      calleeUnionBoundMayWriteSet.add(argHeapPath);
    } else {
      MethodDescriptor mdCallee = fc.getMethod();
      Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
      setPossibleCallees.addAll(callGraph.getMethods(mdCallee));

      // create mapping from arg idx to its heap paths
      Hashtable<Integer, NTuple<Descriptor>> mapArgIdx2CallerArgHeapPath =
          new Hashtable<Integer, NTuple<Descriptor>>();

      // arg idx is starting from 'this' arg
      if (fc.getThis() != null) {
        NTuple<Descriptor> thisHeapPath = mapHeapPath.get(fc.getThis());
        if (thisHeapPath == null) {
          // method is called without creating new flat node representing 'this'
          thisHeapPath = new NTuple<Descriptor>();
          thisHeapPath.add(fc.getThis());
        }

        mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(0), thisHeapPath);
      }

      for (int i = 0; i < fc.numArgs(); i++) {
        TempDescriptor arg = fc.getArg(i);
        NTuple<Descriptor> argHeapPath = computePath(arg);
        mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(i + 1), argHeapPath);
      }

      for (Iterator iterator = setPossibleCallees.iterator(); iterator.hasNext();) {
        MethodDescriptor callee = (MethodDescriptor) iterator.next();
        FlatMethod calleeFlatMethod = state.getMethodFlat(callee);

        // binding caller's args and callee's params

        Set<NTuple<Descriptor>> calleeReadSet = mapFlatMethodToReadSet.get(calleeFlatMethod);
        if (calleeReadSet == null) {
          calleeReadSet = new HashSet<NTuple<Descriptor>>();
          mapFlatMethodToReadSet.put(calleeFlatMethod, calleeReadSet);
        }

        Set<NTuple<Descriptor>> calleeMustWriteSet =
            mapFlatMethodToMustWriteSet.get(calleeFlatMethod);

        if (calleeMustWriteSet == null) {
          calleeMustWriteSet = new HashSet<NTuple<Descriptor>>();
          mapFlatMethodToMustWriteSet.put(calleeFlatMethod, calleeMustWriteSet);
        }

        Set<NTuple<Descriptor>> calleeMayWriteSet =
            mapFlatMethodToMayWriteSet.get(calleeFlatMethod);

        if (calleeMayWriteSet == null) {
          calleeMayWriteSet = new HashSet<NTuple<Descriptor>>();
          mapFlatMethodToMayWriteSet.put(calleeFlatMethod, calleeMayWriteSet);
        }

        Hashtable<Integer, TempDescriptor> mapParamIdx2ParamTempDesc =
            new Hashtable<Integer, TempDescriptor>();
        int offset = 0;
        if (calleeFlatMethod.getMethod().isStatic()) {
          // static method does not have implicit 'this' arg
          offset = 1;
        }
        for (int i = 0; i < calleeFlatMethod.numParameters(); i++) {
          TempDescriptor param = calleeFlatMethod.getParameter(i);
          mapParamIdx2ParamTempDesc.put(Integer.valueOf(i + offset), param);
        }

        Set<NTuple<Descriptor>> calleeBoundReadSet =
            bindSet(calleeReadSet, mapParamIdx2ParamTempDesc, mapArgIdx2CallerArgHeapPath);
        // union of the current read set and the current callee's
        // read set
        calleeUnionBoundReadSet.addAll(calleeBoundReadSet);

        Set<NTuple<Descriptor>> calleeBoundMustWriteSet =
            bindSet(calleeMustWriteSet, mapParamIdx2ParamTempDesc, mapArgIdx2CallerArgHeapPath);
        // intersection of the current overwrite set and the current
        // callee's
        // overwrite set
        merge(calleeIntersectBoundMustWriteSet, calleeBoundMustWriteSet);

        Set<NTuple<Descriptor>> boundWriteSetFromCallee =
            bindSet(calleeMayWriteSet, mapParamIdx2ParamTempDesc, mapArgIdx2CallerArgHeapPath);
        calleeUnionBoundMayWriteSet.addAll(boundWriteSetFromCallee);
      }

    }

  }

  private void bindHeapPathCallerArgWithCaleeParamForSharedLoc(MethodDescriptor mdCaller,
      FlatCall fc) {

    calleeIntersectBoundSharedSet.clear();
    calleeUnionBoundDeleteSet.clear();

    // if arg is not primitive type, we need to propagate maywritten set to
    // the caller's location path

    MethodDescriptor mdCallee = fc.getMethod();
    Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
    setPossibleCallees.addAll(callGraph.getMethods(mdCallee));

    // create mapping from arg idx to its heap paths
    Hashtable<Integer, NTuple<Descriptor>> mapArgIdx2CallerArgHeapPath =
        new Hashtable<Integer, NTuple<Descriptor>>();

    // arg idx is starting from 'this' arg
    if (fc.getThis() != null) {
      NTuple<Descriptor> thisHeapPath = mapHeapPath.get(fc.getThis());
      if (thisHeapPath == null) {
        // method is called without creating new flat node representing 'this'
        thisHeapPath = new NTuple<Descriptor>();
        thisHeapPath.add(fc.getThis());
      }

      mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(0), thisHeapPath);
    }

    for (int i = 0; i < fc.numArgs(); i++) {
      TempDescriptor arg = fc.getArg(i);
      NTuple<Descriptor> argHeapPath = computePath(arg);
      mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(i + 1), argHeapPath);
    }

    // create mapping from arg idx to its location paths
    Hashtable<Integer, NTuple<Location>> mapArgIdx2CallerAgLocationPath =
        new Hashtable<Integer, NTuple<Location>>();

    // arg idx is starting from 'this' arg
    if (fc.getThis() != null) {
      NTuple<Location> thisLocationPath = deriveLocationTuple(mdCaller, fc.getThis());
      mapArgIdx2CallerAgLocationPath.put(Integer.valueOf(0), thisLocationPath);
    }

    for (int i = 0; i < fc.numArgs(); i++) {
      TempDescriptor arg = fc.getArg(i);
      NTuple<Location> argLocationPath = deriveLocationTuple(mdCaller, arg);
      mapArgIdx2CallerAgLocationPath.put(Integer.valueOf(i + 1), argLocationPath);
    }

    for (Iterator iterator = setPossibleCallees.iterator(); iterator.hasNext();) {
      MethodDescriptor callee = (MethodDescriptor) iterator.next();
      FlatMethod calleeFlatMethod = state.getMethodFlat(callee);

      // binding caller's args and callee's params

      Hashtable<Integer, TempDescriptor> mapParamIdx2ParamTempDesc =
          new Hashtable<Integer, TempDescriptor>();
      int offset = 0;
      if (calleeFlatMethod.getMethod().isStatic()) {
        // static method does not have implicit 'this' arg
        offset = 1;
      }
      for (int i = 0; i < calleeFlatMethod.numParameters(); i++) {
        TempDescriptor param = calleeFlatMethod.getParameter(i);
        mapParamIdx2ParamTempDesc.put(Integer.valueOf(i + offset), param);
      }

      Set<Integer> keySet = mapArgIdx2CallerAgLocationPath.keySet();
      for (Iterator iterator2 = keySet.iterator(); iterator2.hasNext();) {
        Integer idx = (Integer) iterator2.next();
        NTuple<Location> callerArgLocationPath = mapArgIdx2CallerAgLocationPath.get(idx);
        NTuple<Descriptor> callerArgHeapPath = mapArgIdx2CallerArgHeapPath.get(idx);

        TempDescriptor calleeParam = mapParamIdx2ParamTempDesc.get(idx);
        NTuple<Location> calleeLocationPath = deriveLocationTuple(mdCallee, calleeParam);
        SharedLocMap calleeDeleteSet = mapFlatMethodToDeleteSet.get(calleeFlatMethod);
        SharedLocMap calleeSharedLocMap = mapFlatMethodToSharedLocMap.get(calleeFlatMethod);

        if (calleeDeleteSet != null) {
          createNewMappingOfDeleteSet(callerArgLocationPath, callerArgHeapPath, calleeLocationPath,
              calleeDeleteSet);
        }

        if (calleeSharedLocMap != null) {
          createNewMappingOfSharedSet(callerArgLocationPath, callerArgHeapPath, calleeLocationPath,
              calleeSharedLocMap);
        }

      }

    }

  }

  private void createNewMappingOfDeleteSet(NTuple<Location> callerArgLocationPath,
      NTuple<Descriptor> callerArgHeapPath, NTuple<Location> calleeLocationPath,
      SharedLocMap calleeDeleteSet) {

    SharedLocMap calleeParamDeleteSet = calleeDeleteSet.getHeapPathStartedWith(calleeLocationPath);

    Set<NTuple<Location>> keySet = calleeParamDeleteSet.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      NTuple<Location> calleeLocTupleKey = (NTuple<Location>) iterator.next();
      Set<NTuple<Descriptor>> heapPathSet = calleeParamDeleteSet.get(calleeLocTupleKey);
      for (Iterator iterator2 = heapPathSet.iterator(); iterator2.hasNext();) {
        NTuple<Descriptor> calleeHeapPath = (NTuple<Descriptor>) iterator2.next();
        calleeUnionBoundDeleteSet.addWrite(
            bindLocationPath(callerArgLocationPath, calleeLocTupleKey),
            bindHeapPath(callerArgHeapPath, calleeHeapPath));
      }
    }

  }

  private void createNewMappingOfSharedSet(NTuple<Location> callerArgLocationPath,
      NTuple<Descriptor> callerArgHeapPath, NTuple<Location> calleeLocationPath,
      SharedLocMap calleeSharedLocMap) {

    SharedLocMap calleeParamSharedSet =
        calleeSharedLocMap.getHeapPathStartedWith(calleeLocationPath);

    Set<NTuple<Location>> keySet = calleeParamSharedSet.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      NTuple<Location> calleeLocTupleKey = (NTuple<Location>) iterator.next();
      Set<NTuple<Descriptor>> heapPathSet = calleeParamSharedSet.get(calleeLocTupleKey);
      Set<NTuple<Descriptor>> boundHeapPathSet = new HashSet<NTuple<Descriptor>>();
      for (Iterator iterator2 = heapPathSet.iterator(); iterator2.hasNext();) {
        NTuple<Descriptor> calleeHeapPath = (NTuple<Descriptor>) iterator2.next();
        boundHeapPathSet.add(bindHeapPath(callerArgHeapPath, calleeHeapPath));
      }
      calleeIntersectBoundSharedSet.intersect(
          bindLocationPath(callerArgLocationPath, calleeLocTupleKey), boundHeapPathSet);
    }

  }

  private NTuple<Location> bindLocationPath(NTuple<Location> start, NTuple<Location> end) {
    NTuple<Location> locPath = new NTuple<Location>();
    locPath.addAll(start);
    for (int i = 1; i < end.size(); i++) {
      locPath.add(end.get(i));
    }
    return locPath;
  }

  private NTuple<Descriptor> bindHeapPath(NTuple<Descriptor> start, NTuple<Descriptor> end) {
    NTuple<Descriptor> heapPath = new NTuple<Descriptor>();
    heapPath.addAll(start);
    for (int i = 1; i < end.size(); i++) {
      heapPath.add(end.get(i));
    }
    return heapPath;
  }

  private NTuple<Descriptor> bind(NTuple<Descriptor> calleeHeapPathKey,
      Hashtable<Integer, TempDescriptor> mapParamIdx2ParamTempDesc,
      Hashtable<Integer, NTuple<Descriptor>> mapCallerArgIdx2HeapPath) {

    Set<Integer> keySet = mapCallerArgIdx2HeapPath.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Integer idx = (Integer) iterator.next();
      NTuple<Descriptor> callerArgHeapPath = mapCallerArgIdx2HeapPath.get(idx);
      TempDescriptor calleeParam = mapParamIdx2ParamTempDesc.get(idx);
      if (calleeHeapPathKey.startsWith(calleeParam)) {
        NTuple<Descriptor> boundElement = combine(callerArgHeapPath, calleeHeapPathKey);
        return boundElement;
      }
    }
    return null;
  }

  private void checkFlag(boolean booleanValue, FlatNode fn, NTuple<Descriptor> hp) {
    if (booleanValue) {
      // the definitely written analysis only takes care about locations that
      // are written to inside of the SSJava loop
      for (Iterator iterator = calleeUnionBoundMayWriteSet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> write = (NTuple<Descriptor>) iterator.next();
        if (hp.startsWith(write)) {
          // it has write effect!
          // throw new Error(
          System.out
              .println("###"
                  + "There is a variable, which is reachable through references "
                  + hp
                  + ", who comes back to the same read statement without being overwritten at the out-most iteration at "
                  + methodContainingSSJavaLoop.getClassDesc().getSourceFileName() + "::"
                  + fn.getNumLine());
          debugcount++;
        }
      }
    }
  }

  private void initialize() {
    // First, identify ssjava loop entrace

    // no need to analyze method having ssjava loop
    methodContainingSSJavaLoop = ssjava.getMethodContainingSSJavaLoop();

    FlatMethod fm = state.getMethodFlat(methodContainingSSJavaLoop);
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    LoopFinder loopFinder = new LoopFinder(fm);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      String label = (String) state.fn2labelMap.get(fn);
      if (label != null) {

        if (label.equals(ssjava.SSJAVA)) {
          ssjavaLoopEntrance = fn;
          break;
        }
      }

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);
        flatNodesToVisit.add(nn);
      }
    }

    assert ssjavaLoopEntrance != null;

    // assume that ssjava loop is top-level loop in method, not nested loop
    Set nestedLoop = loopFinder.nestedLoops();
    for (Iterator loopIter = nestedLoop.iterator(); loopIter.hasNext();) {
      LoopFinder lf = (LoopFinder) loopIter.next();
      if (lf.loopEntrances().iterator().next().equals(ssjavaLoopEntrance)) {
        ssjavaLoop = lf;
      }
    }

    assert ssjavaLoop != null;

    loopIncElements = (Set<FlatNode>) ssjavaLoop.loopIncElements();

    // perform topological sort over the set of methods accessed by the main
    // event loop
    Set<MethodDescriptor> methodDescriptorsToAnalyze = new HashSet<MethodDescriptor>();
    methodDescriptorsToAnalyze.addAll(ssjava.getAnnotationRequireSet());
    sortedDescriptors = topologicalSort(methodDescriptorsToAnalyze);
  }

  private void methodReadWriteSetAnalysis() {
    // perform method READ/OVERWRITE analysis
    LinkedList<MethodDescriptor> descriptorListToAnalyze =
        (LinkedList<MethodDescriptor>) sortedDescriptors.clone();

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by
    // dependency in the call graph
    methodDescriptorsToVisitStack.clear();

    descriptorListToAnalyze.removeFirst();

    Set<MethodDescriptor> methodDescriptorToVistSet = new HashSet<MethodDescriptor>();
    methodDescriptorToVistSet.addAll(descriptorListToAnalyze);

    while (!descriptorListToAnalyze.isEmpty()) {
      MethodDescriptor md = descriptorListToAnalyze.removeFirst();
      methodDescriptorsToVisitStack.add(md);
    }

    // analyze scheduled methods until there are no more to visit
    while (!methodDescriptorsToVisitStack.isEmpty()) {
      // start to analyze leaf node
      MethodDescriptor md = methodDescriptorsToVisitStack.pop();
      FlatMethod fm = state.getMethodFlat(md);

      Set<NTuple<Descriptor>> readSet = new HashSet<NTuple<Descriptor>>();
      Set<NTuple<Descriptor>> mustWriteSet = new HashSet<NTuple<Descriptor>>();
      Set<NTuple<Descriptor>> mayWriteSet = new HashSet<NTuple<Descriptor>>();

      methodReadWriteSet_analyzeMethod(fm, readSet, mustWriteSet, mayWriteSet);

      Set<NTuple<Descriptor>> prevRead = mapFlatMethodToReadSet.get(fm);
      Set<NTuple<Descriptor>> prevMustWrite = mapFlatMethodToMustWriteSet.get(fm);
      Set<NTuple<Descriptor>> prevMayWrite = mapFlatMethodToMayWriteSet.get(fm);

      if (!(readSet.equals(prevRead) && mustWriteSet.equals(prevMustWrite) && mayWriteSet
          .equals(prevMayWrite))) {
        mapFlatMethodToReadSet.put(fm, readSet);
        mapFlatMethodToMustWriteSet.put(fm, mustWriteSet);
        mapFlatMethodToMayWriteSet.put(fm, mayWriteSet);

        // results for callee changed, so enqueue dependents caller for
        // further
        // analysis
        Iterator<MethodDescriptor> depsItr = getDependents(md).iterator();
        while (depsItr.hasNext()) {
          MethodDescriptor methodNext = depsItr.next();
          if (!methodDescriptorsToVisitStack.contains(methodNext)
              && methodDescriptorToVistSet.contains(methodNext)) {
            methodDescriptorsToVisitStack.add(methodNext);
          }

        }

      }

    }

    methodReadWriteSetAnalysisToEventLoopBody();

  }

  private void methodReadWriteSet_analyzeMethod(FlatMethod fm, Set<NTuple<Descriptor>> readSet,
      Set<NTuple<Descriptor>> mustWriteSet, Set<NTuple<Descriptor>> mayWriteSet) {
    if (state.SSJAVADEBUG) {
      System.out.println("SSJAVA: Definitely written Analyzing: " + fm);
    }

    methodReadWriteSet_analyzeBody(fm, readSet, mustWriteSet, mayWriteSet, false);

  }

  private void methodReadWriteSetAnalysisToEventLoopBody() {

    // perform method read/write analysis for Event Loop Body

    FlatMethod flatMethodContainingSSJavaLoop = state.getMethodFlat(methodContainingSSJavaLoop);

    if (state.SSJAVADEBUG) {
      System.out.println("SSJAVA: Definitely written Event Loop Analyzing: "
          + flatMethodContainingSSJavaLoop);
    }

    Set<NTuple<Descriptor>> readSet = new HashSet<NTuple<Descriptor>>();
    Set<NTuple<Descriptor>> mustWriteSet = new HashSet<NTuple<Descriptor>>();
    Set<NTuple<Descriptor>> mayWriteSet = new HashSet<NTuple<Descriptor>>();

    mapFlatMethodToReadSet.put(flatMethodContainingSSJavaLoop, readSet);
    mapFlatMethodToMustWriteSet.put(flatMethodContainingSSJavaLoop, mustWriteSet);
    mapFlatMethodToMayWriteSet.put(flatMethodContainingSSJavaLoop, mayWriteSet);

    methodReadWriteSet_analyzeBody(ssjavaLoopEntrance, readSet, mustWriteSet, mayWriteSet, true);

  }

  private void methodReadWriteSet_analyzeBody(FlatNode startNode, Set<NTuple<Descriptor>> readSet,
      Set<NTuple<Descriptor>> mustWriteSet, Set<NTuple<Descriptor>> mayWriteSet,
      boolean isEventLoopBody) {

    // intraprocedural analysis
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(startNode);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Set<NTuple<Descriptor>> currMustWriteSet = new HashSet<NTuple<Descriptor>>();

      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode prevFn = fn.getPrev(i);
        Set<NTuple<Descriptor>> in = mapFlatNodeToMustWriteSet.get(prevFn);
        if (in != null) {
          merge(currMustWriteSet, in);
        }
      }

      methodReadWriteSet_nodeActions(fn, currMustWriteSet, readSet, mustWriteSet, mayWriteSet,
          isEventLoopBody);

      Set<NTuple<Descriptor>> mustSetPrev = mapFlatNodeToMustWriteSet.get(fn);

      if (!currMustWriteSet.equals(mustSetPrev)) {
        mapFlatNodeToMustWriteSet.put(fn, currMustWriteSet);
        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          if ((!isEventLoopBody) || loopIncElements.contains(nn)) {
            flatNodesToVisit.add(nn);
          }

        }
      }

    }

  }

  private void methodReadWriteSet_nodeActions(FlatNode fn,
      Set<NTuple<Descriptor>> currMustWriteSet, Set<NTuple<Descriptor>> readSet,
      Set<NTuple<Descriptor>> mustWriteSet, Set<NTuple<Descriptor>> mayWriteSet,
      boolean isEventLoopBody) {

    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;

    switch (fn.kind()) {
    case FKind.FlatMethod: {

      // set up initial heap paths for method parameters
      FlatMethod fm = (FlatMethod) fn;
      for (int i = 0; i < fm.numParameters(); i++) {
        TempDescriptor param = fm.getParameter(i);
        NTuple<Descriptor> heapPath = new NTuple<Descriptor>();
        heapPath.add(param);
        mapHeapPath.put(param, heapPath);
      }
    }
      break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;
      // for a normal assign node, need to propagate lhs's heap path to
      // rhs
      if (fon.getOp().getOp() == Operation.ASSIGN) {
        rhs = fon.getLeft();
        lhs = fon.getDest();

        NTuple<Descriptor> rhsHeapPath = mapHeapPath.get(rhs);
        if (rhsHeapPath != null) {
          mapHeapPath.put(lhs, mapHeapPath.get(rhs));
        } else {
          NTuple<Descriptor> heapPath = new NTuple<Descriptor>();
          heapPath.add(rhs);
          mapHeapPath.put(lhs, heapPath);
        }

        // shared loc extension
        if (isEventLoopBody) {
          if (!lhs.getSymbol().startsWith("neverused") && rhs.getType().isImmutable()) {

            if (rhs.getType().getExtension() instanceof Location
                && lhs.getType().getExtension() instanceof CompositeLocation) {
              // rhs is field!
              Location rhsLoc = (Location) rhs.getType().getExtension();

              CompositeLocation lhsCompLoc = (CompositeLocation) lhs.getType().getExtension();
              Location dstLoc = lhsCompLoc.get(lhsCompLoc.getSize() - 1);

              NTuple<Descriptor> heapPath = new NTuple<Descriptor>();
              for (int i = 0; i < rhsHeapPath.size() - 1; i++) {
                heapPath.add(rhsHeapPath.get(i));
              }

              NTuple<Descriptor> writeHeapPath = new NTuple<Descriptor>();
              writeHeapPath.addAll(heapPath);
              writeHeapPath.add(lhs);

              System.out.println("VAR WRITE:" + fn);
              System.out.println("LHS TYPE EXTENSION=" + lhs.getType().getExtension());
              System.out.println("RHS TYPE EXTENSION=" + rhs.getType().getExtension()
                  + " HEAPPATH=" + rhsHeapPath);

              // TODO
              // computing gen/kill set
              // computeKILLSetForWrite(currSharedLocMapping, heapPath, dstLoc,
              // killSetSharedLoc);
              // if (!dstLoc.equals(rhsLoc)) {
              // computeGENSetForHigherWrite(currSharedLocMapping, heapPath,
              // dstLoc, lhs,
              // genSetSharedLoc);
              // deleteSet.remove(writeHeapPath);
              // } else {
              // computeGENSetForSharedWrite(currSharedLocMapping, heapPath,
              // dstLoc, lhs,
              // genSetSharedLoc);
              // deleteSet.add(writeHeapPath);
              // }

            }
          }
        }

      }
    }
      break;

    case FKind.FlatElementNode:
    case FKind.FlatFieldNode: {

      // x=y.f;

      if (fn.kind() == FKind.FlatFieldNode) {
        FlatFieldNode ffn = (FlatFieldNode) fn;
        lhs = ffn.getDst();
        rhs = ffn.getSrc();
        fld = ffn.getField();
      } else {
        FlatElementNode fen = (FlatElementNode) fn;
        lhs = fen.getDst();
        rhs = fen.getSrc();
        TypeDescriptor td = rhs.getType().dereference();
        fld = getArrayField(td);
      }

      if (fld.isFinal()) {
        // if field is final no need to check
        break;
      }

      // set up heap path
      NTuple<Descriptor> srcHeapPath = mapHeapPath.get(rhs);
      if (srcHeapPath != null) {
        // if lhs srcHeapPath is null, it means that it is not reachable from
        // callee's parameters. so just ignore it

        NTuple<Descriptor> readingHeapPath = new NTuple<Descriptor>(srcHeapPath.getList());
        readingHeapPath.add(fld);
        mapHeapPath.put(lhs, readingHeapPath);

        // read (x.f)
        if (fld.getType().isImmutable()) {
          // if WT doesnot have hp(x.f), add hp(x.f) to READ
          if (!currMustWriteSet.contains(readingHeapPath)) {
            readSet.add(readingHeapPath);
          }
        }

        // no need to kill hp(x.f) from WT
      }

    }
      break;

    case FKind.FlatSetFieldNode:
    case FKind.FlatSetElementNode: {

      // x.f=y;

      if (fn.kind() == FKind.FlatSetFieldNode) {
        FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
        lhs = fsfn.getDst();
        fld = fsfn.getField();
        rhs = fsfn.getSrc();
      } else {
        FlatSetElementNode fsen = (FlatSetElementNode) fn;
        lhs = fsen.getDst();
        rhs = fsen.getSrc();
        TypeDescriptor td = lhs.getType().dereference();
        fld = getArrayField(td);
      }

      // set up heap path
      NTuple<Descriptor> lhsHeapPath = mapHeapPath.get(lhs);

      if (lhsHeapPath != null) {
        // if lhs heap path is null, it means that it is not reachable from
        // callee's parameters. so just ignore it
        NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>(lhsHeapPath.getList());
        fldHeapPath.add(fld);
        mapHeapPath.put(fld, fldHeapPath);

        // write(x.f)
        // need to add hp(y) to WT
        currMustWriteSet.add(fldHeapPath);
        mayWriteSet.add(fldHeapPath);

      }

    }
      break;

    case FKind.FlatCall: {

      FlatCall fc = (FlatCall) fn;

      bindHeapPathCallerArgWithCalleeParam(fc);

      mapFlatNodeToBoundReadSet.put(fn, calleeUnionBoundReadSet);
      mapFlatNodeToBoundMustWriteSet.put(fn, calleeIntersectBoundMustWriteSet);
      mapFlatNodeToBoundMayWriteSet.put(fn, calleeUnionBoundMayWriteSet);

      // add heap path, which is an element of READ_bound set and is not
      // an
      // element of WT set, to the caller's READ set
      for (Iterator iterator = calleeUnionBoundReadSet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> read = (NTuple<Descriptor>) iterator.next();
        if (!currMustWriteSet.contains(read)) {
          readSet.add(read);
        }
      }

      // add heap path, which is an element of OVERWRITE_bound set, to the
      // caller's WT set
      for (Iterator iterator = calleeIntersectBoundMustWriteSet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> write = (NTuple<Descriptor>) iterator.next();
        currMustWriteSet.add(write);
      }

      // add heap path, which is an element of WRITE_BOUND set, to the
      // caller's writeSet
      for (Iterator iterator = calleeUnionBoundMayWriteSet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> write = (NTuple<Descriptor>) iterator.next();
        mayWriteSet.add(write);
      }

    }
      break;

    case FKind.FlatExit: {
      // merge the current written set with OVERWRITE set
      merge(mustWriteSet, currMustWriteSet);
    }
      break;

    }

  }

  public NTuple<Descriptor> getPrefix(NTuple<Descriptor> in) {
    return in.subList(0, in.size() - 1);
  }

  public NTuple<Descriptor> getSuffix(NTuple<Descriptor> in) {
    return in.subList(in.size() - 1, in.size());
  }

  static public FieldDescriptor getArrayField(TypeDescriptor td) {
    FieldDescriptor fd = mapTypeToArrayField.get(td);
    if (fd == null) {
      fd =
          new FieldDescriptor(new Modifiers(Modifiers.PUBLIC), td, arrayElementFieldName, null,
              false);
      mapTypeToArrayField.put(td, fd);
    }
    return fd;
  }

  private void mergeSharedLocationAnaylsis(ClearingSummary curr, Set<ClearingSummary> inSet) {
    if (inSet.size() == 0) {
      return;
    }
    Hashtable<Pair<NTuple<Descriptor>, Location>, Boolean> mapHeapPathLoc2Flag =
        new Hashtable<Pair<NTuple<Descriptor>, Location>, Boolean>();

    for (Iterator inIterator = inSet.iterator(); inIterator.hasNext();) {

      ClearingSummary inTable = (ClearingSummary) inIterator.next();

      Set<NTuple<Descriptor>> keySet = inTable.keySet();

      for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
        SharedStatus inState = inTable.get(hpKey);
        SharedStatus currState = curr.get(hpKey);
        if (currState == null) {
          currState = new SharedStatus();
          curr.put(hpKey, currState);
        }

        currState.merge(inState);

        Set<Location> locSet = inState.getMap().keySet();
        for (Iterator iterator2 = locSet.iterator(); iterator2.hasNext();) {
          Location loc = (Location) iterator2.next();
          Pair<Set<Descriptor>, Boolean> pair = inState.getMap().get(loc);
          boolean inFlag = pair.getSecond().booleanValue();

          Pair<NTuple<Descriptor>, Location> flagKey =
              new Pair<NTuple<Descriptor>, Location>(hpKey, loc);
          Boolean current = mapHeapPathLoc2Flag.get(flagKey);
          if (current == null) {
            current = new Boolean(true);
          }
          boolean newInFlag = current.booleanValue() & inFlag;
          mapHeapPathLoc2Flag.put(flagKey, Boolean.valueOf(newInFlag));
        }

      }

    }

    // merge flag status
    Set<NTuple<Descriptor>> hpKeySet = curr.keySet();
    for (Iterator iterator = hpKeySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
      SharedStatus currState = curr.get(hpKey);
      Set<Location> locKeySet = currState.getMap().keySet();
      for (Iterator iterator2 = locKeySet.iterator(); iterator2.hasNext();) {
        Location locKey = (Location) iterator2.next();
        Pair<Set<Descriptor>, Boolean> pair = currState.getMap().get(locKey);
        boolean currentFlag = pair.getSecond().booleanValue();
        Boolean inFlag = mapHeapPathLoc2Flag.get(new Pair(hpKey, locKey));
        if (inFlag != null) {
          boolean newFlag = currentFlag | inFlag.booleanValue();
          if (currentFlag != newFlag) {
            currState.getMap().put(locKey, new Pair(pair.getFirst(), new Boolean(newFlag)));
          }
        }
      }
    }

  }

  private void merge(Set<NTuple<Descriptor>> curr, Set<NTuple<Descriptor>> in) {
    if (curr.isEmpty()) {
      // set has a special initial value which covers all possible
      // elements
      // For the first time of intersection, we can take all previous set
      curr.addAll(in);
    } else {
      // otherwise, current set is the intersection of the two sets
      curr.retainAll(in);
    }

  }

  // combine two heap path
  private NTuple<Descriptor> combine(NTuple<Descriptor> callerIn, NTuple<Descriptor> calleeIn) {
    NTuple<Descriptor> combined = new NTuple<Descriptor>();

    for (int i = 0; i < callerIn.size(); i++) {
      combined.add(callerIn.get(i));
    }

    // the first element of callee's heap path represents parameter
    // so we skip the first one since it is already added from caller's heap
    // path
    for (int i = 1; i < calleeIn.size(); i++) {
      combined.add(calleeIn.get(i));
    }

    return combined;
  }

  private Set<NTuple<Descriptor>> bindSet(Set<NTuple<Descriptor>> calleeSet,
      Hashtable<Integer, TempDescriptor> mapParamIdx2ParamTempDesc,
      Hashtable<Integer, NTuple<Descriptor>> mapCallerArgIdx2HeapPath) {

    Set<NTuple<Descriptor>> boundedCalleeSet = new HashSet<NTuple<Descriptor>>();

    Set<Integer> keySet = mapCallerArgIdx2HeapPath.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Integer idx = (Integer) iterator.next();

      NTuple<Descriptor> callerArgHeapPath = mapCallerArgIdx2HeapPath.get(idx);
      TempDescriptor calleeParam = mapParamIdx2ParamTempDesc.get(idx);
      for (Iterator iterator2 = calleeSet.iterator(); iterator2.hasNext();) {
        NTuple<Descriptor> element = (NTuple<Descriptor>) iterator2.next();
        if (element.startsWith(calleeParam)) {
          NTuple<Descriptor> boundElement = combine(callerArgHeapPath, element);
          boundedCalleeSet.add(boundElement);
        }

      }

    }
    return boundedCalleeSet;

  }

  // Borrowed it from disjoint analysis
  private LinkedList<MethodDescriptor> topologicalSort(Set<MethodDescriptor> toSort) {

    Set<MethodDescriptor> discovered = new HashSet<MethodDescriptor>();

    LinkedList<MethodDescriptor> sorted = new LinkedList<MethodDescriptor>();

    Iterator<MethodDescriptor> itr = toSort.iterator();
    while (itr.hasNext()) {
      MethodDescriptor d = itr.next();

      if (!discovered.contains(d)) {
        dfsVisit(d, toSort, sorted, discovered);
      }
    }

    return sorted;
  }

  // While we're doing DFS on call graph, remember
  // dependencies for efficient queuing of methods
  // during interprocedural analysis:
  //
  // a dependent of a method decriptor d for this analysis is:
  // 1) a method or task that invokes d
  // 2) in the descriptorsToAnalyze set
  private void dfsVisit(MethodDescriptor md, Set<MethodDescriptor> toSort,
      LinkedList<MethodDescriptor> sorted, Set<MethodDescriptor> discovered) {

    discovered.add(md);

    Iterator itr = callGraph.getCallerSet(md).iterator();
    while (itr.hasNext()) {
      MethodDescriptor dCaller = (MethodDescriptor) itr.next();
      // only consider callers in the original set to analyze
      if (!toSort.contains(dCaller)) {
        continue;
      }
      if (!discovered.contains(dCaller)) {
        addDependent(md, // callee
            dCaller // caller
        );

        dfsVisit(dCaller, toSort, sorted, discovered);
      }
    }

    // for leaf-nodes last now!
    sorted.addLast(md);
  }

  // a dependent of a method decriptor d for this analysis is:
  // 1) a method or task that invokes d
  // 2) in the descriptorsToAnalyze set
  private void addDependent(MethodDescriptor callee, MethodDescriptor caller) {
    Set<MethodDescriptor> deps = mapDescriptorToSetDependents.get(callee);
    if (deps == null) {
      deps = new HashSet<MethodDescriptor>();
    }
    deps.add(caller);
    mapDescriptorToSetDependents.put(callee, deps);
  }

  private Set<MethodDescriptor> getDependents(MethodDescriptor callee) {
    Set<MethodDescriptor> deps = mapDescriptorToSetDependents.get(callee);
    if (deps == null) {
      deps = new HashSet<MethodDescriptor>();
      mapDescriptorToSetDependents.put(callee, deps);
    }
    return deps;
  }

  private NTuple<Descriptor> computePath(Descriptor td) {
    // generate proper path fot input td
    // if td is local variable, it just generate one element tuple path
    if (mapHeapPath.containsKey(td)) {
      return mapHeapPath.get(td);
    } else {
      NTuple<Descriptor> path = new NTuple<Descriptor>();
      path.add(td);
      return path;
    }
  }

  private NTuple<Location> deriveThisLocationTuple(MethodDescriptor md) {
    String thisLocIdentifier = ssjava.getMethodLattice(md).getThisLoc();
    Location thisLoc = new Location(md, thisLocIdentifier);
    NTuple<Location> locTuple = new NTuple<Location>();
    locTuple.add(thisLoc);
    return locTuple;
  }

  private NTuple<Location> deriveLocationTuple(MethodDescriptor md, TempDescriptor td) {

    assert td.getType() != null;

    if (mapDescriptorToLocationPath.containsKey(td)) {
      return mapDescriptorToLocationPath.get(td);
    } else {
      if (td.getSymbol().startsWith("this")) {
        return deriveThisLocationTuple(md);
      } else {
        NTuple<Location> locTuple =
            ((SSJavaType) td.getType().getExtension()).getCompLoc().getTuple();
        return locTuple;
      }
    }

  }

}