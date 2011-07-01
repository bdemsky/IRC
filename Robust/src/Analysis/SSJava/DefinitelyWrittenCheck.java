package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

import Analysis.CallGraph.CallGraph;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.Operation;
import IR.State;
import IR.TypeDescriptor;
import IR.Flat.FKind;
import IR.Flat.FlatCall;
import IR.Flat.FlatFieldNode;
import IR.Flat.FlatLiteralNode;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.FlatSetFieldNode;
import IR.Flat.TempDescriptor;

public class DefinitelyWrittenCheck {

  SSJavaAnalysis ssjava;
  State state;
  CallGraph callGraph;

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

  // points to method containing SSJAVA Loop
  private MethodDescriptor methodContainingSSJavaLoop;

  // maps a flatnode to definitely written analysis mapping M
  private Hashtable<FlatNode, Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>>> definitelyWrittenResults;

  private Set<NTuple<Descriptor>> calleeUnionBoundReadSet;
  private Set<NTuple<Descriptor>> calleeIntersectBoundOverWriteSet;

  public DefinitelyWrittenCheck(SSJavaAnalysis ssjava, State state) {
    this.state = state;
    this.ssjava = ssjava;
    this.callGraph = ssjava.getCallGraph();
    this.mapFlatNodeToWrittenSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapDescriptorToSetDependents = new Hashtable<Descriptor, Set<MethodDescriptor>>();
    this.mapHeapPath = new Hashtable<Descriptor, NTuple<Descriptor>>();
    this.mapFlatMethodToRead = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
    this.mapFlatMethodToOverWrite = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
    this.definitelyWrittenResults =
        new Hashtable<FlatNode, Hashtable<NTuple<Descriptor>, Hashtable<FlatNode, Boolean>>>();
    this.calleeUnionBoundReadSet = new HashSet<NTuple<Descriptor>>();
    this.calleeIntersectBoundOverWriteSet = new HashSet<NTuple<Descriptor>>();
  }

  public void definitelyWrittenCheck() {
    if (!ssjava.getAnnotationRequireSet().isEmpty()) {
      methodReadOverWriteAnalysis();
      writtenAnalyis();
    }
  }

