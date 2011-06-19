package Analysis.Loops;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import IR.Operation;
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
        processLoop(fm, loop, loopInv);
      }
    }
  }

  public void processLoop(FlatMethod fm, Loops l, LoopInvariant loopInv) {

    boolean changed = true;

    Set elements = l.loopIncElements();
    Set toprocess = l.loopIncElements();
    Set entrances = l.loopEntrances();
    assert entrances.size() == 1;
    FlatNode entrance = (FlatNode) entrances.iterator().next();

    Hashtable<TempDescriptor, FlatNode> inductionVar2DefNode =
        new Hashtable<TempDescriptor, FlatNode>();

    Hashtable<TempDescriptor, TempDescriptor> derivedVar2basicInduction =
        new Hashtable<TempDescriptor, TempDescriptor>();

    Set<FlatNode> computed = new HashSet<FlatNode>();

    int backEdgeWithInductionCond = 0;

    // #1 find out basic induction variable
    // variable i is a basic induction variable in loop if the only definitions
    // of i within L are of the form i=i+c or i=i-c where c is loop invariant
    for (Iterator elit = elements.iterator(); elit.hasNext();) {
      FlatNode fn = (FlatNode) elit.next();
      if (fn.kind() == FKind.FlatOpNode) {
        FlatOpNode fon = (FlatOpNode) fn;
        int op = fon.getOp().getOp();
        if (op == Operation.ADD /* || op == Operation.SUB */) {
          TempDescriptor tdLeft = fon.getLeft();
          TempDescriptor tdRight = fon.getRight();

          boolean isLeftLoopInvariant = isLoopInvariantVar(l, fn, tdLeft);
          boolean isRightLoopInvariant = isLoopInvariantVar(l, fn, tdRight);

          if (isLeftLoopInvariant ^ isRightLoopInvariant) {

            TempDescriptor candidateTemp;

            if (isLeftLoopInvariant) {
              candidateTemp = tdRight;
            } else {
              candidateTemp = tdLeft;
            }

            Set<FlatNode> defSetOfLoop = getDefinitionInsideLoop(l, fn, candidateTemp);
            if (defSetOfLoop.size() == 1) {
              FlatNode defNode = defSetOfLoop.iterator().next();
              assert defNode.readsTemps().length == 1;

              TempDescriptor readTemp = defNode.readsTemps()[0];
              if (readTemp.equals(fon.getDest())) {
                inductionVar2DefNode.put(candidateTemp, defSetOfLoop.iterator().next());
                inductionSet.add(candidateTemp);
                computed.add(fn);
              }

            }

          }

        }
      }
    }

    // #2 detect derived induction variables
    // variable k is a derived induction variable if
    // 1) there is only one definition of k within the loop, of the form k=j*c
    // or k=j+d where j is induction variable, c, d are loop-invariant
    // 2) and if j is a derived induction variable in the family of i, then:
    // (a) the only definition of j that reaches k is the one in the loop
    // (b) and there is no definition of i on any path between the definition of
    // j and the definition of k

    Set<TempDescriptor> basicInductionSet = new HashSet<TempDescriptor>();
    basicInductionSet.addAll(inductionSet);

    while (changed) {
      changed = false;
      for (Iterator elit = elements.iterator(); elit.hasNext();) {
        FlatNode fn = (FlatNode) elit.next();
        if (!computed.contains(fn)) {
          if (fn.kind() == FKind.FlatOpNode) {
            FlatOpNode fon = (FlatOpNode) fn;
            int op = fon.getOp().getOp();
            if (op == Operation.ADD || op == Operation.MULT) {
              TempDescriptor tdLeft = fon.getLeft();
              TempDescriptor tdRight = fon.getRight();
              TempDescriptor tdDest = fon.getDest();

              boolean isLeftLoopInvariant = isLoopInvariantVar(l, fn, tdLeft);
              boolean isRightLoopInvariant = isLoopInvariantVar(l, fn, tdRight);

              if (isLeftLoopInvariant ^ isRightLoopInvariant) {
                TempDescriptor inductionOp;
                if (isLeftLoopInvariant) {
                  inductionOp = tdRight;
                } else {
                  inductionOp = tdLeft;
                }
                if (inductionSet.contains(inductionOp)) {
                  // find new derived one k

                  if (!basicInductionSet.contains(inductionOp)) {
                    // check if only definition of j that reaches k is the one
                    // in
                    // the loop
                    Set defSet = getDefinitionInsideLoop(l, fn, inductionOp);
                    if (defSet.size() == 1) {
                      // check if there is no def of i on any path bet' def of j
                      // and def of k

                      TempDescriptor originInduc = derivedVar2basicInduction.get(inductionOp);
                      FlatNode defI = inductionVar2DefNode.get(originInduc);
                      FlatNode defJ = inductionVar2DefNode.get(inductionOp);
                      FlatNode defk = fn;

                      if (!checkPath(defI, defJ, defk)) {
                        continue;
                      }

                    }
                  }
                  // add new induction var

                  Set<FlatNode> setUseNode = loopInv.usedef.useMap(fn, tdDest);
                  assert setUseNode.size() == 1;
                  assert setUseNode.iterator().next().writesTemps().length == 1;

                  TempDescriptor derivedInd = setUseNode.iterator().next().writesTemps()[0];
                  FlatNode defNode = setUseNode.iterator().next();

                  computed.add(fn);
                  computed.add(defNode);
                  inductionSet.add(derivedInd);
                  inductionVar2DefNode.put(derivedInd, defNode);
                  derivedVar2basicInduction.put(derivedInd, inductionOp);
                  changed = true;
                }

              }

            }

          }
        }

      }
    }

    // #3 check condition branch
    for (Iterator elit = elements.iterator(); elit.hasNext();) {
      FlatNode fn = (FlatNode) elit.next();
      if (fn.kind() == FKind.FlatCondBranch) {
        FlatCondBranch fcb = (FlatCondBranch) fn;

        if (fcb.isLoopBranch() || hasLoopExitNode(l, fcb, true)) {
          // only need to care about conditional branch that leads it out of the
          // loop
          Set<FlatNode> condSet = getDefinitionInsideLoop(l, fn, fcb.getTest());
          assert condSet.size() == 1;
          FlatNode condFn = condSet.iterator().next();
          if (condFn instanceof FlatOpNode) {
            FlatOpNode condOp = (FlatOpNode) condFn;
            // check if guard condition is composed only with induction
            // variables
            if (checkConditionNode(l, condOp)) {
              backEdgeWithInductionCond++;
            }
          }
        }
      }

    }

    if (backEdgeWithInductionCond == 0) {
      throw new Error("Loop may never terminate at "
          + fm.getMethod().getClassDesc().getSourceFileName() + "::" + entrance.numLine);
    }

  }

  private boolean checkPath(FlatNode def, FlatNode start, FlatNode end) {

    // return true if there is no def in-bet start and end

    Set<FlatNode> endSet = new HashSet<FlatNode>();
    endSet.add(end);
    if ((start.getReachableSet(endSet)).contains(def)) {
      return false;
    }

    return true;
  }

  private boolean checkConditionNode(Loops l, FlatOpNode fon) {
    // check flatOpNode that computes loop guard condition
    // currently we assume that induction variable is always getting bigger
    // and guard variable is constant
    // so need to check (1) one of operand should be induction variable
    // (2) another operand should be constant or loop invariant

    TempDescriptor induction = null;
    TempDescriptor guard = null;

    int op = fon.getOp().getOp();
    if (op == Operation.LT || op == Operation.LTE) {
      // condition is inductionVar <= loop invariant
      induction = fon.getLeft();
      guard = fon.getRight();
    } else if (op == Operation.GT || op == Operation.GTE) {
      // condition is loop invariant >= inductionVar
      induction = fon.getRight();
      guard = fon.getLeft();
    } else {
      return false;
    }

    if (!IsInductionVar(l, fon, induction)) {
      return false;
    }

    if (guard != null) {
      Set guardDefSet = getDefinitionInsideLoop(l, fon, guard);
      for (Iterator iterator = guardDefSet.iterator(); iterator.hasNext();) {
        FlatNode guardDef = (FlatNode) iterator.next();
        if (!(guardDef instanceof FlatLiteralNode) && !loopInv.hoisted.contains(guardDef)) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean IsInductionVar(Loops l, FlatNode fn, TempDescriptor td) {

    if (inductionSet.contains(td)) {
      return true;
    } else {
      // check if td is composed by induction variables
      Set<FlatNode> defSet = getDefinitionInsideLoop(l, fn, td);
      for (Iterator iterator = defSet.iterator(); iterator.hasNext();) {
        FlatNode defNode = (FlatNode) iterator.next();

        TempDescriptor[] readTemps = defNode.readsTemps();
        for (int i = 0; i < readTemps.length; i++) {

          if (!IsInductionVar(l, defNode, readTemps[i])) {
            if (!isLoopInvariantVar(l, defNode, readTemps[i])) {
              return false;
            }
          }
        }

      }
    }
    return true;
  }

  private boolean isLoopInvariantVar(Loops l, FlatNode fn, TempDescriptor td) {

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
      // so it is loop invariant
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

  private Set<FlatNode> getDefinitionInsideLoop(Loops l, FlatNode fn, TempDescriptor td) {

    Set<FlatNode> defSetOfLoop = new HashSet<FlatNode>();
    Set loopElements = l.loopIncElements();

    Set defSet = loopInv.usedef.defMap(fn, td);
    for (Iterator iterator = defSet.iterator(); iterator.hasNext();) {
      FlatNode defFlatNode = (FlatNode) iterator.next();
      if (loopElements.contains(defFlatNode)) {
        defSetOfLoop.add(defFlatNode);
      }
    }

    return defSetOfLoop;

  }

  private boolean hasLoopExitNode(Loops l, FlatCondBranch fcb, boolean fromTrueBlock) {

    Set loopElements = l.loopIncElements();
    Set entrances = l.loopEntrances();
    FlatNode fn = (FlatNode) entrances.iterator().next();

    if (!fromTrueBlock) {
      // in this case, FlatCondBranch must have two next flat node, one for true
      // block and one for false block
      assert fcb.next.size() == 2;
    }

    FlatNode next;
    if (fromTrueBlock) {
      next = fcb.getNext(0);
    } else {
      next = fcb.getNext(1);
    }

    if (hasLoopExitNode(fn, next, loopElements)) {
      return true;
    } else {
      return false;
    }

  }

  private boolean hasLoopExitNode(FlatNode loopHeader, FlatNode start, Set loopElements) {

    Set<FlatNode> tovisit = new HashSet<FlatNode>();
    Set<FlatNode> visited = new HashSet<FlatNode>();
    tovisit.add(start);

    while (!tovisit.isEmpty()) {

      FlatNode fn = tovisit.iterator().next();
      tovisit.remove(fn);
      visited.add(fn);

      if (!loopElements.contains(fn)) {
        // check if this loop exit is derived from start node
        return true;
      }

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode next = fn.getNext(i);
        if (!visited.contains(next)) {
          if (loopInv.domtree.idom(next).equals(fn)) {
            // add next node only if current node is immediate dominator of the
            // next node
            tovisit.add(next);
          }
        }
      }

    }

    return false;

  }
}
