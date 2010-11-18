package Analysis.OoOJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.Map.Entry;

import Analysis.CallGraph.CallGraph;
import Analysis.Disjoint.ReachGraph;
import IR.Descriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.TypeDescriptor;
import IR.TypeUtil;
import IR.Flat.FKind;
import IR.Flat.FlatCall;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.FlatSESEExitNode;

public class RBlockStatusAnalysis {

  // compiler data
  State state;
  TypeUtil typeUtil;
  CallGraph callGraph;
  RBlockRelationAnalysis rra;

  // per method-per node-rblock stacks
  protected Hashtable<FlatMethod, Hashtable<FlatNode, Hashtable<FlatSESEEnterNode, Boolean>>> fm2statusmap;

  public RBlockStatusAnalysis(State state, TypeUtil typeUtil, CallGraph callGraph,
      RBlockRelationAnalysis rra) {
    this.state = state;
    this.typeUtil = typeUtil;
    this.callGraph = callGraph;
    this.rra = rra;

    fm2statusmap =
        new Hashtable<FlatMethod, Hashtable<FlatNode, Hashtable<FlatSESEEnterNode, Boolean>>>();

    MethodDescriptor mdSourceEntry = typeUtil.getMain();
    FlatMethod fmMain = state.getMethodFlat(mdSourceEntry);

    // add all methods transitively reachable from the
    // source's main to set for analysis
    Set<MethodDescriptor> descriptorsToAnalyze = callGraph.getAllMethods(mdSourceEntry);

    descriptorsToAnalyze.add(mdSourceEntry);

    analyzeMethods(descriptorsToAnalyze);

     //analyzeMethodsDebug(descriptorsToAnalyze);
  }

  protected void analyzeMethods(Set<MethodDescriptor> descriptorsToAnalyze) {

    Iterator<MethodDescriptor> mdItr = descriptorsToAnalyze.iterator();
    while (mdItr.hasNext()) {
      FlatMethod fm = state.getMethodFlat(mdItr.next());

      Hashtable<FlatNode, Hashtable<FlatSESEEnterNode, Boolean>> fn2seseStatus =
          computeRBlockStatus(fm);

      fm2statusmap.put(fm, fn2seseStatus);
    }
  }

  public Hashtable<FlatNode, Hashtable<FlatSESEEnterNode, Boolean>> computeRBlockStatus(
      FlatMethod fm) {

    Hashtable<FlatNode, Stack<FlatSESEEnterNode>> seseStacks =
        new Hashtable<FlatNode, Stack<FlatSESEEnterNode>>();

    Hashtable<FlatNode, Hashtable<FlatSESEEnterNode, Boolean>> fn2seseStatus =
        new Hashtable<FlatNode, Hashtable<FlatSESEEnterNode, Boolean>>();

    LinkedList<FlatNode> flatNodesToVisit = new LinkedList<FlatNode>();
    flatNodesToVisit.add(fm);

    Stack<FlatSESEEnterNode> seseStackFirst = new Stack<FlatSESEEnterNode>();
    seseStacks.put(fm, seseStackFirst);

    while (!flatNodesToVisit.isEmpty()) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      Hashtable<FlatSESEEnterNode, Boolean> prevResult = fn2seseStatus.get(fn);

      Hashtable<FlatSESEEnterNode, Boolean> currentResult =
          new Hashtable<FlatSESEEnterNode, Boolean>();

      for (int i = 0; i < fn.numPrev(); i++) {
        FlatNode prevFlatNode = fn.getPrev(i);
        Hashtable<FlatSESEEnterNode, Boolean> incoming = fn2seseStatus.get(prevFlatNode);
        if (incoming != null) {
          merge(currentResult, incoming);
        }
      }

      flatNodesToVisit.remove(fn);

      nodeActions(fn, fm, currentResult);

      // if we have a new result, schedule forward nodes for
      // analysis
      if (prevResult == null || !currentResult.equals(prevResult)) {
        fn2seseStatus.put(fn, currentResult);
        for (int i = 0; i < fn.numNext(); i++) {
          FlatNode nn = fn.getNext(i);
          flatNodesToVisit.addFirst(nn);
        }
      }
    }

