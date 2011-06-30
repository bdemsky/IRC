package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import Analysis.CallGraph.CallGraph;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.Operation;
import IR.State;
import IR.Flat.FKind;
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

  private Hashtable<FlatNode, Hashtable<Descriptor, Hashtable<FlatNode, Boolean>>> definitelyWrittenResults;

  public DefinitelyWrittenCheck(SSJavaAnalysis ssjava, State state) {
    this.state = state;
    this.ssjava = ssjava;
    this.callGraph = ssjava.getCallGraph();
    this.mapFlatNodeToWrittenSet = new Hashtable<FlatNode, Set<NTuple<Descriptor>>>();
    this.mapDescriptorToSetDependents = new Hashtable<Descriptor, Set<MethodDescriptor>>();
    this.mapHeapPath = new Hashtable<Descriptor, NTuple<Descriptor>>();
    this.mapFlatMethodToRead = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
    this.mapFlatMethodToOverWrite = new Hashtable<FlatMethod, Set<NTuple<Descriptor>>>();
  }

  public void definitelyWrittenCheck() {

    analyzeMethods();
  }

  private void analyzeMethods() {
    // perform method READ/OVERWRITE analysis

    Set<MethodDescriptor> methodDescriptorsToAnalyze = new HashSet<MethodDescriptor>();
    methodDescriptorsToAnalyze.addAll(ssjava.getAnnotationRequireSet());

    LinkedList<MethodDescriptor> sortedDescriptors = topologicalSort(methodDescriptorsToAnalyze);

    // no need to analyze method having ssjava loop
    sortedDescriptors.removeFirst();

    // analyze scheduled methods until there are no more to visit
    while (!sortedDescriptors.isEmpty()) {
      // start to analyze leaf node
      MethodDescriptor md = sortedDescriptors.removeLast();
      analyzeMethod(md);
    }

  }

  private void analyzeMethod(MethodDescriptor md) {
    if (state.SSJAVADEBUG) {
      System.out.println("Definitely written Analyzing: " + md);
    }

    FlatMethod fm = state.getMethodFlat(md);

    Set<NTuple<Descriptor>> readSet = mapFlatMethodToRead.get(fm);
    if (readSet == null) {
      readSet = new HashSet<NTuple<Descriptor>>();
      mapFlatMethodToRead.put(fm, readSet);
    }

    Set<NTuple<Descriptor>> overWriteSet = mapFlatMethodToOverWrite.get(fm);
    if (overWriteSet == null) {
      overWriteSet = new HashSet<NTuple<Descriptor>>();
      mapFlatMethodToOverWrite.put(fm, overWriteSet);
    }

    // intraprocedural analysis
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Set<NTuple<Descriptor>> prev = mapFlatNodeToWrittenSet.get(fn);
      Set<NTuple<Descriptor>> curr = new HashSet<NTuple<Descriptor>>();

      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode prevFn = fn.getPrev(i);
        Set<NTuple<Descriptor>> in = mapFlatNodeToWrittenSet.get(prevFn);
        if (in != null) {
          merge(curr, in);
        }
      }

      analyzeFlatNode(fn, curr, readSet, overWriteSet);

      // if a new result, schedule forward nodes for analysis
      if (!curr.equals(prev)) {
        mapFlatNodeToWrittenSet.put(fn, curr);

        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          flatNodesToVisit.add(nn);
        }
      }

    }

    System.out.println("READSET=" + mapFlatMethodToRead.get(fm));
    System.out.println("OVERWRITESET=" + mapFlatMethodToOverWrite.get(fm));

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

  private void analyzeFlatNode(FlatNode fn, Set<NTuple<Descriptor>> writtenSet,
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
      // for a normal assign node, need to propagate lhs's heap path to rhs
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

    case FKind.FlatExit: {
      // merge the current written set with OVERWRITE set
      merge(overWriteSet, writtenSet);
    }
      break;

    }
  }

  // Borrowed it from disjoint analysis
  protected LinkedList<MethodDescriptor> topologicalSort(Set<MethodDescriptor> toSort) {

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
  protected void dfsVisit(MethodDescriptor md, Set<MethodDescriptor> toSort,
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
  protected void addDependent(MethodDescriptor callee, MethodDescriptor caller) {
    Set<MethodDescriptor> deps = mapDescriptorToSetDependents.get(callee);
    if (deps == null) {
      deps = new HashSet<MethodDescriptor>();
    }
    deps.add(caller);
    mapDescriptorToSetDependents.put(callee, deps);
  }

  private void definitelyWrittenForward(FlatNode entrance) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(entrance);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Hashtable<Descriptor, Hashtable<FlatNode, Boolean>> prev = definitelyWrittenResults.get(fn);

      Hashtable<Descriptor, Hashtable<FlatNode, Boolean>> curr =
          new Hashtable<Descriptor, Hashtable<FlatNode, Boolean>>();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        Hashtable<Descriptor, Hashtable<FlatNode, Boolean>> dwIn = definitelyWrittenResults.get(nn);
        if (dwIn != null) {
          mergeResults(curr, dwIn);
        }
      }

      definitelyWritten_nodeActions(fn, curr, entrance);

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

  private void mergeResults(Hashtable<Descriptor, Hashtable<FlatNode, Boolean>> curr,
      Hashtable<Descriptor, Hashtable<FlatNode, Boolean>> in) {

    Set<Descriptor> inKeySet = in.keySet();
    for (Iterator iterator = inKeySet.iterator(); iterator.hasNext();) {
      Descriptor inKey = (Descriptor) iterator.next();
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

  private void definitelyWritten_nodeActions(FlatNode fn,
      Hashtable<Descriptor, Hashtable<FlatNode, Boolean>> curr, FlatNode entrance) {

    if (fn == entrance) {

      Set<Descriptor> keySet = curr.keySet();
      for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
        Descriptor key = (Descriptor) iterator.next();
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
        System.out.println("\nfon=" + fon);

        if (fon.getOp().getOp() == Operation.ASSIGN) {

          // read(rhs)
          Hashtable<FlatNode, Boolean> gen = curr.get(rhs);
          if (gen == null) {
            gen = new Hashtable<FlatNode, Boolean>();
            curr.put(rhs, gen);
          }
          System.out.println("READ LOC=" + rhs.getType().getExtension());

          Boolean currentStatus = gen.get(fn);
          if (currentStatus == null) {
            gen.put(fn, Boolean.FALSE);
          }
        }
        // write(lhs)
        curr.put(lhs, new Hashtable<FlatNode, Boolean>());
        System.out.println("WRITING LOC=" + lhs.getType().getExtension());

      }
        break;

      case FKind.FlatLiteralNode: {
        FlatLiteralNode fln = (FlatLiteralNode) fn;
        lhs = fln.getDst();

        // write(lhs)
        curr.put(lhs, new Hashtable<FlatNode, Boolean>());

        System.out.println("WRITING LOC=" + lhs.getType().getExtension());

      }
        break;

      case FKind.FlatFieldNode:
      case FKind.FlatElementNode: {

        FlatFieldNode ffn = (FlatFieldNode) fn;
        lhs = ffn.getSrc();
        fld = ffn.getField();

        // read field
        Hashtable<FlatNode, Boolean> gen = curr.get(fld);
        if (gen == null) {
          gen = new Hashtable<FlatNode, Boolean>();
          curr.put(fld, gen);
        }
        Boolean currentStatus = gen.get(fn);
        if (currentStatus == null) {
          gen.put(fn, Boolean.FALSE);
        }

        System.out.println("\nffn=" + ffn);
        System.out.println("READ LOCfld=" + fld.getType().getExtension());
        System.out.println("READ LOClhs=" + lhs.getType().getExtension());

      }
        break;

      case FKind.FlatSetFieldNode:
      case FKind.FlatSetElementNode: {

        FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
        fld = fsfn.getField();

        // write(field)
        curr.put(fld, new Hashtable<FlatNode, Boolean>());

        System.out.println("\nfsfn=" + fsfn);
        System.out.println("WRITELOC LOC=" + fld.getType().getExtension());

      }
        break;

      case FKind.FlatCall: {

      }
        break;

      }
    }

  }

}
