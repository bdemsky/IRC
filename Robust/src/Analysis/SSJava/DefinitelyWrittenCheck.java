package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import Analysis.Loops.LoopFinder;
import Analysis.Loops.Loops;
import IR.ClassDescriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.Operation;
import IR.State;
import IR.SymbolTable;
import IR.Flat.FKind;
import IR.Flat.FlatFieldNode;
import IR.Flat.FlatLiteralNode;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.TempDescriptor;

public class DefinitelyWrittenCheck {

  static State state;
  HashSet toanalyze;

  // maintains analysis results in the form of <tempDesc,<read statement,flag>>
  private Hashtable<FlatNode, Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>>> definitelyWrittenResults;

  public DefinitelyWrittenCheck(State state) {
    this.state = state;
    this.toanalyze = new HashSet();
    this.definitelyWrittenResults =
        new Hashtable<FlatNode, Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>>>();
  }

  public void definitelyWrittenCheck() {

    // creating map
    SymbolTable classtable = state.getClassSymbolTable();
    toanalyze.addAll(classtable.getValueSet());
    toanalyze.addAll(state.getTaskSymbolTable().getValueSet());
    while (!toanalyze.isEmpty()) {
      Object obj = toanalyze.iterator().next();
      ClassDescriptor cd = (ClassDescriptor) obj;
      toanalyze.remove(cd);
      if (cd.isClassLibrary()) {
        // doesn't care about class libraries now
        continue;
      }
      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        FlatMethod fm = state.getMethodFlat(md);

        LoopFinder loopFinder = new LoopFinder(fm);
        Loops loops = loopFinder.getRootloop(fm);
        Set loopSet = loops.nestedLoops();

        for (Iterator iterator = loopSet.iterator(); iterator.hasNext();) {
          Loops rootLoops = (Loops) iterator.next();
          Set loopEntranceSet = rootLoops.loopEntrances();
          for (Iterator iterator2 = loopEntranceSet.iterator(); iterator2.hasNext();) {
            FlatNode loopEnter = (FlatNode) iterator2.next();
            definitelyWrittenForward(loopEnter);
          }
        }
      }
    }

    // check if there is a read statement with flag=TRUE
    toanalyze.addAll(classtable.getValueSet());
    toanalyze.addAll(state.getTaskSymbolTable().getValueSet());
    while (!toanalyze.isEmpty()) {
      Object obj = toanalyze.iterator().next();
      ClassDescriptor cd = (ClassDescriptor) obj;
      toanalyze.remove(cd);
      if (cd.isClassLibrary()) {
        // doesn't care about class libraries now
        continue;
      }
      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        FlatMethod fm = state.getMethodFlat(md);
        try {
          checkMethodBody(fm);
        } catch (Error e) {
          System.out.println("Error in " + md);
          throw e;
        }
      }
    }

  }

  private void checkMethodBody(FlatMethod fm) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    Set<FlatNode> visited = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      visited.add(fn);
      flatNodesToVisit.remove(fn);

      checkMethodBody_nodeAction(fn);

      // if a new result, schedule forward nodes for analysis
      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);
        if (!visited.contains(nn)) {
          flatNodesToVisit.add(nn);
        }
      }
    }

  }

  private void checkMethodBody_nodeAction(FlatNode fn) {

    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;

    switch (fn.kind()) {

    case FKind.FlatOpNode: {

      FlatOpNode fon = (FlatOpNode) fn;
      if (fon.getOp().getOp() == Operation.ASSIGN) {
        lhs = fon.getDest();
        rhs = fon.getLeft();
        // read(rhs)
        Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>> map =
            definitelyWrittenResults.get(fn);
        if (map != null) {
          if (map.get(rhs).get(fn).booleanValue()) {
            throw new Error("variable " + rhs
                + " was not overwritten in-between the same read statement by the out-most loop.");
          }
        }

      }

    }
      break;

    case FKind.FlatFieldNode: {

      FlatFieldNode ffn = (FlatFieldNode) fn;
      lhs = ffn.getDst();
      rhs = ffn.getSrc();
      fld = ffn.getField();

    }
      break;

    case FKind.FlatElementNode: {

    }
      break;

    case FKind.FlatSetFieldNode: {
    }
      break;

    case FKind.FlatSetElementNode: {

    }
      break;

    case FKind.FlatCall: {

    }
      break;

    }

  }

  private void definitelyWrittenForward(FlatNode entrance) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(entrance);

    while (!flatNodesToVisit.isEmpty()) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>> prev =
          definitelyWrittenResults.get(fn);

      Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>> curr =
          new Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>>();
      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode nn = fn.getPrev(i);
        Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>> dwIn =
            definitelyWrittenResults.get(nn);
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

  private void mergeResults(Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>> curr,
      Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>> in) {

    Set<TempDescriptor> inKeySet = in.keySet();
    for (Iterator iterator = inKeySet.iterator(); iterator.hasNext();) {
      TempDescriptor inKey = (TempDescriptor) iterator.next();
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
      Hashtable<TempDescriptor, Hashtable<FlatNode, Boolean>> curr, FlatNode entrance) {

    if (fn == entrance) {

      Set<TempDescriptor> keySet = curr.keySet();
      for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
        TempDescriptor key = (TempDescriptor) iterator.next();
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
        if (fon.getOp().getOp() == Operation.ASSIGN) {
          rhs = fon.getLeft();

          // read(rhs)
          Hashtable<FlatNode, Boolean> gen = curr.get(rhs);
          if (gen == null) {
            gen = new Hashtable<FlatNode, Boolean>();
            curr.put(rhs, gen);
          }

          Boolean currentStatus = gen.get(fn);
          if (currentStatus == null) {
            gen.put(fn, Boolean.FALSE);
          }
        }
        // write(lhs)
        curr.put(lhs, new Hashtable<FlatNode, Boolean>());

      }
        break;

      case FKind.FlatLiteralNode: {
        FlatLiteralNode fln = (FlatLiteralNode) fn;
        lhs = fln.getDst();

        // write(lhs)
        curr.put(lhs, new Hashtable<FlatNode, Boolean>());

      }
        break;

      case FKind.FlatFieldNode: {

        FlatFieldNode ffn = (FlatFieldNode) fn;
        lhs = ffn.getDst();
        rhs = ffn.getSrc();
        fld = ffn.getField();

      }
        break;

      case FKind.FlatElementNode: {

      }
        break;

      case FKind.FlatSetFieldNode: {
      }
        break;

      case FKind.FlatSetElementNode: {

      }
        break;

      case FKind.FlatCall: {

      }
        break;

      }
    }

  }

}
