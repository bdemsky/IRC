package Analysis.Loops;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import IR.Operation;
import IR.Flat.FKind;
import IR.Flat.FlatCondBranch;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.TempDescriptor;

public class LoopTerminate {

  private FlatMethod fm;
  private LoopInvariant loopInv;
  private Set<TempDescriptor> inductionSet;
  // mapping from Induction Variable TempDescriptor to Flat Node that defines
  // it
  private HashMap<TempDescriptor, FlatNode> inductionVar2DefNode;

  // mapping from Derived Induction Variable TempDescriptor to its root
  // induction variable TempDescriptor
  private HashMap<TempDescriptor, TempDescriptor> derivedVar2basicInduction;

  Set<FlatNode> computed;

  /**
   * Constructor for Loop Termination Analysis
   * 
   * @param fm
   *          FlatMethod for termination analysis
   * @param loopInv
   *          LoopInvariants for given method
   */
  public LoopTerminate(FlatMethod fm, LoopInvariant loopInv) {
    this.fm = fm;
    this.loopInv = loopInv;
    this.inductionSet = new HashSet<TempDescriptor>();
    this.inductionVar2DefNode = new HashMap<TempDescriptor, FlatNode>();
    this.derivedVar2basicInduction = new HashMap<TempDescriptor, TempDescriptor>();
    this.computed = new HashSet<FlatNode>();
  }

  /**
   * starts loop termination analysis
   */
  public void terminateAnalysis() {
    Loops loopFinder = loopInv.root;
    recurse(fm, loopFinder);
  }

  /**
   * iterate over the current level of loops and spawn analysis for its child
   * 
   * @param fm
   *          FlatMethod for loop analysis
   * @param parent
   *          the current level of loop
   */
  private void recurse(FlatMethod fm, Loops parent) {
    for (Iterator lpit = parent.nestedLoops().iterator(); lpit.hasNext();) {
      Loops child = (Loops) lpit.next();
      processLoop(fm, child);
      recurse(fm, child);
    }
  }

  /**
   * initialize internal data structure
   */
  private void init() {
    inductionSet.clear();
    inductionVar2DefNode.clear();
    derivedVar2basicInduction.clear();
  }

  /**
   * analysis loop for termination property
   * 
   * @param fm
   *          FlatMethod that contains loop l
   * @param l
   *          analysis target loop l
   */
  private void processLoop(FlatMethod fm, Loops l) {

    Set loopElements = l.loopIncElements(); // loop body elements
    Set loopEntrances = l.loopEntrances(); // loop entrance
    assert loopEntrances.size() == 1;
    FlatNode loopEntrance = (FlatNode) loopEntrances.iterator().next();

    init();
    detectBasicInductionVar(loopElements);
    detectDerivedInductionVar(loopElements);
    checkConditionBranch(loopEntrance, loopElements);

  }

  /**
   * check if condition branch node satisfies loop condition
   * 
   * @param loopEntrance
   *          loop entrance flat node
   * @param loopElements
   *          elements of current loop and all nested loop
   */
  private void checkConditionBranch(FlatNode loopEntrance, Set loopElements) {
    // In the loop, every guard condition of the loop must be composed by
    // induction & invariants
    // every guard condition of the if-statement that leads it to the exit must
    // be composed by induction&invariants

    Set<FlatNode> tovisit = new HashSet<FlatNode>();
    Set<FlatNode> visited = new HashSet<FlatNode>();
    tovisit.add(loopEntrance);

    int numMustTerminateGuardCondtion = 0;
    int numLoop = 0;
    while (!tovisit.isEmpty()) {
      FlatNode fnvisit = tovisit.iterator().next();
      tovisit.remove(fnvisit);
      visited.add(fnvisit);

      if (fnvisit.kind() == FKind.FlatCondBranch) {
        FlatCondBranch fcb = (FlatCondBranch) fnvisit;

        if (fcb.isLoopBranch()) {
          numLoop++;
        }

        if (fcb.isLoopBranch() || hasLoopExitNode(fcb, true, loopEntrance, loopElements)) {
          // current FlatCondBranch can introduce loop exits
          // in this case, guard condition of it should be composed only by loop
          // invariants and induction variables
          Set<FlatNode> condSet = getDefinitionInLoop(fnvisit, fcb.getTest(), loopElements);
          assert condSet.size() == 1;
          FlatNode condFn = condSet.iterator().next();
          // assume that guard condition node is always a conditional inequality
          if (condFn instanceof FlatOpNode) {
            FlatOpNode condOp = (FlatOpNode) condFn;
            // check if guard condition is composed only with induction
            // variables
            if (checkConditionNode(condOp, fcb.isLoopBranch(), loopElements)) {
              numMustTerminateGuardCondtion++;
            } else {
              if (!fcb.isLoopBranch()) {
                // if it is if-condition and it leads us to loop exit,
                // corresponding guard condition should be composed by induction
                // and invariants
                throw new Error("Loop may never terminate at "
                    + fm.getMethod().getClassDesc().getSourceFileName() + "::"
                    + loopEntrance.numLine);
              }
            }
          }
        }
      }

      for (int i = 0; i < fnvisit.numNext(); i++) {
        FlatNode next = fnvisit.getNext(i);
        if (loopElements.contains(next) && !visited.contains(next)) {
          tovisit.add(next);
        }
      }

    }

    // # of must-terminate loop condition must be equal to or larger than # of
    // loop
    if (numMustTerminateGuardCondtion < numLoop) {
      throw new Error("Loop may never terminate at "
          + fm.getMethod().getClassDesc().getSourceFileName() + "::" + loopEntrance.numLine);
    }

  }

