package Analysis.SSJava;

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
import IR.Flat.FlatNew;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.FlatSetElementNode;
import IR.Flat.FlatSetFieldNode;
import IR.Flat.TempDescriptor;
import IR.Tree.Modifiers;

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

  // maps shared location to the set of descriptors which belong to the shared
  // location

  // keep current descriptors to visit in fixed-point interprocedural analysis,
  private Stack<MethodDescriptor> methodDescriptorsToVisitStack;

  // when analyzing flatcall, need to re-schedule set of callee
  private Set<MethodDescriptor> calleesToEnqueue;

  private Set<ReadSummary> possibleCalleeReadSummarySetToCaller;

  public static final String arrayElementFieldName = "___element_";
  static protected Hashtable<TypeDescriptor, FieldDescriptor> mapTypeToArrayField;

  // maps a method descriptor to the merged incoming caller's current
  // reading status
  // it is for setting clearance flag when all read set is overwritten
  private Hashtable<MethodDescriptor, ReadSummary> mapMethodDescriptorToReadSummary;

  private Hashtable<MethodDescriptor, MultiSourceMap<NTuple<Location>, NTuple<Descriptor>>> mapMethodToSharedLocCoverSet;

  private Hashtable<FlatNode, SharedLocMap> mapFlatNodeToSharedLocMapping;
  private Hashtable<FlatNode, SharedLocMap> mapFlatNodeToDeleteSet;

  private LinkedList<MethodDescriptor> sortedDescriptors;

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

    this.methodDescriptorsToVisitStack = new Stack<MethodDescriptor>();
    this.calleesToEnqueue = new HashSet<MethodDescriptor>();
    this.mapTypeToArrayField = new Hashtable<TypeDescriptor, FieldDescriptor>();
    this.LOCAL = new TempDescriptor("LOCAL");
    this.mapDescToLocation = new Hashtable<Descriptor, Location>();
    this.possibleCalleeReadSummarySetToCaller = new HashSet<ReadSummary>();
    this.mapMethodDescriptorToReadSummary = new Hashtable<MethodDescriptor, ReadSummary>();
    this.mapFlatNodeToBoundReadSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapFlatNodeToBoundMustWriteSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapFlatNodeToBoundMayWriteSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapFlatNodeToSharedLocMapping = new Hashtable<FlatNode, SharedLocMap>();
    this.mapFlatMethodToDeleteSet = new Hashtable<FlatMethod, SharedLocMap>();
    this.calleeUnionBoundDeleteSet = new SharedLocMap();
    this.calleeIntersectBoundSharedSet = new SharedLocMap();
    this.mapFlatMethodToSharedLocMap = new Hashtable<FlatMethod, SharedLocMap>();
    this.mapMethodToSharedLocCoverSet =
        new Hashtable<MethodDescriptor, MultiSourceMap<NTuple<Location>, NTuple<Descriptor>>>();
    this.mapFlatNodeToDeleteSet = new Hashtable<FlatNode, SharedLocMap>();
  }

  public void definitelyWrittenCheck() {
    if (!ssjava.getAnnotationRequireSet().isEmpty()) {
      initialize();

      methodReadWriteSetAnalysis();
      computeSharedCoverSet();

      // System.out.println("$$$=" +
      // mapMethodToSharedLocCoverSet.get(methodContainingSSJavaLoop));
      // System.exit(0);

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
    sharedLoc_analyzeBody(state.getMethodFlat(methodContainingSSJavaLoop),
        ssjava.getSSJavaLoopEntrance(), sharedLocMap, deleteSet, true);

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

    MethodDescriptor md = fm.getMethod();

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

          if (!lhs.getSymbol().startsWith("neverused") && !lhs.getSymbol().startsWith("leftop")
              && !lhs.getSymbol().startsWith("rightop") && rhs.getType().isImmutable()) {

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
              // " lhsHeapPath=" + lhsHeapPath);
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

      Location fieldLoc;
      if (fn.kind() == FKind.FlatSetFieldNode) {
        FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
        lhs = fsfn.getDst();
        fld = fsfn.getField();
        rhs = fsfn.getSrc();
        fieldLoc = (Location) fld.getType().getExtension();
      } else {
        break;
      }

      if (!isEventLoopBody && fieldLoc.getDescriptor().equals(md)) {
        // if the field belongs to the local lattice, no reason to calculate
        // shared location
        break;
      }

      NTuple<Location> fieldLocTuple = new NTuple<Location>();
      if (fld.isStatic()) {
        if (fld.isFinal()) {
          // in this case, fld has TOP location
          Location topLocation = Location.createTopLocation(md);
          fieldLocTuple.add(topLocation);
        } else {
          fieldLocTuple.addAll(deriveGlobalLocationTuple(md));
          if (fn.kind() == FKind.FlatSetFieldNode) {
            fieldLocTuple.add((Location) fld.getType().getExtension());
          }
        }

      } else {
        fieldLocTuple.addAll(deriveLocationTuple(md, lhs));
        if (fn.kind() == FKind.FlatSetFieldNode) {
          fieldLocTuple.add((Location) fld.getType().getExtension());
        }
      }

      // shared loc extension
      Location srcLoc = getLocation(rhs);
      if (ssjava.isSharedLocation(fieldLoc)) {
        // only care the case that loc(f) is shared location
        // write(field)

        // NTuple<Location> fieldLocTuple = new NTuple<Location>();
        // fieldLocTuple.addAll(mapDescriptorToLocationPath.get(lhs));
        // fieldLocTuple.add(fieldLoc);

        NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>();
        fldHeapPath.addAll(computePath(lhs));
        if (fn.kind() == FKind.FlatSetFieldNode) {
          fldHeapPath.add(fld);
        }

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
    if (!curr.map.isEmpty()) {
      // System.out.println(fn + "#######" + curr);
    }

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

    System.out.println("\n###");
    System.out.println("computeSharedCoverSet_analyzeMethod=" + fm);
    MethodDescriptor md = fm.getMethod();

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();

    Set<FlatNode> visited = new HashSet<FlatNode>();

    if (onlyVisitSSJavaLoop) {
      flatNodesToVisit.add(ssjava.getSSJavaLoopEntrance());
    } else {
      flatNodesToVisit.add(fm);
    }

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);
      visited.add(fn);

      computeSharedCoverSet_nodeActions(md, fn, onlyVisitSSJavaLoop);

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

  private void computeSharedCoverSet_nodeActions(MethodDescriptor md, FlatNode fn,
      boolean isEventLoopBody) {
    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;

    switch (fn.kind()) {

    case FKind.FlatLiteralNode: {
      FlatLiteralNode fln = (FlatLiteralNode) fn;
      lhs = fln.getDst();

      NTuple<Location> lhsLocTuple = new NTuple<Location>();
      lhsLocTuple.add(Location.createTopLocation(md));
      mapDescriptorToLocationPath.put(lhs, lhsLocTuple);

      if (lhs.getType().isPrimitive() && !lhs.getSymbol().startsWith("neverused")
          && !lhs.getSymbol().startsWith("srctmp")) {
        // only need to care about composite location case here
        if (lhs.getType().getExtension() instanceof SSJavaType) {
          CompositeLocation compLoc = ((SSJavaType) lhs.getType().getExtension()).getCompLoc();
          Location lastLocElement = compLoc.get(compLoc.getSize() - 1);
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

        if (!lhs.getSymbol().startsWith("neverused") && !lhs.getSymbol().startsWith("leftop")
            && !lhs.getSymbol().startsWith("rightop")) {

          NTuple<Location> rhsLocTuple = new NTuple<Location>();
          NTuple<Location> lhsLocTuple = new NTuple<Location>();
          if (mapDescriptorToLocationPath.containsKey(rhs)) {
            mapDescriptorToLocationPath.put(lhs, deriveLocationTuple(md, rhs));
            lhsLocTuple = mapDescriptorToLocationPath.get(lhs);
          } else {
            // rhs side
            if (rhs.getType().getExtension() != null
                && rhs.getType().getExtension() instanceof SSJavaType) {

              if (((SSJavaType) rhs.getType().getExtension()).getCompLoc() != null) {
                rhsLocTuple.addAll(((SSJavaType) rhs.getType().getExtension()).getCompLoc()
                    .getTuple());
              }

            } else {
              NTuple<Location> locTuple = deriveLocationTuple(md, rhs);
              if (locTuple != null) {
                rhsLocTuple.addAll(locTuple);
              }
            }
            if (rhsLocTuple.size() > 0) {
              mapDescriptorToLocationPath.put(rhs, rhsLocTuple);
            }

            // lhs side
            if (lhs.getType().getExtension() != null
                && lhs.getType().getExtension() instanceof SSJavaType) {
              lhsLocTuple.addAll(((SSJavaType) lhs.getType().getExtension()).getCompLoc()
                  .getTuple());
              mapDescriptorToLocationPath.put(lhs, lhsLocTuple);
            } else if (mapDescriptorToLocationPath.get(rhs) != null) {
              // propagate rhs's location to lhs
              lhsLocTuple.addAll(mapDescriptorToLocationPath.get(rhs));
              mapDescriptorToLocationPath.put(lhs, lhsLocTuple);
            }
          }

          if (isEventLoopBody && lhs.getType().isPrimitive()
              && !lhs.getSymbol().startsWith("srctmp")) {

            NTuple<Descriptor> lhsHeapPath = computePath(lhs);

            if (lhsLocTuple != null) {
              addMayWrittenSet(md, lhsLocTuple, lhsHeapPath);
            }

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

      NTuple<Location> fieldLocTuple = new NTuple<Location>();
      fieldLocTuple.addAll(deriveLocationTuple(md, lhs));
      if (fn.kind() == FKind.FlatSetFieldNode) {
        fieldLocTuple.add((Location) fld.getType().getExtension());
      }

      if (mapHeapPath.containsKey(lhs)) {
        // fields reachable from the param can have heap path entry.
        NTuple<Descriptor> lhsHeapPath = new NTuple<Descriptor>();
        lhsHeapPath.addAll(mapHeapPath.get(lhs));

        Location fieldLocation;
        if (fn.kind() == FKind.FlatSetFieldNode) {
          fieldLocation = getLocation(fld);
        } else {
          fieldLocation = getLocation(lhsHeapPath.get(getArrayBaseDescriptorIdx(lhsHeapPath)));
        }

        // Location fieldLocation = getLocation(lhs);
        if (!isEventLoopBody && fieldLocation.getDescriptor().equals(md)) {
          // if the field belongs to the local lattice, no reason to calculate
          // shared location
          break;
        }

        if (ssjava.isSharedLocation(fieldLocation)) {

          NTuple<Descriptor> fieldHeapPath = new NTuple<Descriptor>();
          fieldHeapPath.addAll(computePath(lhs));
          if (fn.kind() == FKind.FlatSetFieldNode) {
            fieldHeapPath.add(fld);
          }

          addMayWrittenSet(md, fieldLocTuple, fieldHeapPath);

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

      NTuple<Location> locTuple = new NTuple<Location>();

      if (fld.isStatic()) {

        if (fld.isFinal()) {
          // in this case, fld has TOP location
          Location topLocation = Location.createTopLocation(md);
          locTuple.add(topLocation);
        } else {
          locTuple.addAll(deriveGlobalLocationTuple(md));
          if (fn.kind() == FKind.FlatFieldNode) {
            locTuple.add((Location) fld.getType().getExtension());
          }
        }

      } else {
        locTuple.addAll(deriveLocationTuple(md, rhs));
        if (fn.kind() == FKind.FlatFieldNode) {
          locTuple.add((Location) fld.getType().getExtension());
        }
      }

      mapDescriptorToLocationPath.put(lhs, locTuple);

    }
      break;

    case FKind.FlatCall: {

      FlatCall fc = (FlatCall) fn;

      bindLocationPathCallerArgWithCalleeParam(md, fc);

    }
      break;

    case FKind.FlatNew: {

      FlatNew fnew = (FlatNew) fn;
      TempDescriptor dst = fnew.getDst();
      NTuple<Location> locTuple = deriveLocationTuple(md, dst);

      if (locTuple != null) {
        NTuple<Location> dstLocTuple = new NTuple<Location>();
        dstLocTuple.addAll(locTuple);
        mapDescriptorToLocationPath.put(dst, dstLocTuple);
      }

    }
      break;
    }
  }

  private void addMayWrittenSet(MethodDescriptor md, NTuple<Location> locTuple,
      NTuple<Descriptor> heapPath) {

    MultiSourceMap<NTuple<Location>, NTuple<Descriptor>> map = mapMethodToSharedLocCoverSet.get(md);
    if (map == null) {
      map = new MultiSourceMap<NTuple<Location>, NTuple<Descriptor>>();
      mapMethodToSharedLocCoverSet.put(md, map);
    }

    Set<NTuple<Descriptor>> writeSet = map.get(locTuple);
    if (writeSet == null) {
      writeSet = new HashSet<NTuple<Descriptor>>();
      map.put(locTuple, writeSet);
    }
    writeSet.add(heapPath);

  }

  private void bindLocationPathCallerArgWithCalleeParam(MethodDescriptor mdCaller, FlatCall fc) {

    if (ssjava.isSSJavaUtil(fc.getMethod().getClassDesc())) {
      // ssjava util case!
      // have write effects on the first argument
      TempDescriptor arg = fc.getArg(0);
      NTuple<Location> argLocationPath = deriveLocationTuple(mdCaller, arg);
      NTuple<Descriptor> argHeapPath = computePath(arg);
      addMayWrittenSet(mdCaller, argLocationPath, argHeapPath);
    } else if (ssjava.needTobeAnnotated(fc.getMethod())) {

      // if arg is not primitive type, we need to propagate maywritten set to
      // the caller's location path

      MethodDescriptor mdCallee = fc.getMethod();
      Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
      setPossibleCallees.addAll(callGraph.getMethods(mdCallee));

      // create mapping from arg idx to its heap paths
      Hashtable<Integer, NTuple<Descriptor>> mapArgIdx2CallerArgHeapPath =
          new Hashtable<Integer, NTuple<Descriptor>>();

      // create mapping from arg idx to its location paths
      Hashtable<Integer, NTuple<Location>> mapArgIdx2CallerArgLocationPath =
          new Hashtable<Integer, NTuple<Location>>();

      if (fc.getThis() != null) {

        if (mapHeapPath.containsKey(fc.getThis())) {

          // setup heap path for 'this'
          NTuple<Descriptor> thisHeapPath = new NTuple<Descriptor>();
          thisHeapPath.addAll(mapHeapPath.get(fc.getThis()));
          mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(0), thisHeapPath);

          // setup location path for 'this'
          NTuple<Location> thisLocationPath = deriveLocationTuple(mdCaller, fc.getThis());
          mapArgIdx2CallerArgLocationPath.put(Integer.valueOf(0), thisLocationPath);

        }
      }

      for (int i = 0; i < fc.numArgs(); i++) {
        TempDescriptor arg = fc.getArg(i);
        // create mapping arg to loc path

        if (mapHeapPath.containsKey(arg)) {
          // setup heap path
          NTuple<Descriptor> argHeapPath = mapHeapPath.get(arg);
          mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(i + 1), argHeapPath);
          // setup loc path
          NTuple<Location> argLocationPath = deriveLocationTuple(mdCaller, arg);
          mapArgIdx2CallerArgLocationPath.put(Integer.valueOf(i + 1), argLocationPath);
        }

      }

      for (Iterator iterator = setPossibleCallees.iterator(); iterator.hasNext();) {
        MethodDescriptor callee = (MethodDescriptor) iterator.next();
        FlatMethod calleeFlatMethod = state.getMethodFlat(callee);

        // binding caller's args and callee's params

        Hashtable<NTuple<Descriptor>, NTuple<Descriptor>> mapParamHeapPathToCallerArgHeapPath =
            new Hashtable<NTuple<Descriptor>, NTuple<Descriptor>>();

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

          NTuple<Descriptor> calleeHeapPath = computePath(param);

          NTuple<Descriptor> argHeapPath =
              mapArgIdx2CallerArgHeapPath.get(Integer.valueOf(i + offset));

          if (argHeapPath != null) {
            mapParamHeapPathToCallerArgHeapPath.put(calleeHeapPath, argHeapPath);

          }

        }

        Set<Integer> keySet = mapArgIdx2CallerArgLocationPath.keySet();
        for (Iterator iterator2 = keySet.iterator(); iterator2.hasNext();) {
          Integer idx = (Integer) iterator2.next();

          NTuple<Location> callerArgLocationPath = mapArgIdx2CallerArgLocationPath.get(idx);

          TempDescriptor calleeParam = mapParamIdx2ParamTempDesc.get(idx);
          NTuple<Location> calleeLocationPath = deriveLocationTuple(mdCallee, calleeParam);

          NTuple<Descriptor> callerArgHeapPath = mapArgIdx2CallerArgHeapPath.get(idx);
          NTuple<Descriptor> calleeHeapPath = computePath(calleeParam);

          if (!calleeParam.getType().isPrimitive()) {
            createNewMappingOfMayWrittenSet(mdCaller, callee, callerArgHeapPath,
                callerArgLocationPath, calleeHeapPath, calleeLocationPath,
                mapParamHeapPathToCallerArgHeapPath);
          }
        }

      }

    }

  }

  private Hashtable<NTuple<Location>, Set<NTuple<Descriptor>>> getMappingByStartedWith(
      MultiSourceMap<NTuple<Location>, NTuple<Descriptor>> map, NTuple<Location> in) {

    Hashtable<NTuple<Location>, Set<NTuple<Descriptor>>> matchedMapping =
        new Hashtable<NTuple<Location>, Set<NTuple<Descriptor>>>();

    Set<NTuple<Location>> keySet = map.keySet();

    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      NTuple<Location> key = (NTuple<Location>) iterator.next();
      if (key.startsWith(in)) {
        matchedMapping.put(key, map.get(key));
      }
    }

    return matchedMapping;

  }

  private void createNewMappingOfMayWrittenSet(MethodDescriptor caller, MethodDescriptor callee,
      NTuple<Descriptor> callerArgHeapPath, NTuple<Location> callerArgLocPath,
      NTuple<Descriptor> calleeParamHeapPath, NTuple<Location> calleeParamLocPath,
      Hashtable<NTuple<Descriptor>, NTuple<Descriptor>> mapParamHeapPathToCallerArgHeapPath) {

    // propagate may-written-set associated with the key that is started with
    // calleepath to the caller
    // 1) makes a new key by combining caller path and callee path(except local
    // loc element of param)
    // 2) create new mapping of may-written-set of callee path to caller path

    // extract all may written effect accessed through callee param path
    MultiSourceMap<NTuple<Location>, NTuple<Descriptor>> calleeMapping =
        mapMethodToSharedLocCoverSet.get(callee);

    if (calleeMapping == null) {
      return;
    }

    MultiSourceMap<NTuple<Location>, NTuple<Descriptor>> callerMapping =
        mapMethodToSharedLocCoverSet.get(caller);

    if (callerMapping == null) {
      callerMapping = new MultiSourceMap<NTuple<Location>, NTuple<Descriptor>>();
      mapMethodToSharedLocCoverSet.put(caller, callerMapping);
    }

    Hashtable<NTuple<Location>, Set<NTuple<Descriptor>>> paramMapping =
        getMappingByStartedWith(calleeMapping, calleeParamLocPath);

    Set<NTuple<Location>> calleeKeySet = paramMapping.keySet();

    for (Iterator iterator = calleeKeySet.iterator(); iterator.hasNext();) {
      NTuple<Location> calleeKey = (NTuple<Location>) iterator.next();

      Set<NTuple<Descriptor>> calleeMayWriteSet = paramMapping.get(calleeKey);

      if (calleeMayWriteSet != null) {

        Set<NTuple<Descriptor>> boundMayWriteSet = new HashSet<NTuple<Descriptor>>();

        Set<NTuple<Descriptor>> boundSet =
            convertToCallerMayWriteSet(calleeParamHeapPath, calleeMayWriteSet, callerMapping,
                mapParamHeapPathToCallerArgHeapPath);

        boundMayWriteSet.addAll(boundSet);

        NTuple<Location> newKey = new NTuple<Location>();
        newKey.addAll(callerArgLocPath);
        // need to replace the local location with the caller's path so skip the
        // local location of the parameter
        for (int i = 1; i < calleeKey.size(); i++) {
          newKey.add(calleeKey.get(i));
        }

        callerMapping.union(newKey, boundMayWriteSet);
      }

    }

  }

  private Set<NTuple<Descriptor>> convertToCallerMayWriteSet(
      NTuple<Descriptor> calleeParamHeapPath, Set<NTuple<Descriptor>> calleeMayWriteSet,
      MultiSourceMap<NTuple<Location>, NTuple<Descriptor>> callerMapping,
      Hashtable<NTuple<Descriptor>, NTuple<Descriptor>> mapParamHeapPathToCallerArgHeapPath) {

    Set<NTuple<Descriptor>> boundSet = new HashSet<NTuple<Descriptor>>();

    // replace callee's param path with caller's arg path
    for (Iterator iterator = calleeMayWriteSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> calleeWriteHeapPath = (NTuple<Descriptor>) iterator.next();

      NTuple<Descriptor> writeHeapPathParamHeapPath = calleeWriteHeapPath.subList(0, 1);

      NTuple<Descriptor> callerArgHeapPath =
          mapParamHeapPathToCallerArgHeapPath.get(writeHeapPathParamHeapPath);

      NTuple<Descriptor> boundHeapPath = new NTuple<Descriptor>();
      boundHeapPath.addAll(callerArgHeapPath);

      for (int i = 1; i < calleeWriteHeapPath.size(); i++) {
        boundHeapPath.add(calleeWriteHeapPath.get(i));
      }

      boundSet.add(boundHeapPath);

    }

    return boundSet;
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
          if (ssType.getCompLoc() != null) {
            CompositeLocation comp = ssType.getCompLoc();
            return comp.get(comp.getSize() - 1);
          } else {
            return null;
          }
        } else {
          return (Location) te;
        }
      }
    }

    return mapDescToLocation.get(d);
  }

  private void eventLoopAnalysis() {
    // perform second stage analysis: intraprocedural analysis ensure that
    // all
    // variables are definitely written in-between the same read

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(ssjava.getSSJavaLoopEntrance());

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

      eventLoopAnalysis_nodeAction(fn, curr, ssjava.getSSJavaLoopEntrance());

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

    } else {
      TempDescriptor lhs;
      TempDescriptor rhs;
      FieldDescriptor fld;

      switch (fn.kind()) {

      case FKind.FlatOpNode: {
        FlatOpNode fon = (FlatOpNode) fn;
        lhs = fon.getDest();
        rhs = fon.getLeft();

        if (fon.getOp().getOp() == Operation.ASSIGN) {

          if (!lhs.getSymbol().startsWith("neverused") && !lhs.getSymbol().startsWith("leftop")
              && !lhs.getSymbol().startsWith("rightop")) {

            boolean hasWriteEffect = false;

            if (rhs.getType().getExtension() instanceof SSJavaType
                && lhs.getType().getExtension() instanceof SSJavaType) {

              CompositeLocation rhsCompLoc =
                  ((SSJavaType) rhs.getType().getExtension()).getCompLoc();

              CompositeLocation lhsCompLoc =
                  ((SSJavaType) lhs.getType().getExtension()).getCompLoc();

              if (lhsCompLoc != rhsCompLoc) {
                // have a write effect!
                hasWriteEffect = true;
              }

            } else if (lhs.getType().isImmutable()) {
              hasWriteEffect = true;
            }

            if (hasWriteEffect) {
              // write(lhs)
              NTuple<Descriptor> rhsHeapPath = computePath(rhs);
              NTuple<Descriptor> lhsHeapPath = new NTuple<Descriptor>();
              lhsHeapPath.addAll(rhsHeapPath);

              Location lhsLoc = getLocation(lhs);
              if (ssjava.isSharedLocation(lhsLoc)) {

                NTuple<Descriptor> varHeapPath = computePath(lhs);
                NTuple<Location> varLocTuple = mapDescriptorToLocationPath.get(lhs);

                Set<NTuple<Descriptor>> writtenSet =
                    mapFlatNodeToSharedLocMapping.get(fn).get(varLocTuple);

                if (isCovered(varLocTuple, writtenSet)) {
                  computeKILLSetForSharedWrite(curr, writtenSet, readWriteKillSet);
                  computeGENSetForSharedAllCoverWrite(curr, writtenSet, readWriteGenSet);
                } else {
                  computeGENSetForSharedNonCoverWrite(curr, varHeapPath, readWriteGenSet);
                }

              } else {

                computeKILLSetForWrite(curr, lhsHeapPath, readWriteKillSet);
                computeGENSetForWrite(lhsHeapPath, readWriteGenSet);
              }

              // System.out.println("write effect on =" + lhsHeapPath);
              // System.out.println("#KILLSET=" + readWriteKillSet);
              // System.out.println("#GENSet=" + readWriteGenSet + "\n");

              Set<WriteAge> writeAgeSet = curr.get(lhsHeapPath);
              checkWriteAgeSet(writeAgeSet, lhsHeapPath, fn);
            }

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
        if (fn.kind() == FKind.FlatSetFieldNode) {
          fldHeapPath.add(fld);
        }

        // shared loc extension
        Location fieldLoc;
        if (fn.kind() == FKind.FlatSetFieldNode) {
          fieldLoc = (Location) fld.getType().getExtension();
        } else {
          NTuple<Location> locTuple = mapDescriptorToLocationPath.get(lhs);
          fieldLoc = locTuple.get(locTuple.size() - 1);
        }

        if (ssjava.isSharedLocation(fieldLoc)) {

          NTuple<Location> fieldLocTuple = new NTuple<Location>();
          fieldLocTuple.addAll(mapDescriptorToLocationPath.get(lhs));
          if (fn.kind() == FKind.FlatSetFieldNode) {
            fieldLocTuple.add(fieldLoc);
          }

          Set<NTuple<Descriptor>> writtenSet =
              mapFlatNodeToSharedLocMapping.get(fn).get(fieldLocTuple);

          if (isCovered(fieldLocTuple, writtenSet)) {
            computeKILLSetForSharedWrite(curr, writtenSet, readWriteKillSet);
            computeGENSetForSharedAllCoverWrite(curr, writtenSet, readWriteGenSet);
          } else {
            computeGENSetForSharedNonCoverWrite(curr, fldHeapPath, readWriteGenSet);
          }

        } else {
          computeKILLSetForWrite(curr, fldHeapPath, readWriteKillSet);
          computeGENSetForWrite(fldHeapPath, readWriteGenSet);
        }

        // System.out.println("KILLSET=" + readWriteKillSet);
        // System.out.println("GENSet=" + readWriteGenSet);

      }
        break;

      case FKind.FlatCall: {
        FlatCall fc = (FlatCall) fn;

        SharedLocMap sharedLocMap = mapFlatNodeToSharedLocMapping.get(fc);
        // System.out.println("FLATCALL:" + fn);
        generateKILLSetForFlatCall(fc, curr, sharedLocMap, readWriteKillSet);
        generateGENSetForFlatCall(fc, sharedLocMap, readWriteGenSet);

        // System.out.println("KILLSET=" + readWriteKillSet);
        // System.out.println("GENSet=" + readWriteGenSet);

        checkManyRead(fc, curr);
      }
        break;

      }

      computeNewMapping(curr, readWriteKillSet, readWriteGenSet);
      // System.out.println("#######" + curr);

    }

  }

  private void computeGENSetForSharedNonCoverWrite(
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr, NTuple<Descriptor> heapPath,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> genSet) {

    Set<WriteAge> writeAgeSet = genSet.get(heapPath);
    if (writeAgeSet == null) {
      writeAgeSet = new HashSet<WriteAge>();
      genSet.put(heapPath, writeAgeSet);
    }

    writeAgeSet.add(new WriteAge(1));

  }

  private void computeGENSetForSharedAllCoverWrite(
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr, Set<NTuple<Descriptor>> writtenSet,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> genSet) {

    for (Iterator iterator = writtenSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> writeHeapPath = (NTuple<Descriptor>) iterator.next();

      Set<WriteAge> writeAgeSet = new HashSet<WriteAge>();
      writeAgeSet.add(new WriteAge(0));

      genSet.put(writeHeapPath, writeAgeSet);
    }

  }

  private void computeKILLSetForSharedWrite(Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr,
      Set<NTuple<Descriptor>> writtenSet, Hashtable<NTuple<Descriptor>, Set<WriteAge>> killSet) {

    for (Iterator iterator = writtenSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> writeHeapPath = (NTuple<Descriptor>) iterator.next();
      Set<WriteAge> writeSet = curr.get(writeHeapPath);
      if (writeSet != null) {
        killSet.put(writeHeapPath, writeSet);
      }
    }

  }

  private boolean isCovered(NTuple<Location> locTuple, Set<NTuple<Descriptor>> inSet) {

    if (inSet == null) {
      return false;
    }

    Set<NTuple<Descriptor>> coverSet =
        mapMethodToSharedLocCoverSet.get(methodContainingSSJavaLoop).get(locTuple);

    return inSet.containsAll(coverSet);
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

    // System.out.println("# CHECK WRITE AGE of " + path + " from set=" +
    // writeAgeSet);

    if (writeAgeSet != null) {
      for (Iterator iterator = writeAgeSet.iterator(); iterator.hasNext();) {
        WriteAge writeAge = (WriteAge) iterator.next();
        if (writeAge.getAge() > MAXAGE) {
          generateErrorMessage(path, fn);
        }
      }
    }
  }

  private void generateErrorMessage(NTuple<Descriptor> path, FlatNode fn) {

    Descriptor lastDesc = path.get(getArrayBaseDescriptorIdx(path));
    if (ssjava.isSharedLocation(getLocation(lastDesc))) {

      NTuple<Location> locPathTuple = getLocationTuple(path);
      Set<NTuple<Descriptor>> coverSet =
          mapMethodToSharedLocCoverSet.get(methodContainingSSJavaLoop).get(locPathTuple);
      throw new Error("Shared memory locations, which is reachable through references " + path
          + ", are not completely overwritten by the higher values at "
          + methodContainingSSJavaLoop.getClassDesc().getSourceFileName() + "::" + fn.getNumLine()
          + ".\nThe following memory locations belong to the same shared locations:" + coverSet);

    } else {
      throw new Error(
          "Memory location, which is reachable through references "
              + path
              + ", who comes back to the same read statement without being overwritten at the out-most iteration at "
              + methodContainingSSJavaLoop.getClassDesc().getSourceFileName() + "::"
              + fn.getNumLine());
    }

  }

  private void generateGENSetForFlatCall(FlatCall fc, SharedLocMap sharedLocMap,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> GENSet) {

    Set<NTuple<Descriptor>> boundMayWriteSet = mapFlatNodeToBoundMayWriteSet.get(fc);
    // System.out.println("boundMayWriteSet=" + boundMayWriteSet);

    for (Iterator iterator = boundMayWriteSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> heapPath = (NTuple<Descriptor>) iterator.next();

      if (!isSharedLocation(heapPath)) {
        addWriteAgeToSet(heapPath, GENSet, new WriteAge(0));
      } else {
        // if the current heap path is shared location

        NTuple<Location> locTuple = getLocationTuple(heapPath);

        Set<NTuple<Descriptor>> sharedWriteHeapPathSet = sharedLocMap.get(locTuple);

        if (isCovered(locTuple, sharedLocMap.get(locTuple))) {
          // if it is covered, add all of heap paths belong to the same shared
          // loc with write age 0

          for (Iterator iterator2 = sharedWriteHeapPathSet.iterator(); iterator2.hasNext();) {
            NTuple<Descriptor> sharedHeapPath = (NTuple<Descriptor>) iterator2.next();
            addWriteAgeToSet(sharedHeapPath, GENSet, new WriteAge(0));
          }

        } else {
          // if not covered, add write age 1 to the heap path that is
          // may-written but not covered
          addWriteAgeToSet(heapPath, GENSet, new WriteAge(1));
        }

      }

    }

  }

  private void addWriteAgeToSet(NTuple<Descriptor> heapPath,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> map, WriteAge age) {

    Set<WriteAge> currSet = map.get(heapPath);
    if (currSet == null) {
      currSet = new HashSet<WriteAge>();
      map.put(heapPath, currSet);
    }

    currSet.add(age);
  }

  private void generateKILLSetForFlatCall(FlatCall fc,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> curr, SharedLocMap sharedLocMap,
      Hashtable<NTuple<Descriptor>, Set<WriteAge>> KILLSet) {

    Set<NTuple<Descriptor>> boundMustWriteSet = mapFlatNodeToBoundMustWriteSet.get(fc);
    System.out.println("boundMustWriteSet=" + boundMustWriteSet);

    for (Iterator iterator = boundMustWriteSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> heapPath = (NTuple<Descriptor>) iterator.next();

      if (isSharedLocation(heapPath)) {
        NTuple<Location> locTuple = getLocationTuple(heapPath);

        if (isCovered(locTuple, sharedLocMap.get(locTuple))) {
          // if it is shared loc and corresponding shared loc has been covered
          KILLSet.put(heapPath, curr.get(heapPath));
        }
      } else {

        for (Enumeration<NTuple<Descriptor>> e = curr.keys(); e.hasMoreElements();) {
          NTuple<Descriptor> key = e.nextElement();
          if (key.startsWith(heapPath)) {
            KILLSet.put(key, curr.get(key));
          }
        }

      }

    }

  }

  private int getArrayBaseDescriptorIdx(NTuple<Descriptor> heapPath) {

    for (int i = heapPath.size() - 1; i >= 0; i--) {
      if (!heapPath.get(i).getSymbol().equals(arrayElementFieldName)) {
        return i;
      }
    }

    return -1;

  }

  private boolean isSharedLocation(NTuple<Descriptor> heapPath) {

    Descriptor d = heapPath.get(getArrayBaseDescriptorIdx(heapPath));

    return ssjava.isSharedLocation(getLocation(heapPath.get(getArrayBaseDescriptorIdx(heapPath))));

  }

  private NTuple<Location> getLocationTuple(NTuple<Descriptor> heapPath) {

    NTuple<Location> locTuple = new NTuple<Location>();

    locTuple.addAll(mapDescriptorToLocationPath.get(heapPath.get(0)));

    for (int i = 1; i <= getArrayBaseDescriptorIdx(heapPath); i++) {
      locTuple.add(getLocation(heapPath.get(i)));
    }

    return locTuple;
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
        if (thisHeapPath != null) {
          // if 'this' does not have heap path, it is local reference
          mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(0), thisHeapPath);
        }
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

    if (ssjava.isSSJavaUtil(fc.getMethod().getClassDesc())) {
      // ssjava util case!
      // have write effects on the first argument
      TempDescriptor arg = fc.getArg(0);
      NTuple<Descriptor> argHeapPath = computePath(arg);

      // convert heap path to location path
      NTuple<Location> argLocTuple = new NTuple<Location>();
      argLocTuple.addAll(deriveLocationTuple(mdCaller, (TempDescriptor) argHeapPath.get(0)));
      for (int i = 1; i < argHeapPath.size(); i++) {
        argLocTuple.add(getLocation(argHeapPath.get(i)));
      }

      calleeIntersectBoundSharedSet.addWrite(argLocTuple, argHeapPath);

    } else if (ssjava.needTobeAnnotated(fc.getMethod())) {

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
        if (thisLocationPath != null) {
          mapArgIdx2CallerAgLocationPath.put(Integer.valueOf(0), thisLocationPath);
        }
      }

      for (int i = 0; i < fc.numArgs(); i++) {
        TempDescriptor arg = fc.getArg(i);
        NTuple<Location> argLocationPath = deriveLocationTuple(mdCaller, arg);
        if (argLocationPath != null) {
          mapArgIdx2CallerAgLocationPath.put(Integer.valueOf(i + 1), argLocationPath);
        }
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
            createNewMappingOfDeleteSet(callerArgLocationPath, callerArgHeapPath,
                calleeLocationPath, calleeDeleteSet);
          }

          if (calleeSharedLocMap != null) {
            createNewMappingOfSharedSet(callerArgLocationPath, callerArgHeapPath,
                calleeLocationPath, calleeSharedLocMap);
          }

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
          ssjava.setSSJavaLoopEntrance(fn);
          break;
        }
      }

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);
        flatNodesToVisit.add(nn);
      }
    }

    assert ssjava.getSSJavaLoopEntrance() != null;

    // assume that ssjava loop is top-level loop in method, not nested loop
    Set nestedLoop = loopFinder.nestedLoops();
    for (Iterator loopIter = nestedLoop.iterator(); loopIter.hasNext();) {
      LoopFinder lf = (LoopFinder) loopIter.next();
      if (lf.loopEntrances().iterator().next().equals(ssjava.getSSJavaLoopEntrance())) {
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

    methodReadWriteSet_analyzeBody(ssjava.getSSJavaLoopEntrance(), readSet, mustWriteSet,
        mayWriteSet, true);

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

        // if (lhs.getType().isPrimitive()) {
        // NTuple<Descriptor> lhsHeapPath = new NTuple<Descriptor>();
        // lhsHeapPath.add(lhs);
        // mapHeapPath.put(lhs, lhsHeapPath);
        // } else

        if (rhsHeapPath != null) {
          mapHeapPath.put(lhs, mapHeapPath.get(rhs));
        } else {
          if (isEventLoopBody) {
            NTuple<Descriptor> heapPath = new NTuple<Descriptor>();
            heapPath.add(rhs);
            mapHeapPath.put(lhs, heapPath);
          } else {
            break;
          }
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
        if (fn.kind() == FKind.FlatFieldNode) {
          readingHeapPath.add(fld);
        }

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
        if (fn.kind() != FKind.FlatSetElementNode) {
          fldHeapPath.add(fld);
        }
        // mapHeapPath.put(fld, fldHeapPath);

        // write(x.f)
        // need to add hp(y) to WT
        if (fn.kind() != FKind.FlatSetElementNode) {
          currMustWriteSet.add(fldHeapPath);
        }
        mayWriteSet.add(fldHeapPath);

      }

    }
      break;

    case FKind.FlatCall: {

      FlatCall fc = (FlatCall) fn;

      bindHeapPathCallerArgWithCalleeParam(fc);

      Set<NTuple<Descriptor>> boundReadSet = new HashSet<NTuple<Descriptor>>();
      boundReadSet.addAll(calleeUnionBoundReadSet);

      Set<NTuple<Descriptor>> boundMustWriteSet = new HashSet<NTuple<Descriptor>>();
      boundMustWriteSet.addAll(calleeIntersectBoundMustWriteSet);

      Set<NTuple<Descriptor>> boundMayWriteSet = new HashSet<NTuple<Descriptor>>();
      boundMayWriteSet.addAll(calleeUnionBoundMayWriteSet);

      mapFlatNodeToBoundReadSet.put(fn, boundReadSet);
      mapFlatNodeToBoundMustWriteSet.put(fn, boundMustWriteSet);
      mapFlatNodeToBoundMayWriteSet.put(fn, boundMayWriteSet);

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
      NTuple<Descriptor> rtrHeapPath = new NTuple<Descriptor>();
      rtrHeapPath.addAll(mapHeapPath.get(td));
      return rtrHeapPath;
    } else {
      NTuple<Descriptor> rtrHeapPath = new NTuple<Descriptor>();
      rtrHeapPath.add(td);
      return rtrHeapPath;
    }
  }

  private NTuple<Location> deriveThisLocationTuple(MethodDescriptor md) {
    String thisLocIdentifier = ssjava.getMethodLattice(md).getThisLoc();
    Location thisLoc = new Location(md, thisLocIdentifier);
    NTuple<Location> locTuple = new NTuple<Location>();
    locTuple.add(thisLoc);
    return locTuple;
  }

  private NTuple<Location> deriveGlobalLocationTuple(MethodDescriptor md) {
    String globalLocIdentifier = ssjava.getMethodLattice(md).getGlobalLoc();
    Location globalLoc = new Location(md, globalLocIdentifier);
    NTuple<Location> locTuple = new NTuple<Location>();
    locTuple.add(globalLoc);
    return locTuple;
  }

  private NTuple<Location> deriveLocationTuple(MethodDescriptor md, TempDescriptor td) {

    assert td.getType() != null;

    if (mapDescriptorToLocationPath.containsKey(td)) {
      NTuple<Location> locPath = mapDescriptorToLocationPath.get(td);
      NTuple<Location> rtrPath = new NTuple<Location>();
      rtrPath.addAll(locPath);
      return rtrPath;
    } else {
      if (td.getSymbol().startsWith("this")) {
        NTuple<Location> thisPath = deriveThisLocationTuple(md);
        NTuple<Location> rtrPath = new NTuple<Location>();
        rtrPath.addAll(thisPath);
        return rtrPath;
      } else {

        if (td.getType().getExtension() != null) {
          SSJavaType ssJavaType = (SSJavaType) td.getType().getExtension();
          if (ssJavaType.getCompLoc() != null) {
            NTuple<Location> rtrPath = new NTuple<Location>();
            rtrPath.addAll(ssJavaType.getCompLoc().getTuple());
            return rtrPath;
          }
        }

        return null;

      }
    }
  }
}