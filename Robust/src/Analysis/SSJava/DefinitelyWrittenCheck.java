package Analysis.SSJava;

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
  private Hashtable<FlatNode, Set<NTuple<Descriptor>>> mapFlatNodeToWrittenSet;

  // maps a temp descriptor to its heap path
  // each temp descriptor has a unique heap path since we do not allow any
  // alias.
  private Hashtable<Descriptor, NTuple<Descriptor>> mapHeapPath;

  // maps a flat method to the READ that is the set of heap path that is
  // expected to be written before method invocation
  private Hashtable<FlatMethod, Set<NTuple<Descriptor>>> mapFlatMethodToRead;

  // maps a flat method to the OVERWRITE that is the set of heap path that is
  // overwritten on every possible path during method invocation
  private Hashtable<FlatMethod, Set<NTuple<Descriptor>>> mapFlatMethodToOverWrite;

  // maps a flat method to the WRITE that is the set of heap path that is
  // written to
  private Hashtable<FlatMethod, Set<NTuple<Descriptor>>> mapFlatMethodToWrite;

  // points to method containing SSJAVA Loop
  private MethodDescriptor methodContainingSSJavaLoop;

  // maps a flatnode to definitely written analysis mapping M
  private Hashtable<FlatNode, Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>>> definitelyWrittenResults;

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

  // maps heap path to the mapping of a shared location and the set of reading
  // descriptors
  // it is for setting clearance flag when all read set is overwritten
  private Hashtable<NTuple<Descriptor>, Hashtable<Location, Set<Descriptor>>> mapHeapPathToLocSharedDescReadSet;

  public static final String arrayElementFieldName = "___element_";
  static protected Hashtable<TypeDescriptor, FieldDescriptor> mapTypeToArrayField;

  private Set<ClearingSummary> possibleCalleeCompleteSummarySetToCaller;

  private LinkedList<MethodDescriptor> sortedDescriptors;

  private FlatNode ssjavaLoopEntrance;
  private LoopFinder ssjavaLoop;
  private Set<FlatNode> loopIncElements;

  private Set<NTuple<Descriptor>> calleeUnionBoundReadSet;
  private Set<NTuple<Descriptor>> calleeIntersectBoundOverWriteSet;
  private Set<NTuple<Descriptor>> calleeBoundWriteSet;

  private Hashtable<Descriptor, Location> mapDescToLocation;

  private TempDescriptor LOCAL;

  public DefinitelyWrittenCheck(SSJavaAnalysis ssjava, State state) {
    this.state = state;
    this.ssjava = ssjava;
    this.callGraph = ssjava.getCallGraph();
    this.mapFlatNodeToWrittenSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapDescriptorToSetDependents = new Hashtable<Descriptor, Set<MethodDescriptor>>();
    this.mapHeapPath = new Hashtable<Descriptor, NTuple<Descriptor>>();
    this.mapFlatMethodToRead = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
    this.mapFlatMethodToOverWrite = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
    this.mapFlatMethodToWrite = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
    this.definitelyWrittenResults =
        new Hashtable<FlatNode, Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>>>();
    this.calleeUnionBoundReadSet = new HashSet<NTuple<Descriptor>>();
    this.calleeIntersectBoundOverWriteSet = new HashSet<NTuple<Descriptor>>();
    this.calleeBoundWriteSet = new HashSet<NTuple<Descriptor>>();

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
    this.mapHeapPathToLocSharedDescReadSet =
        new Hashtable<NTuple<Descriptor>, Hashtable<Location, Set<Descriptor>>>();
  }

  public void definitelyWrittenCheck() {
    if (!ssjava.getAnnotationRequireSet().isEmpty()) {
      methodReadOverWriteAnalysis();
      writtenAnalyis();
      sharedLocationAnalysis();
      checkSharedLocationResult();
    }
  }

  private void checkSharedLocationResult() {

    // mapping of method containing ssjava loop has the final result of
    // shared location analysis

    ClearingSummary result =
        mapMethodDescriptorToCompleteClearingSummary.get(methodContainingSSJavaLoop);

    System.out.println("\n\n#RESULT=" + result);

    Set<NTuple<Descriptor>> hpKeySet = result.keySet();
    for (Iterator iterator = hpKeySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
      SharedStatus state = result.get(hpKey);
      Set<Location> locKeySet = state.getLocationSet();
      for (Iterator iterator2 = locKeySet.iterator(); iterator2.hasNext();) {
        Location locKey = (Location) iterator2.next();
        if (!state.getFlag(locKey)) {
          printNotClearedResult(result);
          throw new Error(
              "Some concrete locations of the shared abstract location are not cleared at the same time:");
        }
      }
    }

  }

  private void printNotClearedResult(ClearingSummary result) {
    Set<NTuple<Descriptor>> keySet = result.keySet();

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
            System.out.println("Concrete locations of the shared location '" + locKey
                + "' are not cleared out, which are reachable through the heap path '" + hpKey
                + "'");
          }
        }
      }
    }

  }

  private void sharedLocationAnalysis() {
    // verify that all concrete locations of shared location are cleared out at
    // the same time once per the out-most loop

    computeReadSharedDescriptorSet();

    System.out.println("###");
    System.out.println("READ SHARED=" + mapHeapPathToLocSharedDescReadSet);

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

        ClearingSummary summaryFromCaller = mapMethodDescriptorToInitialClearingSummary.get(md);
        // System.out.println("###");
        // System.out.println("# summaryFromCaller=" + summaryFromCaller);
        // System.out.println("# completeSummary=" + completeSummary);
        // System.out.println("# prev=" + prevCompleteSummary);
        // System.out.println("# changed!\n");

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
      System.out.println("Definite clearance for shared locations Analyzing: " + md + " "
          + onlyVisitSSJavaLoop);
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

    if (md.getSymbol().startsWith("decodeFrame")) {
      System.out.println("#");
      System.out.println("# method=" + md);
      System.out.println("XXXXX summarySet=" + summarySet);
      System.out.println("XXXXX completeSummary=" + completeSummary);
      System.out.println("#");
    }

    return completeSummary;
  }

  private void sharedLocation_nodeActions(MethodDescriptor caller, FlatNode fn,
      ClearingSummary curr, Set<FlatNode> returnNodeSet, boolean isSSJavaLoop) {

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
            readLocation(curr, rhsHeapPath, getLocation(rhs), rhs);
            writeLocation(curr, lhsHeapPath, getLocation(lhs), lhs);
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

      if (srcHeapPath != null) {
        // if lhs srcHeapPath is null, it means that it is not reachable from
        // callee's parameters. so just ignore it
        NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>(srcHeapPath.getList());

        // if (!fld.getType().isArray() && fld.getType().isImmutable()) {
        // addReadDescriptor(fldHeapPath, getLocation(fld), fld);
        // } else {
        // if (fn.kind() == FKind.FlatFieldNode) {
        // mapDescToLocation.put(lhs, getLocation(fld));
        // readLocation(curr, fldHeapPath, getLocation(fld), fld);
        // } else {
        // fldHeapPath.add(fld);
        // }
        // mapHeapPath.put(lhs, fldHeapPath);
        // }

        if (!fld.getType().isArray() && fld.getType().isImmutable()) {
          Location loc;
          if (fn.kind() == FKind.FlatElementNode) {
            // array element read case
            loc = mapDescToLocation.get(rhs);
            NTuple<Descriptor> newHeapPath = new NTuple<Descriptor>();
            for (int i = 0; i < fldHeapPath.size() - 1; i++) {
              newHeapPath.add(fldHeapPath.get(i));
            }

            Descriptor desc = fldHeapPath.get(fldHeapPath.size() - 1);
            if (desc instanceof FieldDescriptor) {
              fld = (FieldDescriptor) desc;
              fldHeapPath = newHeapPath;
              readLocation(curr, fldHeapPath, loc, fld);
            }
          } else {
            loc = getLocation(fld);
            readLocation(curr, fldHeapPath, loc, fld);
          }
        } else {

          if (fn.kind() != FKind.FlatElementNode) {
            // if it is multi dimensional array, do not need to add heap path
            // because all accesses from the same array is represented by
            // "_element_"
            fldHeapPath.add(fld);
          }
          mapHeapPath.put(lhs, fldHeapPath);
        }

      }

      // }

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

      // write(field)
      NTuple<Descriptor> lhsHeapPath = computePath(lhs);
      NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>(lhsHeapPath.getList());
      if (fld.getType().isImmutable()) {
        writeLocation(curr, fldHeapPath, getLocation(fld), fld);

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

          writeLocation(curr, argHeapPath, loc, fld);
        }

      } else {
        // find out the set of callees
        MethodDescriptor mdCallee = fc.getMethod();
        FlatMethod fmCallee = state.getMethodFlat(mdCallee);
        Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
        // TypeDescriptor typeDesc = fc.getThis().getType();
        setPossibleCallees.addAll(callGraph.getMethods(mdCallee));

        possibleCalleeCompleteSummarySetToCaller.clear();

        for (Iterator iterator = setPossibleCallees.iterator(); iterator.hasNext();) {
          MethodDescriptor mdPossibleCallee = (MethodDescriptor) iterator.next();
          FlatMethod calleeFlatMethod = state.getMethodFlat(mdPossibleCallee);

          addDependent(mdPossibleCallee, // callee
              caller); // caller

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
            // System.out.println("#CALLEE INIT CHANGED=" + mdPossibleCallee);
            // System.out.println("# prev=" + prevCalleeInitSummary);
            // System.out.println("# current=" + calleeInitSummary);
            // System.out.println("#");

            if (!methodDescriptorsToVisitStack.contains(mdPossibleCallee)) {
              methodDescriptorsToVisitStack.add(mdPossibleCallee);
            }

            mapMethodDescriptorToInitialClearingSummary.put(mdPossibleCallee, calleeInitSummary);
          }

        }

        // contribute callee's writing effects to the caller
        mergeSharedLocationAnaylsis(curr, possibleCalleeCompleteSummarySetToCaller);
        if (fc.getMethod().getSymbol().startsWith("decode")) {
          System.out.println("XXXXX callee " + fc + " summary="
              + possibleCalleeCompleteSummarySetToCaller);
          System.out.println("XXXXX curr=" + curr);
        }

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

    // DEBUG!
    // System.out.println("UPDATE WRITE REF=" + heapPath);
    // Descriptor d = heapPath.get(heapPath.size() - 1);
    // boolean print = false;
    // if (d instanceof FieldDescriptor) {
    // FieldDescriptor fd = (FieldDescriptor) d;
    // if (fd.getSymbol().equals("si")) {
    // System.out.println("UPDATE PREV CURR=" + curr);
    // print = true;
    // }
    // }
    // 2. if there exists a tuple t in sharing summary that starts with
    // hp(x) then, set flag of tuple t to 'true'
    Set<NTuple<Descriptor>> hpKeySet = curr.keySet();
    for (Iterator iterator = hpKeySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
      if (hpKey.startsWith(heapPath)) {
        curr.get(hpKey).updateFlag(true);
      }
    }

    // if (print) {
    // System.out.println("UPDATE CURR=" + curr);
    // }
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
        // for (int i = 0; i < fc.numArgs(); i++) {
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

  private void computeReadSharedDescriptorSet() {
    Set<MethodDescriptor> methodDescriptorsToAnalyze = new HashSet<MethodDescriptor>();
    methodDescriptorsToAnalyze.addAll(ssjava.getAnnotationRequireSet());

    for (Iterator iterator = methodDescriptorsToAnalyze.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      FlatMethod fm = state.getMethodFlat(md);
      computeReadSharedDescriptorSet_analyzeMethod(fm, md.equals(methodContainingSSJavaLoop));
    }

  }

  private void computeReadSharedDescriptorSet_analyzeMethod(FlatMethod fm,
      boolean onlyVisitSSJavaLoop) {

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

      computeReadSharedDescriptorSet_nodeActions(fn, onlyVisitSSJavaLoop);

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

  private void computeReadSharedDescriptorSet_nodeActions(FlatNode fn, boolean isSSJavaLoop) {

    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;

    switch (fn.kind()) {
    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;
      lhs = fon.getDest();
      rhs = fon.getLeft();

      if (fon.getOp().getOp() == Operation.ASSIGN) {
        if (rhs.getType().isImmutable() && isSSJavaLoop && (!rhs.getSymbol().startsWith("srctmp"))) {
          // in ssjavaloop, we need to take care about reading local variables!
          NTuple<Descriptor> rhsHeapPath = new NTuple<Descriptor>();
          NTuple<Descriptor> lhsHeapPath = new NTuple<Descriptor>();
          rhsHeapPath.add(LOCAL);
          Location loc = getLocation(rhs);
          addReadDescriptor(rhsHeapPath, loc, rhs);
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

      if (fld.isStatic() && fld.isFinal()) {
        break;
      }

      // read field
      NTuple<Descriptor> srcHeapPath = mapHeapPath.get(rhs);
      if (srcHeapPath != null) {
        // if srcHeapPath is null, it means that it is not reachable from
        // callee's parameters. so just ignore it

        NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>(srcHeapPath.getList());

        if (!fld.getType().isArray() && fld.getType().isImmutable()) {

          Location loc;
          if (fn.kind() == FKind.FlatElementNode) {
            // array element read case
            loc = mapDescToLocation.get(rhs);
            NTuple<Descriptor> newHeapPath = new NTuple<Descriptor>();
            for (int i = 0; i < fldHeapPath.size() - 1; i++) {
              newHeapPath.add(fldHeapPath.get(i));
            }

            Descriptor desc = fldHeapPath.get(fldHeapPath.size() - 1);
            if (desc instanceof FieldDescriptor) {
              fld = (FieldDescriptor) desc;
              fldHeapPath = newHeapPath;
              addReadDescriptor(fldHeapPath, loc, fld);
            }
          } else {
            loc = getLocation(fld);
            addReadDescriptor(fldHeapPath, loc, fld);
          }
        } else {
          // propagate rhs's heap path to the lhs

          if (fn.kind() == FKind.FlatElementNode) {
            mapDescToLocation.put(lhs, getLocation(rhs));
          } else {
            fldHeapPath.add(fld);
          }
          mapHeapPath.put(lhs, fldHeapPath);
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
      // writeLocation(curr, fldHeapPath, fld, getLocation(fld));

    }
      break;

    }
  }

  private boolean hasReadingEffectOnSharedLocation(NTuple<Descriptor> hp, Location loc, Descriptor d) {

    Hashtable<Location, Set<Descriptor>> mapLocToDescSet =
        mapHeapPathToLocSharedDescReadSet.get(hp);
    if (mapLocToDescSet == null) {
      return false;
    } else {
      Set<Descriptor> setDesc = mapLocToDescSet.get(loc);
      if (setDesc == null) {
        return false;
      } else {
        return setDesc.contains(d);
      }
    }

  }

  private void addReadDescriptor(NTuple<Descriptor> hp, Location loc, Descriptor d) {

    if (loc != null && ssjava.isSharedLocation(loc)) {
      Hashtable<Location, Set<Descriptor>> mapLocToDescSet =
          mapHeapPathToLocSharedDescReadSet.get(hp);
      if (mapLocToDescSet == null) {
        mapLocToDescSet = new Hashtable<Location, Set<Descriptor>>();
        mapHeapPathToLocSharedDescReadSet.put(hp, mapLocToDescSet);
      }
      Set<Descriptor> descSet = mapLocToDescSet.get(loc);
      if (descSet == null) {
        descSet = new HashSet<Descriptor>();
        mapLocToDescSet.put(loc, descSet);
      }
      descSet.add(d);
    }

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
        if (te instanceof CompositeLocation) {
          CompositeLocation comp = (CompositeLocation) te;

          return comp.get(comp.getSize() - 1);
        } else {
          return (Location) te;
        }
      }
    }

    return mapDescToLocation.get(d);
  }

  private void writeLocation(ClearingSummary curr, NTuple<Descriptor> hp, Location loc, Descriptor d) {

    // System.out.println("# WRITE LOC hp=" + hp + " d=" + d + " loc=" + loc);
    SharedStatus state = getState(curr, hp);
    if (loc != null && hasReadingEffectOnSharedLocation(hp, loc, d)) {
      // 1. add field x to the clearing set

      state.addVar(loc, d);

      // 3. if the set v contains all of variables belonging to the shared
      // location, set flag to true
      if (overwrittenAllSharedConcreteLocation(hp, loc, state.getVarSet(loc))) {
        state.updateFlag(loc, true);
      }
    }
    state.setWriteEffect(loc);
    // System.out.println("# WRITE CURR=" + curr);

  }

  private boolean overwrittenAllSharedConcreteLocation(NTuple<Descriptor> hp, Location loc,
      Set<Descriptor> writtenSet) {

    Hashtable<Location, Set<Descriptor>> mapLocToDescSet =
        mapHeapPathToLocSharedDescReadSet.get(hp);

    if (mapLocToDescSet != null) {
      Set<Descriptor> descSet = mapLocToDescSet.get(loc);
      if (writtenSet.containsAll(descSet)) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  private void readLocation(ClearingSummary curr, NTuple<Descriptor> hp, Location loc, Descriptor d) {
    // remove reading var x from written set
    // System.out.println("# READ LOC hp=" + hp + " d=" + d + " loc=" + loc);
    if (loc != null && hasReadingEffectOnSharedLocation(hp, loc, d)) {
      SharedStatus state = getState(curr, hp);
      state.removeVar(loc, d);
    }
    // System.out.println("# READ CURR=" + curr);
  }

  private SharedStatus getState(ClearingSummary curr, NTuple<Descriptor> hp) {
    SharedStatus state = curr.get(hp);
    if (state == null) {
      state = new SharedStatus();
      curr.put(hp, state);
    }
    return state;
  }

  private void writtenAnalyis() {
    // perform second stage analysis: intraprocedural analysis ensure that
    // all
    // variables are definitely written in-between the same read

    // First, identify ssjava loop entrace
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

    writtenAnalysis_analyzeLoop();

    if (debugcount > 0) {
      throw new Error();
    }

  }

  private void writtenAnalysis_analyzeLoop() {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(ssjavaLoopEntrance);

    loopIncElements = (Set<FlatNode>) ssjavaLoop.loopIncElements();

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>> prev =
          definitelyWrittenResults.get(fn);

      Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>> curr =
          new Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>>();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>> dwIn =
            definitelyWrittenResults.get(nn);
        if (dwIn != null) {
          merge(curr, dwIn);
        }
      }

      writtenAnalysis_nodeAction(fn, curr, ssjavaLoopEntrance);

      // if a new result, schedule forward nodes for analysis
      if (!curr.equals(prev)) {
        definitelyWrittenResults.put(fn, curr);

        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          if (loopIncElements.contains(nn)) {
            flatNodesToVisit.add(nn);
          }

        }
      }
    }
  }

  private void writtenAnalysis_nodeAction(FlatNode fn,
      Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>> curr, FlatNode loopEntrance) {

    if (fn.equals(loopEntrance)) {
      // it reaches loop entrance: changes all flag to true
      Set<NTuple<Descriptor>> keySet = curr.keySet();
      for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> key = (NTuple<Descriptor>) iterator.next();
        Hashtable<FlatNode, Boolean> pair = curr.get(key);
        if (pair != null) {
          Set<FlatNode> pairKeySet = pair.keySet();
          for (Iterator iterator2 = pairKeySet.iterator(); iterator2.hasNext();) {
            FlatNode pairKey = (FlatNode) iterator2.next();
            pair.put(pairKey, Boolean.TRUE);
          }
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

        NTuple<Descriptor> rhsHeapPath = computePath(rhs);
        if (!rhs.getType().isImmutable()) {
          mapHeapPath.put(lhs, rhsHeapPath);
        } else {
          if (fon.getOp().getOp() == Operation.ASSIGN) {
            // read(rhs)
            readValue(fn, rhsHeapPath, curr);
          }
          // write(lhs)
          NTuple<Descriptor> lhsHeapPath = computePath(lhs);
          removeHeapPath(curr, lhsHeapPath);
        }
      }
        break;

      case FKind.FlatLiteralNode: {
        FlatLiteralNode fln = (FlatLiteralNode) fn;
        lhs = fln.getDst();

        // write(lhs)
        NTuple<Descriptor> lhsHeapPath = computePath(lhs);
        removeHeapPath(curr, lhsHeapPath);

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

        if (fld.isFinal() /* && fld.isStatic() */) {
          // if field is final and static, no need to check
          break;
        }

        // read field
        NTuple<Descriptor> srcHeapPath = mapHeapPath.get(rhs);
        NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>(srcHeapPath.getList());
        fldHeapPath.add(fld);

        if (fld.getType().isImmutable()) {
          readValue(fn, fldHeapPath, curr);
        }

        // propagate rhs's heap path to the lhs
        mapHeapPath.put(lhs, fldHeapPath);

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
        removeHeapPath(curr, fldHeapPath);

      }
        break;

      case FKind.FlatCall: {
        FlatCall fc = (FlatCall) fn;

        bindHeapPathCallerArgWithCaleeParam(fc);
        // add <hp,statement,false> in which hp is an element of
        // READ_bound set
        // of callee: callee has 'read' requirement!

        for (Iterator iterator = calleeUnionBoundReadSet.iterator(); iterator.hasNext();) {
          NTuple<Descriptor> read = (NTuple<Descriptor>) iterator.next();
          Hashtable<FlatNode, Boolean> gen = curr.get(read);
          if (gen == null) {
            gen = new Hashtable<FlatNode, Boolean>();
            curr.put(read, gen);
          }
          Boolean currentStatus = gen.get(fn);
          if (currentStatus == null) {
            gen.put(fn, Boolean.FALSE);
          } else {
            checkFlag(currentStatus.booleanValue(), fn, read);
          }
        }

        // removes <hp,statement,flag> if hp is an element of
        // OVERWRITE_bound
        // set of callee. it means that callee will overwrite it
        for (Iterator iterator = calleeIntersectBoundOverWriteSet.iterator(); iterator.hasNext();) {
          NTuple<Descriptor> write = (NTuple<Descriptor>) iterator.next();
          removeHeapPath(curr, write);
        }
      }
        break;

      }
    }

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

  private void removeHeapPath(Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>> curr,
      NTuple<Descriptor> hp) {

    // removes all of heap path that starts with prefix 'hp'
    // since any reference overwrite along heap path gives overwriting side
    // effects on the value

    Set<NTuple<Descriptor>> keySet = curr.keySet();
    for (Iterator<NTuple<Descriptor>> iter = keySet.iterator(); iter.hasNext();) {
      NTuple<Descriptor> key = iter.next();
      if (key.startsWith(hp)) {
        curr.put(key, new Hashtable<FlatNode, Boolean>());
      }
    }

  }

  private void bindHeapPathCallerArgWithCaleeParam(FlatCall fc) {
    // compute all possible callee set
    // transform all READ/OVERWRITE set from the any possible
    // callees to the
    // caller
    calleeUnionBoundReadSet.clear();
    calleeIntersectBoundOverWriteSet.clear();
    calleeBoundWriteSet.clear();

    if (ssjava.isSSJavaUtil(fc.getMethod().getClassDesc())) {
      // ssjava util case!
      // have write effects on the first argument
      TempDescriptor arg = fc.getArg(0);
      NTuple<Descriptor> argHeapPath = computePath(arg);
      calleeIntersectBoundOverWriteSet.add(argHeapPath);
    } else {
      MethodDescriptor mdCallee = fc.getMethod();
      // FlatMethod fmCallee = state.getMethodFlat(mdCallee);
      Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
      // setPossibleCallees.addAll(callGraph.getMethods(mdCallee, typeDesc));
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

        Set<NTuple<Descriptor>> calleeReadSet = mapFlatMethodToRead.get(calleeFlatMethod);
        if (calleeReadSet == null) {
          calleeReadSet = new HashSet<NTuple<Descriptor>>();
          mapFlatMethodToRead.put(calleeFlatMethod, calleeReadSet);
        }

        Set<NTuple<Descriptor>> calleeOverWriteSet = mapFlatMethodToOverWrite.get(calleeFlatMethod);

        if (calleeOverWriteSet == null) {
          calleeOverWriteSet = new HashSet<NTuple<Descriptor>>();
          mapFlatMethodToOverWrite.put(calleeFlatMethod, calleeOverWriteSet);
        }

        Set<NTuple<Descriptor>> calleeWriteSet = mapFlatMethodToWrite.get(calleeFlatMethod);

        if (calleeWriteSet == null) {
          calleeWriteSet = new HashSet<NTuple<Descriptor>>();
          mapFlatMethodToWrite.put(calleeFlatMethod, calleeWriteSet);
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
        Set<NTuple<Descriptor>> calleeBoundOverWriteSet =
            bindSet(calleeOverWriteSet, mapParamIdx2ParamTempDesc, mapArgIdx2CallerArgHeapPath);
        // intersection of the current overwrite set and the current
        // callee's
        // overwrite set
        merge(calleeIntersectBoundOverWriteSet, calleeBoundOverWriteSet);

        Set<NTuple<Descriptor>> boundWriteSetFromCallee =
            bindSet(calleeWriteSet, mapParamIdx2ParamTempDesc, mapArgIdx2CallerArgHeapPath);
        calleeBoundWriteSet.addAll(boundWriteSetFromCallee);
      }

    }

  }

  private void checkFlag(boolean booleanValue, FlatNode fn, NTuple<Descriptor> hp) {
    if (booleanValue) {
      // the definitely written analysis only takes care about locations that
      // are written to inside of the SSJava loop
      for (Iterator iterator = calleeBoundWriteSet.iterator(); iterator.hasNext();) {
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

  private void merge(Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>> curr,
      Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>> in) {

    Set<NTuple<Descriptor>> inKeySet = in.keySet();
    for (Iterator iterator = inKeySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> inKey = (NTuple<Descriptor>) iterator.next();
      Hashtable<FlatNode, Boolean> inPair = in.get(inKey);

      Set<FlatNode> pairKeySet = inPair.keySet();
      for (Iterator iterator2 = pairKeySet.iterator(); iterator2.hasNext();) {
        FlatNode pairKey = (FlatNode) iterator2.next();
        Boolean inFlag = inPair.get(pairKey);

        Hashtable<FlatNode, Boolean> currPair = curr.get(inKey);
        if (currPair == null) {
          currPair = new Hashtable<FlatNode, Boolean>();
          curr.put(inKey, currPair);
        }

        Boolean currFlag = currPair.get(pairKey);
        // by default, flag is set by false
        if (currFlag == null) {
          currFlag = Boolean.FALSE;
        }
        currFlag = Boolean.valueOf(inFlag.booleanValue() | currFlag.booleanValue());
        currPair.put(pairKey, currFlag);
      }

    }

  }

  private void methodReadOverWriteAnalysis() {
    // perform method READ/OVERWRITE analysis
    Set<MethodDescriptor> methodDescriptorsToAnalyze = new HashSet<MethodDescriptor>();
    methodDescriptorsToAnalyze.addAll(ssjava.getAnnotationRequireSet());

    sortedDescriptors = topologicalSort(methodDescriptorsToAnalyze);

    LinkedList<MethodDescriptor> descriptorListToAnalyze =
        (LinkedList<MethodDescriptor>) sortedDescriptors.clone();

    // no need to analyze method having ssjava loop
    // methodContainingSSJavaLoop = descriptorListToAnalyze.removeFirst();
    methodContainingSSJavaLoop = ssjava.getMethodContainingSSJavaLoop();

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
      // start to analyze leaf node
      MethodDescriptor md = methodDescriptorsToVisitStack.pop();
      FlatMethod fm = state.getMethodFlat(md);

      Set<NTuple<Descriptor>> readSet = new HashSet<NTuple<Descriptor>>();
      Set<NTuple<Descriptor>> overWriteSet = new HashSet<NTuple<Descriptor>>();
      Set<NTuple<Descriptor>> writeSet = new HashSet<NTuple<Descriptor>>();

      methodReadOverWrite_analyzeMethod(fm, readSet, overWriteSet, writeSet);

      Set<NTuple<Descriptor>> prevRead = mapFlatMethodToRead.get(fm);
      Set<NTuple<Descriptor>> prevOverWrite = mapFlatMethodToOverWrite.get(fm);
      Set<NTuple<Descriptor>> prevWrite = mapFlatMethodToWrite.get(fm);

      if (!(readSet.equals(prevRead) && overWriteSet.equals(prevOverWrite) && writeSet
          .equals(prevWrite))) {
        mapFlatMethodToRead.put(fm, readSet);
        mapFlatMethodToOverWrite.put(fm, overWriteSet);
        mapFlatMethodToWrite.put(fm, writeSet);

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

  }

  private void methodReadOverWrite_analyzeMethod(FlatMethod fm, Set<NTuple<Descriptor>> readSet,
      Set<NTuple<Descriptor>> overWriteSet, Set<NTuple<Descriptor>> writeSet) {
    if (state.SSJAVADEBUG) {
      System.out.println("Definitely written Analyzing: " + fm);
    }

    // intraprocedural analysis
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Set<NTuple<Descriptor>> curr = new HashSet<NTuple<Descriptor>>();

      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode prevFn = fn.getPrev(i);
        Set<NTuple<Descriptor>> in = mapFlatNodeToWrittenSet.get(prevFn);
        if (in != null) {
          merge(curr, in);
        }
      }

      methodReadOverWrite_nodeActions(fn, curr, readSet, overWriteSet, writeSet);

      Set<NTuple<Descriptor>> writtenSetPrev = mapFlatNodeToWrittenSet.get(fn);
      if (!curr.equals(writtenSetPrev)) {
        mapFlatNodeToWrittenSet.put(fn, curr);
        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          flatNodesToVisit.add(nn);
        }
      }

    }

  }

  private void methodReadOverWrite_nodeActions(FlatNode fn, Set<NTuple<Descriptor>> writtenSet,
      Set<NTuple<Descriptor>> readSet, Set<NTuple<Descriptor>> overWriteSet,
      Set<NTuple<Descriptor>> writeSet) {
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
        }

      }
    }
      break;

    case FKind.FlatElementNode:
    case FKind.FlatFieldNode: {

      // y=x.f;

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

      if (fld.isFinal() /* && fld.isStatic() */) {
        // if field is final and static, no need to check
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
          if (!writtenSet.contains(readingHeapPath)) {
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
        NTuple<Descriptor> newHeapPath = new NTuple<Descriptor>(lhsHeapPath.getList());
        newHeapPath.add(fld);
        mapHeapPath.put(fld, newHeapPath);

        // write(x.f)
        // need to add hp(y) to WT
        writtenSet.add(newHeapPath);

        writeSet.add(newHeapPath);
      }

    }
      break;

    case FKind.FlatCall: {

      FlatCall fc = (FlatCall) fn;

      bindHeapPathCallerArgWithCaleeParam(fc);

      // add heap path, which is an element of READ_bound set and is not
      // an
      // element of WT set, to the caller's READ set
      for (Iterator iterator = calleeUnionBoundReadSet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> read = (NTuple<Descriptor>) iterator.next();
        if (!writtenSet.contains(read)) {
          readSet.add(read);
        }
      }

      // add heap path, which is an element of OVERWRITE_bound set, to the
      // caller's WT set
      for (Iterator iterator = calleeIntersectBoundOverWriteSet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> write = (NTuple<Descriptor>) iterator.next();
        writtenSet.add(write);
      }

      // add heap path, which is an element of WRITE_BOUND set, to the
      // caller's writeSet
      for (Iterator iterator = calleeBoundWriteSet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> write = (NTuple<Descriptor>) iterator.next();
        writeSet.add(write);
      }

    }
      break;

    case FKind.FlatExit: {
      // merge the current written set with OVERWRITE set
      merge(overWriteSet, writtenSet);
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
      // WrittenSet has a special initial value which covers all possible
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

  private NTuple<Descriptor> computePath(TempDescriptor td) {
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

}