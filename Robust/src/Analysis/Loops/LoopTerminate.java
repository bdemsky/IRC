package Analysis.Loops;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import IR.FieldDescriptor;
import IR.Operation;
import IR.TypeDescriptor;
import IR.Flat.FKind;
import IR.Flat.FlatCondBranch;
import IR.Flat.FlatLiteralNode;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.TempDescriptor;

public class LoopTerminate {

  LoopInvariant loopInv;
  Set<TempDescriptor> inductionSet;

  public void terminateAnalysis(FlatMethod fm, LoopInvariant loopInv) {
    this.loopInv = loopInv;
    this.inductionSet = new HashSet<TempDescriptor>();
    Loops loopFinder = loopInv.root;
    if (loopFinder.nestedLoops().size() > 0) {
      for (Iterator lpit = loopFinder.nestedLoops().iterator(); lpit.hasNext();) {
        Loops loop = (Loops) lpit.next();
        Set entrances = loop.loopEntrances();
        processLoop(loop, loopInv);
      }
    }
  }

  public void processLoop(Loops l, LoopInvariant loopInv) {

    boolean changed = true;

    Set elements = l.loopIncElements();
    Set toprocess = l.loopIncElements();
    Set entrances = l.loopEntrances();
    assert entrances.size() == 1;
    FlatNode entrance = (FlatNode) entrances.iterator().next();

    // find out basic induction variable
    // variable i is a basic induction variable in loop if the only definitions
    // of i within L are of the form i=i+c or i=i-c where c is loop invariant
    for (Iterator elit = elements.iterator(); elit.hasNext();) {
      FlatNode fn = (FlatNode) elit.next();
      if (fn.kind() == FKind.FlatOpNode) {
        FlatOpNode fon = (FlatOpNode) fn;
        int op = fon.getOp().getOp();
        if (op == Operation.ADD || op == Operation.SUB) {
          TempDescriptor tdLeft = fon.getLeft();
          TempDescriptor tdRight = fon.getRight();

          boolean isLeftLoopInvariant = isLoopInvariant(l, fn, tdLeft);
          boolean isRightLoopInvariant = isLoopInvariant(l, fn, tdRight);

          if (isLeftLoopInvariant ^ isRightLoopInvariant) {

            TempDescriptor candidateTemp;

            if (isLeftLoopInvariant) {
              candidateTemp = tdRight;
            } else {
              candidateTemp = tdLeft;
            }

            Set<FlatNode> defSet = loopInv.usedef.defMap(fn, candidateTemp);
            Set<FlatNode> defSetOfLoop = new HashSet<FlatNode>();
            for (Iterator iterator = defSet.iterator(); iterator.hasNext();) {
              FlatNode defFlatNode = (FlatNode) iterator.next();
              if (elements.contains(defFlatNode)) {
                defSetOfLoop.add(defFlatNode);
              }
            }

            if (defSetOfLoop.size() == 1) {
              FlatNode defFn = defSet.iterator().next();
              inductionSet.add(candidateTemp);
            }

          }

        }
      }
    }

    for (Iterator elit = elements.iterator(); elit.hasNext();) {
      FlatNode fn = (FlatNode) elit.next();
      if (fn.kind() == FKind.FlatCondBranch) {
        FlatCondBranch fcb = (FlatCondBranch) fn;
        if (fcb.isLoopBranch()) {

          Set<FlatNode> condSet = getDefinitionInsideLoop(fn, fcb.getTest(), elements);
          assert condSet.size() == 1;
          FlatNode condFn = condSet.iterator().next();
          if (condFn instanceof FlatOpNode) {
            FlatOpNode condOp = (FlatOpNode) condFn;
            // check if guard condition is composed only with induction
            // variables
            checkConditionNode(condOp, elements);
          }

        }
      }

    }

  }

  private boolean checkConditionNode(FlatOpNode fon, Set loopElements) {
    // check flatOpNode that computes loop guard condition
    // currently we assume that induction variable is always getting bigger
    // and guard variable is constant
    // so need to check (1) left operand should be induction variable
    // (2) right operand should be constant or loop invariant

    TempDescriptor left = fon.getLeft();
    TempDescriptor right = fon.getRight();

    // if (inductionSet.contains(left)) {
    // induction = left;
    // guard=right;
    // } else if (inductionSet.contains(right)) {
    // induction = right;
    // guard=left;
    // }

    TempDescriptor induction = null;
    TempDescriptor guard = null;

    if (inductionSet.contains(left)) {
      induction = left;
      guard = right;
    } else {
      // TODO
      // throw new Error("Loop termination error.");
    }

    if (guard != null) {
      Set guardDefSet = getDefinitionInsideLoop(fon, guard, loopElements);
      for (Iterator iterator = guardDefSet.iterator(); iterator.hasNext();) {
        FlatNode guardDef = (FlatNode) iterator.next();
        if (!(guardDef instanceof FlatLiteralNode) && !loopInv.hoisted.contains(guardDef)) {
          // TODO
          // throw new Error("Loop termination error.");
        }
      }
    }

    return true;
  }

  private boolean isLoopInvariant(Loops l, FlatNode fn, TempDescriptor td) {

    Set elements = l.loopIncElements();
    Set<FlatNode> defset = loopInv.usedef.defMap(fn, td);

    Set<FlatNode> defSetOfLoop = new HashSet<FlatNode>();
    for (Iterator<FlatNode> defit = defset.iterator(); defit.hasNext();) {
      FlatNode def = defit.next();
      if (elements.contains(def)) {
        defSetOfLoop.add(def);
      }
    }

    if (defSetOfLoop.size() == 0) {
      // all definition comes from outside the loop
      return true;
    } else if (defSetOfLoop.size() == 1) {
      // check if def is 1) constant node or 2) loop invariant
      FlatNode defFlatNode = defSetOfLoop.iterator().next();
      if (defFlatNode instanceof FlatLiteralNode || loopInv.hoisted.contains(defFlatNode)) {
        return true;
      }
    }

    return false;

  }

  private Set<FlatNode> getDefinitionInsideLoop(FlatNode fn, TempDescriptor td, Set loopElements) {

    Set<FlatNode> defSetOfLoop = new HashSet<FlatNode>();

    Set defSet = loopInv.usedef.defMap(fn, td);
    for (Iterator iterator = defSet.iterator(); iterator.hasNext();) {
      FlatNode defFlatNode = (FlatNode) iterator.next();
      if (loopElements.contains(defFlatNode)) {
        defSetOfLoop.add(defFlatNode);
      }
    }

    return defSetOfLoop;

  }

}