    return fn2seseStatus;
  }

  private void merge(Hashtable<FlatSESEEnterNode, Boolean> current,
      Hashtable<FlatSESEEnterNode, Boolean> incoming) {

    Iterator inIter = incoming.entrySet().iterator();
    while (inIter.hasNext()) {
      Entry inEntry = (Entry) inIter.next();
      FlatSESEEnterNode seseContaining = (FlatSESEEnterNode) inEntry.getKey();
      Boolean isAfter = (Boolean) inEntry.getValue();

      Boolean currentIsAfter = current.get(seseContaining);
      if (currentIsAfter == null || currentIsAfter == Boolean.FALSE) {
        current.put(seseContaining, isAfter);
      }
    }

  }
  
  public boolean isInCriticalRegion(FlatMethod fmContaining, FlatNode fn) {
    FlatSESEEnterNode seseContaining = rra.getRBlockStacks(fmContaining, fn).peek();
    Hashtable<FlatNode, Hashtable<FlatSESEEnterNode, Boolean>> statusMap =
        fm2statusmap.get(fmContaining);
    Hashtable<FlatSESEEnterNode, Boolean> status = statusMap.get(fn);

    if(status.get(seseContaining).booleanValue()==true){
//      System.out.println(fn+" is in the critical region in according to "+seseContaining);
    }
    
    return status.get(seseContaining).booleanValue();
  }

  protected void nodeActions(FlatNode fn, FlatMethod fm,
      Hashtable<FlatSESEEnterNode, Boolean> status) {
    switch (fn.kind()) {

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
      FlatSESEEnterNode fsen = fsexn.getFlatEnter();
      if (fsen.getParent() != null) {
        status.put(fsen.getParent(), Boolean.TRUE);
      }
    }
      break;
      
    case FKind.FlatCall: {
      Descriptor mdCaller = fm.getMethod();

      FlatCall         fc       = (FlatCall) fn;
      MethodDescriptor mdCallee = fc.getMethod();
      FlatMethod       fmCallee = state.getMethodFlat( mdCallee );

         Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();

      if (mdCallee.isStatic()) {
        setPossibleCallees.add(mdCallee);
      } else {
        TypeDescriptor typeDesc = fc.getThis().getType();
        setPossibleCallees.addAll(callGraph.getMethods(mdCallee, typeDesc));
      }
      
      Iterator<MethodDescriptor> mdItr = setPossibleCallees.iterator();
      while( mdItr.hasNext() ) {
        MethodDescriptor mdPossible = mdItr.next();
        FlatMethod       fmPossible = state.getMethodFlat( mdPossible );
      }
      
      boolean hasSESECallee=false;
      for (Iterator iterator = setPossibleCallees.iterator(); iterator.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) iterator.next();
        FlatMethod flatMethod = state.getMethodFlat(md);
        FlatNode flatNode = flatMethod.getNext(0);
        assert flatNode instanceof FlatSESEEnterNode;
        FlatSESEEnterNode flatSESE = (FlatSESEEnterNode) flatNode;
        hasSESECallee |= (!flatSESE.getIsLeafSESE());
      }

      Stack<FlatSESEEnterNode> seseStack = rra.getRBlockStacks(fm, fn);
      if (!seseStack.isEmpty()) {
        FlatSESEEnterNode currentParent = seseStack.peek();
        if(!status.containsKey(currentParent)){
//          System.out.println("currentParent="+currentParent+" fm="+currentParent.getfmEnclosing()+" hasSESECallee="+hasSESECallee);
          status.put(currentParent, new Boolean(hasSESECallee));
        }else{
          boolean currentParentStatus=status.get(currentParent).booleanValue();
//          System.out.println("currentParent="+currentParent+" fm="+currentParent.getfmEnclosing()+" hasSESECallee="+hasSESECallee+" currentParentStatus="+currentParentStatus);
          status.put(currentParent, new Boolean(hasSESECallee|currentParentStatus));
        }
      }
      
    } break;

    default: {
      if (!(fn instanceof FlatMethod)) {
        Stack<FlatSESEEnterNode> seseStack = rra.getRBlockStacks(fm, fn);
        if (!seseStack.isEmpty()) {
          FlatSESEEnterNode currentParent = seseStack.peek();
          if (!status.containsKey(currentParent)) {
            status.put(currentParent, Boolean.FALSE);
          }
        }
      }

    }
      break;
    }

  }

  /*
   * DEBUG
   */
  protected void analyzeMethodsDebug(Set<MethodDescriptor> descriptorsToAnalyze) {

    Iterator<MethodDescriptor> mdItr = descriptorsToAnalyze.iterator();
    while (mdItr.hasNext()) {
      FlatMethod fm = state.getMethodFlat(mdItr.next());
      printStatusMap(fm);

    }
  }

  protected void printStatusMap(FlatMethod fm) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    Set<FlatNode> visited = new HashSet<FlatNode>();

    while (!flatNodesToVisit.isEmpty()) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      flatNodesToVisit.remove(fn);
      visited.add(fn);

      System.out.println("------------------");
      System.out.println("fn=" + fn);
      System.out.println(fm2statusmap.get(fm).get(fn));

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);

        if (!visited.contains(nn)) {
          flatNodesToVisit.add(nn);

        }
      }
    }

  }

}