  /**
   * detect derived induction variable
   * 
   * @param loopElements
   *          elements of current loop and all nested loop
   */
  private void detectDerivedInductionVar(Set loopElements) {
    // 2) detect derived induction variables
    // variable k is a derived induction variable if
    // 1) there is only one definition of k within the loop, of the form k=j*c
    // or k=j+d where j is induction variable, c, d are loop-invariant
    // 2) and if j is a derived induction variable in the family of i, then:
    // (a) the only definition of j that reaches k is the one in the loop
    // (b) and there is no definition of i on any path between the definition of
    // j and the definition of k

    boolean changed = true;
    Set<TempDescriptor> basicInductionSet = new HashSet<TempDescriptor>();
    basicInductionSet.addAll(inductionSet);

    while (changed) {
      changed = false;
      nextfn: for (Iterator elit = loopElements.iterator(); elit.hasNext();) {
        FlatNode fn = (FlatNode) elit.next();
        if (!computed.contains(fn)) {
          if (fn.kind() == FKind.FlatOpNode) {
            FlatOpNode fon = (FlatOpNode) fn;
            int op = fon.getOp().getOp();
            if (op == Operation.ADD || op == Operation.MULT) {
              TempDescriptor tdLeft = fon.getLeft();
              TempDescriptor tdRight = fon.getRight();
              TempDescriptor tdDest = fon.getDest();

              boolean isLeftLoopInvariant = isLoopInvariantVar(fn, tdLeft, loopElements);
              boolean isRightLoopInvariant = isLoopInvariantVar(fn, tdRight, loopElements);

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
                    // in this case, induction variable 'j' is derived from
                    // basic induction var

                    // check if only definition of j that reaches k is the one
                    // in the loop

                    Set<FlatNode> defSet = getDefinitionInLoop(fn, inductionOp, loopElements);
                    if (defSet.size() == 1) {
                      // check if there is no def of i on any path bet' def of j
                      // and def of k

                      TempDescriptor originInduc = derivedVar2basicInduction.get(inductionOp);
                      FlatNode defI = inductionVar2DefNode.get(originInduc);
                      FlatNode defJ = inductionVar2DefNode.get(inductionOp);
                      FlatNode defk = fn;

                      if (!checkPath(defI, defJ, defk)) {
                        continue nextfn;
                      }

                    }
                  }
                  // add new induction var

                  // when tdDest has the form of srctmp(tdDest) = inductionOp +
                  // loopInvariant
                  // want to have the definition of srctmp
                  Set<FlatNode> setUseNode = loopInv.usedef.useMap(fn, tdDest);
                  assert setUseNode.size() == 1;
                  assert setUseNode.iterator().next().writesTemps().length == 1;

                  FlatNode srcDefNode = setUseNode.iterator().next();
                  if (srcDefNode instanceof FlatOpNode) {
                    if (((FlatOpNode) srcDefNode).getOp().getOp() == Operation.ASSIGN) {
                      TempDescriptor derivedIndVar = setUseNode.iterator().next().writesTemps()[0];
                      FlatNode defNode = setUseNode.iterator().next();

                      computed.add(fn);
                      computed.add(defNode);
                      inductionSet.add(derivedIndVar);
                      inductionVar2DefNode.put(derivedIndVar, defNode);
                      derivedVar2basicInduction.put(derivedIndVar, inductionOp);
                      changed = true;
                    }
                  }

                }

              }

            }

          }
        }

      }
    }

  }

  /**
   * detect basic induction variable
   * 
   * @param loopElements
   *          elements of current loop and all nested loop
   */
  private void detectBasicInductionVar(Set loopElements) {
    // 1) find out basic induction variable
    // variable i is a basic induction variable in loop if the only definitions
    // of i within L are of the form i=i+c where c is loop invariant
    for (Iterator elit = loopElements.iterator(); elit.hasNext();) {
      FlatNode fn = (FlatNode) elit.next();
      if (fn.kind() == FKind.FlatOpNode) {
        FlatOpNode fon = (FlatOpNode) fn;
        int op = fon.getOp().getOp();
        if (op == Operation.ADD) {
          TempDescriptor tdLeft = fon.getLeft();
          TempDescriptor tdRight = fon.getRight();

          boolean isLeftLoopInvariant = isLoopInvariantVar(fn, tdLeft, loopElements);
          boolean isRightLoopInvariant = isLoopInvariantVar(fn, tdRight, loopElements);

          if (isLeftLoopInvariant ^ isRightLoopInvariant) {

            TempDescriptor candidateTemp;

            if (isLeftLoopInvariant) {
              candidateTemp = tdRight;
            } else {
              candidateTemp = tdLeft;
            }

            Set<FlatNode> defSetOfLoop = getDefinitionInLoop(fn, candidateTemp, loopElements);
            // basic induction variable must have only one definition within the
            // loop
            if (defSetOfLoop.size() == 1) {
              // find out definition of induction var, form of Flat
              // Assign:inductionVar = candidateTemp
              FlatNode indNode = defSetOfLoop.iterator().next();
              assert indNode.readsTemps().length == 1;
              TempDescriptor readTemp = indNode.readsTemps()[0];
              if (readTemp.equals(fon.getDest())) {
                inductionVar2DefNode.put(candidateTemp, defSetOfLoop.iterator().next());
                inductionVar2DefNode.put(readTemp, defSetOfLoop.iterator().next());
                inductionSet.add(fon.getDest());
                inductionSet.add(candidateTemp);
                computed.add(fn);
              }

            }

          }

        }
      }
    }

  }

  /**
   * check whether there is no definition node 'def' on any path between 'start'
   * node and 'end' node
   * 
   * @param def
   * @param start
   * @param end
   * @return true if there is no def in-bet start and end
   */
  private boolean checkPath(FlatNode def, FlatNode start, FlatNode end) {
    Set<FlatNode> endSet = new HashSet<FlatNode>();
    endSet.add(end);
    return !(start.getReachableSet(endSet)).contains(def);
  }

  /**
   * check condition node satisfies termination condition
   * 
   * @param fon
   *          condition node FlatOpNode
   * @param isLoopCondition
   *          true if condition is loop condition
   * @param loopElements
   *          elements of current loop and all nested loop
   * @return true if it satisfies termination condition
   */
  private boolean checkConditionNode(FlatOpNode fon, boolean isLoopCondition, Set loopElements) {
    // check flatOpNode that computes loop guard condition
    // currently we assume that induction variable is always getting bigger
    // and guard variable is constant
    // so need to check (1) one of operand should be induction variable
    // (2) another operand should be constant or loop invariant

    TempDescriptor induction = null;
    TempDescriptor guard = null;

    int op = fon.getOp().getOp();
    if (op == Operation.LT || op == Operation.LTE) {
      if (isLoopCondition) {
        // loop condition is inductionVar <= loop invariant
        induction = fon.getLeft();
        guard = fon.getRight();
      } else {
        // if-statement condition is loop invariant <= inductionVar since
        // inductionVar is getting biggier each iteration
        induction = fon.getRight();
        guard = fon.getLeft();
      }
    } else if (op == Operation.GT || op == Operation.GTE) {
      if (isLoopCondition) {
        // condition is loop invariant >= inductionVar
        induction = fon.getRight();
        guard = fon.getLeft();
      } else {
        // if-statement condition is loop inductionVar >= invariant
        induction = fon.getLeft();
        guard = fon.getRight();
      }
    } else {
      return false;
    }

    // here, verify that guard operand is an induction variable
    if (!hasInductionVar(fon, induction, loopElements)) {
      return false;
    }

    if (guard != null) {
      Set guardDefSet = getDefinitionInLoop(fon, guard, loopElements);
      for (Iterator iterator = guardDefSet.iterator(); iterator.hasNext();) {
        FlatNode guardDef = (FlatNode) iterator.next();
        if (!(guardDef.kind() == FKind.FlatLiteralNode) && !loopInv.hoisted.contains(guardDef)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * check if TempDescriptor td has at least one induction variable and is
   * composed only by induction vars +loop invariants
   * 
   * @param fn
   *          FlatNode that contains TempDescriptor 'td'
   * @param td
   *          TempDescriptor representing target variable
   * @param loopElements
   *          elements of current loop and all nested loop
   * @return true if 'td' is induction variable
   */
  private boolean hasInductionVar(FlatNode fn, TempDescriptor td, Set loopElements) {

    if (inductionSet.contains(td)) {
      return true;
    } else {
      // check if td is composed by induction variables or loop invariants
      Set<FlatNode> defSet = getDefinitionInLoop(fn, td, loopElements);
      for (Iterator iterator = defSet.iterator(); iterator.hasNext();) {
        FlatNode defNode = (FlatNode) iterator.next();

        int inductionVarCount = 0;
        TempDescriptor[] readTemps = defNode.readsTemps();
        for (int i = 0; i < readTemps.length; i++) {
          if (!hasInductionVar(defNode, readTemps[i], loopElements)) {
            if (!isLoopInvariantVar(defNode, readTemps[i], loopElements)) {
              return false;
            }
          } else {
            inductionVarCount++;
          }
        }

        // check definition of td has at least one induction var
        if (inductionVarCount > 0) {
          return true;
        }

      }

      return false;
    }

  }

  /**
   * check if TempDescriptor td is loop invariant variable or constant value wrt
   * the current loop
   * 
   * @param fn
   *          FlatNode that contains TempDescriptor 'td'
   * @param td
   *          TempDescriptor representing target variable
   * @param loopElements
   *          elements of current loop and all nested loop
   * @return true if 'td' is loop invariant variable
   */
  private boolean isLoopInvariantVar(FlatNode fn, TempDescriptor td, Set loopElements) {

    Set<FlatNode> defset = loopInv.usedef.defMap(fn, td);

    Set<FlatNode> defSetOfLoop = new HashSet<FlatNode>();
    for (Iterator<FlatNode> defit = defset.iterator(); defit.hasNext();) {
      FlatNode def = defit.next();
      if (loopElements.contains(def)) {
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
      if (defFlatNode.kind() == FKind.FlatLiteralNode || loopInv.hoisted.contains(defFlatNode)) {
        return true;
      }
    }

    return false;

  }

  /**
   * compute the set of definitions of variable 'td' inside of the loop
   * 
   * @param fn
   *          FlatNode that uses 'td'
   * @param td
   *          target node that we want to have the set of definitions
   * @param loopElements
   *          elements of current loop and all nested loop
   * @return the set of definition nodes for 'td' in the current loop
   */
  private Set<FlatNode> getDefinitionInLoop(FlatNode fn, TempDescriptor td, Set loopElements) {

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

  /**
   * check whether FlatCondBranch introduces loop exit
   * 
   * @param fcb
   *          target node
   * @param fromTrueBlock
   *          specify which block is possible to have loop exit
   * @param loopHeader
   *          loop header of current loop
   * @param loopElements
   *          elements of current loop and all nested loop
   * @return true if input 'fcb' intrroduces loop exit
   */
  private boolean hasLoopExitNode(FlatCondBranch fcb, boolean fromTrueBlock, FlatNode loopHeader,
      Set loopElements) {
    // return true if fcb possibly introduces loop exit

    FlatNode next;
    if (fromTrueBlock) {
      next = fcb.getNext(0);
    } else {
      next = fcb.getNext(1);
    }

    return hasLoopExitNode(loopHeader, next, loopElements);

  }

  /**
   * check whether start node reaches loop exit
   * 
   * @param loopHeader
   * @param start
   * @param loopElements
   * @return true if a path exist from start to loop exit
   */
  private boolean hasLoopExitNode(FlatNode loopHeader, FlatNode start, Set loopElements) {

    Set<FlatNode> tovisit = new HashSet<FlatNode>();
    Set<FlatNode> visited = new HashSet<FlatNode>();
    tovisit.add(start);

    while (!tovisit.isEmpty()) {

      FlatNode fn = tovisit.iterator().next();
      tovisit.remove(fn);
      visited.add(fn);

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode next = fn.getNext(i);
        if (!visited.contains(next)) {
          // check that if-body statment introduces loop exit.
          if (!loopElements.contains(next)) {
            return true;
          }

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