  private void writtenAnalyis() {
    // perform second stage analysis: intraprocedural analysis ensure that
    // all
    // variables are definitely written in-between the same read

    // First, identify ssjava loop entrace
    FlatMethod fm = state.getMethodFlat(methodContainingSSJavaLoop);
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    FlatNode entrance = null;

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      String label = (String) state.fn2labelMap.get(fn);
      if (label != null) {

        if (label.equals(ssjava.SSJAVA)) {
          entrance = fn;
          break;
        }
      }

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);
        flatNodesToVisit.add(nn);
      }
    }

    assert entrance != null;

    writtenAnalysis_analyzeLoop(entrance);

  }

  private void writtenAnalysis_analyzeLoop(FlatNode entrance) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(entrance);

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

      writtenAnalysis_nodeAction(fn, curr, entrance);

      // if a new result, schedule forward nodes for analysis
      if (!curr.equals(prev)) {
        definitelyWrittenResults.put(fn, curr);

        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          flatNodesToVisit.add(nn);
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
        }

        if (fon.getOp().getOp() == Operation.ASSIGN) {
          // read(rhs)
          Hashtable<FlatNode, Boolean> gen = curr.get(rhsHeapPath);

          if (gen == null) {
            gen = new Hashtable<FlatNode, Boolean>();
            curr.put(rhsHeapPath, gen);
          }
          Boolean currentStatus = gen.get(fn);
          if (currentStatus == null) {
            gen.put(fn, Boolean.FALSE);
          } else {
            if (!rhs.getType().isClass()) {
              checkFlag(currentStatus.booleanValue(), fn);
            }
          }

        }
        // write(lhs)
        NTuple<Descriptor> lhsHeapPath = computePath(lhs);
        removeHeapPath(curr, lhsHeapPath);
        // curr.put(lhsHeapPath, new Hashtable<FlatNode, Boolean>());
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

        FlatFieldNode ffn = (FlatFieldNode) fn;
        lhs = ffn.getSrc();
        fld = ffn.getField();

        // read field
        NTuple<Descriptor> srcHeapPath = mapHeapPath.get(lhs);
        NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>(srcHeapPath.getList());
        fldHeapPath.add(fld);
        Hashtable<FlatNode, Boolean> gen = curr.get(fldHeapPath);

        if (gen == null) {
          gen = new Hashtable<FlatNode, Boolean>();
          curr.put(fldHeapPath, gen);
        }

        Boolean currentStatus = gen.get(fn);
        if (currentStatus == null) {
          gen.put(fn, Boolean.FALSE);
        } else {
          checkFlag(currentStatus.booleanValue(), fn);
        }

      }
        break;

      case FKind.FlatSetFieldNode:
      case FKind.FlatSetElementNode: {

        FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
        lhs = fsfn.getDst();
        fld = fsfn.getField();

        // write(field)
        NTuple<Descriptor> lhsHeapPath = mapHeapPath.get(lhs);
        NTuple<Descriptor> fldHeapPath = new NTuple<Descriptor>(lhsHeapPath.getList());
        fldHeapPath.add(fld);
        removeHeapPath(curr, fldHeapPath);
        // curr.put(fldHeapPath, new Hashtable<FlatNode, Boolean>());

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
            checkFlag(currentStatus.booleanValue(), fn);
          }
        }

        // removes <hp,statement,flag> if hp is an element of
        // OVERWRITE_bound
        // set of callee. it means that callee will overwrite it
        for (Iterator iterator = calleeIntersectBoundOverWriteSet.iterator(); iterator.hasNext();) {
          NTuple<Descriptor> write = (NTuple<Descriptor>) iterator.next();
          removeHeapPath(curr, write);
          // curr.put(write, new Hashtable<FlatNode, Boolean>());
        }
      }
        break;

      }

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
    MethodDescriptor mdCallee = fc.getMethod();
    FlatMethod fmCallee = state.getMethodFlat(mdCallee);
    Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
    TypeDescriptor typeDesc = fc.getThis().getType();
    setPossibleCallees.addAll(callGraph.getMethods(mdCallee, typeDesc));

    // create mapping from arg idx to its heap paths
    Hashtable<Integer, NTuple<Descriptor>> mapArgIdx2CallerArgHeapPath =
        new Hashtable<Integer, NTuple<Descriptor>>();

    // arg idx is starting from 'this' arg
    NTuple<Descriptor> thisHeapPath = new NTuple<Descriptor>();
    thisHeapPath.add(fc.getThis());
    mapArgIdx2CallerArgHeapPath.put(Integer.valueOf(0), thisHeapPath);

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

      Hashtable<Integer, TempDescriptor> mapParamIdx2ParamTempDesc =
          new Hashtable<Integer, TempDescriptor>();
      for (int i = 0; i < calleeFlatMethod.numParameters(); i++) {
        TempDescriptor param = calleeFlatMethod.getParameter(i);
        mapParamIdx2ParamTempDesc.put(Integer.valueOf(i), param);
      }

      Set<NTuple<Descriptor>> calleeBoundReadSet =
          bindSet(calleeReadSet, mapParamIdx2ParamTempDesc, mapArgIdx2CallerArgHeapPath);
      // union of the current read set and the current callee's
      // read set
      calleeUnionBoundReadSet.addAll(calleeBoundReadSet);
      Set<NTuple<Descriptor>> calleeBoundWriteSet =
          bindSet(calleeOverWriteSet, mapParamIdx2ParamTempDesc, mapArgIdx2CallerArgHeapPath);
      // intersection of the current overwrite set and the current
      // callee's
      // overwrite set
      merge(calleeIntersectBoundOverWriteSet, calleeBoundWriteSet);
    }

  }

  private void checkFlag(boolean booleanValue, FlatNode fn) {
    if (booleanValue) {
      throw new Error(
          "There is a variable who comes back to the same read statement at the out-most iteration at "
              + methodContainingSSJavaLoop.getClassDesc().getSourceFileName() + "::"
              + fn.getNumLine());
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

    LinkedList<MethodDescriptor> sortedDescriptors = topologicalSort(methodDescriptorsToAnalyze);

    // no need to analyze method having ssjava loop
    methodContainingSSJavaLoop = sortedDescriptors.removeFirst();

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by
    // dependency in the call graph
    Stack<MethodDescriptor> methodDescriptorsToVisitStack = new Stack<MethodDescriptor>();

    Set<MethodDescriptor> methodDescriptorToVistSet = new HashSet<MethodDescriptor>();
    methodDescriptorToVistSet.addAll(sortedDescriptors);

    while (!sortedDescriptors.isEmpty()) {
      MethodDescriptor md = sortedDescriptors.removeFirst();
      methodDescriptorsToVisitStack.add(md);
    }

    // analyze scheduled methods until there are no more to visit
    while (!methodDescriptorsToVisitStack.isEmpty()) {
      // start to analyze leaf node
      MethodDescriptor md = methodDescriptorsToVisitStack.pop();
      FlatMethod fm = state.getMethodFlat(md);

      Set<NTuple<Descriptor>> readSet = new HashSet<NTuple<Descriptor>>();
      Set<NTuple<Descriptor>> overWriteSet = new HashSet<NTuple<Descriptor>>();

      methodReadOverWrite_analyzeMethod(fm, readSet, overWriteSet);

      Set<NTuple<Descriptor>> prevRead = mapFlatMethodToRead.get(fm);
      Set<NTuple<Descriptor>> prevOverWrite = mapFlatMethodToOverWrite.get(fm);

      if (!(readSet.equals(prevRead) && overWriteSet.equals(prevOverWrite))) {
        mapFlatMethodToRead.put(fm, readSet);
        mapFlatMethodToOverWrite.put(fm, overWriteSet);

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
      Set<NTuple<Descriptor>> overWriteSet) {
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

      methodReadOverWrite_nodeActions(fn, curr, readSet, overWriteSet);

      mapFlatNodeToWrittenSet.put(fn, curr);

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);
        flatNodesToVisit.add(nn);
      }

    }

  }

  private void methodReadOverWrite_nodeActions(FlatNode fn, Set<NTuple<Descriptor>> writtenSet,
      Set<NTuple<Descriptor>> readSet, Set<NTuple<Descriptor>> overWriteSet) {
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

    case FKind.FlatFieldNode:
    case FKind.FlatElementNode: {

      // y=x.f;

      FlatFieldNode ffn = (FlatFieldNode) fn;
      lhs = ffn.getDst();
      rhs = ffn.getSrc();
      fld = ffn.getField();

      // set up heap path
      NTuple<Descriptor> srcHeapPath = mapHeapPath.get(rhs);
      NTuple<Descriptor> readingHeapPath = new NTuple<Descriptor>(srcHeapPath.getList());
      readingHeapPath.add(fld);
      mapHeapPath.put(lhs, readingHeapPath);

      // read (x.f)
      // if WT doesnot have hp(x.f), add hp(x.f) to READ
      if (!writtenSet.contains(readingHeapPath)) {
        readSet.add(readingHeapPath);
      }

      // need to kill hp(x.f) from WT
      writtenSet.remove(readingHeapPath);

    }
      break;

    case FKind.FlatSetFieldNode:
    case FKind.FlatSetElementNode: {

      // x.f=y;
      FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
      lhs = fsfn.getDst();
      fld = fsfn.getField();
      rhs = fsfn.getSrc();

      // set up heap path
      NTuple<Descriptor> lhsHeapPath = mapHeapPath.get(lhs);
      NTuple<Descriptor> newHeapPath = new NTuple<Descriptor>(lhsHeapPath.getList());
      newHeapPath.add(fld);
      mapHeapPath.put(fld, newHeapPath);

      // write(x.f)
      // need to add hp(y) to WT
      writtenSet.add(newHeapPath);

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
      writtenSet.removeAll(calleeUnionBoundReadSet);

      // add heap path, which is an element of OVERWRITE_bound set, to the
      // caller's WT set
      for (Iterator iterator = calleeIntersectBoundOverWriteSet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> write = (NTuple<Descriptor>) iterator.next();
        writtenSet.add(write);
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

    // otherwise call graph guides DFS
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