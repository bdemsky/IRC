package Analysis.Loops;
import IR.Flat.*;
import Analysis.Locality.*;
import IR.Operation;
import IR.State;
import IR.MethodDescriptor;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;

public class WriteBarrier {
  /* This computes whether we actually need a write barrier. */
  LocalityAnalysis la;
  State state;
  boolean turnoff;

  public WriteBarrier(LocalityAnalysis la, State state) {
    this.la=la;
    this.state=state;
    turnoff=false;
  }

  public void turnoff() {
    turnoff=true;
  }

  public void turnon() {
    turnoff=false;
  }
  
  public boolean needBarrier(FlatNode fn) {
    if (turnoff)
      return false;
    HashSet<TempDescriptor> nb=computeIntersection(fn);
    switch(fn.kind()) {
    case FKind.FlatSetElementNode:
      {
	FlatSetElementNode fsen=(FlatSetElementNode)fn;
	return !nb.contains(fsen.getDst());
      }
    case FKind.FlatElementNode:
      {
	FlatElementNode fen=(FlatElementNode)fn;
	return !nb.contains(fen.getSrc());
      }
    case FKind.FlatSetFieldNode:
      {
	FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	return !nb.contains(fsfn.getDst());
      }
    default:
      return true;
    }
  }
  
  Hashtable<FlatNode,HashSet<TempDescriptor>> needbarrier;

  public void analyze(LocalityBinding lb) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    HashSet useful=new HashSet();
    HashSet toprocess=new HashSet();
    HashSet discovered=new HashSet();
    needbarrier=new Hashtable<FlatNode,HashSet<TempDescriptor>>();
    toprocess.add(fm.getNext(0));
    discovered.add(fm.getNext(0));
    Hashtable<FlatNode, Integer> atomic=la.getAtomic(lb);
    
    while(!toprocess.isEmpty()) {
      FlatNode fn=(FlatNode)toprocess.iterator().next();
      toprocess.remove(fn);
      for(int i=0;i<fn.numNext();i++) {
        FlatNode nnext=fn.getNext(i);
        if (!discovered.contains(nnext)) {
          toprocess.add(nnext);
          discovered.add(nnext);
        }
      }
      HashSet<TempDescriptor> nb=computeIntersection(fn);
      TempDescriptor[] writes=fn.writesTemps();
      for(int i=0;i<writes.length;i++) {
	nb.remove(writes[i]);
      }
      switch(fn.kind()) {
      case FKind.FlatSetElementNode:
	{
	  FlatSetElementNode fsen=(FlatSetElementNode)fn;
	  if (!state.STMARRAY)
	    nb.add(fsen.getDst());
	  break;
	}
      case FKind.FlatSetFieldNode:
	{
	  FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	  nb.add(fsfn.getDst());
	  break;
	}
      case FKind.FlatOpNode: 
	{
	  FlatOpNode fon=(FlatOpNode)fn;
	  if (fon.getOp().getOp()==Operation.ASSIGN) {
	    if (nb.contains(fon.getLeft())) {
	      nb.add(fon.getDest());
	    }
	  }
	  break;
	}
      case FKind.FlatNew:
	{
	  FlatNew fnew=(FlatNew)fn;
	  nb.add(fnew.getDst());
	  break;
	}
      default:
	//If we enter a transaction toss everything
	if (atomic.get(fn).intValue()>0&&
	    atomic.get(fn.getPrev(0)).intValue()==0) {
	  nb=new HashSet<TempDescriptor>();
	}
      }
      if (!needbarrier.containsKey(fn)||
	  !needbarrier.get(fn).equals(nb)) {
	for(int i=0;i<fn.numNext();i++) {
	  FlatNode nnext=fn.getNext(i);
	  toprocess.add(nnext);
	}
	needbarrier.put(fn,nb);
      }
    }
  }
  HashSet<TempDescriptor> computeIntersection(FlatNode fn) {
    HashSet<TempDescriptor> tab=new HashSet<TempDescriptor>();
    boolean first=true;
    for(int i=0;i<fn.numPrev();i++) {
      FlatNode fprev=fn.getPrev(i);
      HashSet<TempDescriptor> hs=needbarrier.get(fprev);
      if (hs!=null) {
	if (first) {
	  tab.addAll(hs);
	  first=false;
	} else {
	  //Intersect sets
	  for(Iterator<TempDescriptor> it=tab.iterator();it.hasNext();) {
	    TempDescriptor t=it.next();
	    if (!hs.contains(t))
	      it.remove();
	  }
	}
      }
    }
    return tab;
  }
}