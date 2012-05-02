package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;


public class PointerMethod {
  public PointerMethod() {
    nextmap=new Hashtable<FlatNode, Vector<FlatNode>>();
    prevmap=new Hashtable<FlatNode, Vector<FlatNode>>();
  }

  Hashtable<FlatNode, Vector<FlatNode>> nextmap;
  Hashtable<FlatNode, Vector<FlatNode>> prevmap;

  public void analyzeMethod(FlatMethod fm) {
    if (nextmap.containsKey(fm))
      return;
    Hashtable<FlatNode, HashSet<FlatNode>> map=new Hashtable<FlatNode, HashSet<FlatNode>>();
    HashSet<FlatNode> toprocess=new HashSet<FlatNode>();
    toprocess.add(fm);
    while(!toprocess.isEmpty()) {
      FlatNode fn=toprocess.iterator().next();
      toprocess.remove(fn);
      HashSet<FlatNode> myset=new HashSet<FlatNode>();
      if (!analysisCares(fn)) {
        for(int i=0; i<fn.numPrev(); i++) {
          if (map.containsKey(fn.getPrev(i)))
            myset.addAll(map.get(fn.getPrev(i)));
        }
      } else {
        myset.add(fn);
      }
      if (!map.containsKey(fn)||!map.get(fn).equals(myset)) {
        map.put(fn, myset);
        for(int i=0; i<fn.numNext(); i++) {
          toprocess.add(fn.getNext(i));
        }
      }
    }
    for(Iterator<FlatNode> it=map.keySet().iterator(); it.hasNext(); ) {
      FlatNode fn=it.next();
      if (analysisCares(fn)) {
        HashSet<FlatNode> myset=new HashSet<FlatNode>();
        for(int i=0; i<fn.numPrev(); i++) {
          if (map.containsKey(fn.getPrev(i)))
            myset.addAll(map.get(fn.getPrev(i)));
        }
        if (!prevmap.containsKey(fn))
          prevmap.put(fn, new Vector());
        for(Iterator<FlatNode> it2=myset.iterator(); it2.hasNext(); ) {
          FlatNode fnprev=it2.next();
          if (!nextmap.containsKey(fnprev))
            nextmap.put(fnprev, new Vector());
          nextmap.get(fnprev).add(fn);
          prevmap.get(fn).add(fnprev);
        }
      }
    }
  }

  public int numNext(FlatNode fn) {
    Vector<FlatNode> vfn=nextmap.get(fn);
    if (vfn==null)
      return 0;
    else
      return vfn.size();
  }

  public FlatNode getNext(FlatNode fn, int i) {
    return nextmap.get(fn).get(i);
  }

  public int numPrev(FlatNode fn) {
    return prevmap.get(fn).size();
  }

  public FlatNode getPrev(FlatNode fn, int i) {
    return prevmap.get(fn).get(i);
  }

  public boolean isBackEdge(FlatNode fn) {
    return fn.kind() == FKind.FlatBackEdge;
  }

  public boolean analysisCares(FlatNode fn) {
    switch(fn.kind()) {
    case FKind.FlatMethod:
    case FKind.FlatFieldNode:
    case FKind.FlatSetFieldNode:
    case FKind.FlatElementNode:
    case FKind.FlatSetElementNode:
    case FKind.FlatNew:
    case FKind.FlatCall:
    case FKind.FlatReturnNode:
    case FKind.FlatBackEdge:
    case FKind.FlatSESEEnterNode:
    case FKind.FlatSESEExitNode:
    case FKind.FlatGenReachNode:
    case FKind.FlatGenDefReachNode:
    case FKind.FlatExit:
      return true;

    case FKind.FlatCastNode:
      FlatCastNode fcn=(FlatCastNode)fn;
      TypeDescriptor td=fcn.getType();
      return td.isPtr();

    case FKind.FlatOpNode:
      FlatOpNode fon = (FlatOpNode) fn;
      return fon.getOp().getOp()==Operation.ASSIGN&&fon.getLeft().getType().isPtr();

    default:
      return false;
    }
  }
}