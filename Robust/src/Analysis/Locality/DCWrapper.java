package Analysis.Locality;
import Analysis.Liveness;
import Analysis.ReachingDefs;
import Analysis.Loops.DomTree;
import IR.State;
import IR.MethodDescriptor;
import IR.TypeDescriptor;
import IR.FieldDescriptor;
import IR.Flat.*;
import Analysis.Loops.GlobalFieldType;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.Stack;
import java.util.Iterator;

public class DCWrapper {
  DelayComputation delaycomp;
  State state;
  LocalityAnalysis locality;
  TypeAnalysis typeanalysis;
  GlobalFieldType gft;

  public DCWrapper(LocalityAnalysis locality, State state, TypeAnalysis typeanalysis, GlobalFieldType gft) {
    delaycomp=new DelayComputation(locality, state, typeanalysis, gft);
    delaycomp.doAnalysis();
    this.state=state;
    this.locality=locality;
    this.typeanalysis=typeanalysis;
    this.gft=gft;
    Set<LocalityBinding> localityset=locality.getLocalityBindings();
    for(Iterator<LocalityBinding> lbit=localityset.iterator(); lbit.hasNext(); ) {
      processlb(lbit.next());
    }
  }

  Hashtable<LocalityBinding, Set<FlatNode>> transmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
  Hashtable<LocalityBinding, Set<FlatNode>> recordmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
  Hashtable<LocalityBinding, Set<FlatNode>> othermap=new Hashtable<LocalityBinding, Set<FlatNode>>();
  Hashtable<LocalityBinding, Set<FlatNode>> notreadymap=new Hashtable<LocalityBinding, Set<FlatNode>>();
  Hashtable<LocalityBinding, HashSet<FlatNode>> cannotdelaymap=new Hashtable<LocalityBinding, HashSet<FlatNode>>();
  Hashtable<LocalityBinding, Set<FlatNode>> derefmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
  Hashtable<LocalityBinding, Set<FlatNode>> convmap=new Hashtable<LocalityBinding, Set<FlatNode>>();

  public DiscoverConflicts getConflicts() {
    DiscoverConflicts dc=new DiscoverConflicts(locality, state, typeanalysis, cannotdelaymap, false, false, state.READSET?gft:null);
    dc.doAnalysis();
    return dc;
  }

  public Hashtable<LocalityBinding, HashSet<FlatNode>> getCannotDelayMap() {
    return cannotdelaymap;
  }

  public boolean needsFission(LocalityBinding lb, FlatAtomicEnterNode faen) {
    return transmap.get(lb).contains(faen);
  }

  public Set<TempDescriptor> liveinto(LocalityBinding lb, FlatAtomicEnterNode faen, Set<FlatNode> recordset) {
    return delaycomp.liveinto(lb, faen, recordset);
  }

  public Set<TempDescriptor> alltemps(LocalityBinding lb, FlatAtomicEnterNode faen, Set<FlatNode> recordset) {
    return delaycomp.alltemps(lb, faen, recordset);
  }

  public Set<TempDescriptor> liveout(LocalityBinding lb, FlatAtomicEnterNode faen) {
    return delaycomp.liveout(lb, faen);
  }

  public Set<TempDescriptor> liveoutvirtualread(LocalityBinding lb, FlatAtomicEnterNode faen) {
    return delaycomp.liveoutvirtualread(lb, faen);
  }

  private static HashSet<FlatNode> intersect(Set<FlatNode> a, Set<FlatNode> b) {
    HashSet<FlatNode> intersect=new HashSet(b);
    intersect.retainAll(a);
    return intersect;
  }

  public Set<FlatNode> getDeref(LocalityBinding lb) {
    return derefmap.get(lb);
  }

  public Set<FlatNode> getNotReady(LocalityBinding lb) {
    return notreadymap.get(lb);
  }

  public Set<FlatNode> getCannotDelay(LocalityBinding lb) {
    return cannotdelaymap.get(lb);
  }

  public Set<FlatNode> getOther(LocalityBinding lb) {
    return othermap.get(lb);
  }

  public Set<FlatNode> getConv(LocalityBinding lb) {
    return convmap.get(lb);
  }

  public Set<FlatNode> livecode(LocalityBinding lb) {
    return recordmap.get(lb);
  }

  private void processlb(LocalityBinding lb) {
    transmap.put(lb, new HashSet<FlatNode>());
    Set<FlatNode> convset=new HashSet<FlatNode>();
    convmap.put(lb, convset);
    if (lb.isAtomic()||!lb.getHasAtomic())
      return;

    Set<FlatNode> recordset=delaycomp.livecode(lb);
    Set<FlatNode> cannotdelay=delaycomp.getCannotDelay(lb);
    Set<FlatNode> otherset=delaycomp.getOther(lb);
    Set<FlatNode> notreadyset=delaycomp.getNotReady(lb);
    Set<FlatNode> derefset=(state.STMARRAY&&!state.DUALVIEW)?delaycomp.getDeref(lb):null;
    Set<FlatNode> checkset=new HashSet<FlatNode>();
    checkset.addAll(cannotdelay);
    checkset.addAll(otherset);

    Set<FlatNode> nrecordset=new HashSet<FlatNode>();
    HashSet<FlatNode> ncannotdelay=new HashSet<FlatNode>();
    Set<FlatNode> notherset=new HashSet<FlatNode>();
    Set<FlatNode> nnotready=new HashSet<FlatNode>();
    Set<FlatNode> nderef=new HashSet<FlatNode>();


    recordmap.put(lb, nrecordset);
    cannotdelaymap.put(lb, ncannotdelay);
    notreadymap.put(lb, nnotready);
    othermap.put(lb, notherset);
    derefmap.put(lb, nderef);


    FlatMethod fm=state.getMethodFlat(lb.getMethod());
    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator(); fnit.hasNext(); ) {
      FlatNode fn=fnit.next();
      if (fn.kind()==FKind.FlatAtomicEnterNode&&
          locality.getAtomic(lb).get(fn.getPrev(0)).intValue()==0) {
	Set<FlatNode> transSet=computeTrans(lb, fn);
	Set<FlatNode> tCheckSet=intersect(checkset, transSet);
	Set<FlatNode> tRecordSet=intersect(recordset, transSet);
	Set<FlatNode> tOtherSet=intersect(otherset, transSet);
	Set<FlatNode> tNotReadySet=intersect(notreadyset, transSet);
	HashSet<FlatNode> tCannotDelay=intersect(cannotdelay, transSet);
	Set<FlatNode> tderef=(state.STMARRAY&&!state.DUALVIEW)?intersect(derefset, transSet):null;

	if (checkSet(fn, tCheckSet, tRecordSet, lb)) {
	  //We will convert this one
	  nrecordset.addAll(tRecordSet);
	  notherset.addAll(tOtherSet);
	  nnotready.addAll(tNotReadySet);
	  ncannotdelay.addAll(tCannotDelay);
	  if (state.STMARRAY&&!state.DUALVIEW)
	    nderef.addAll(tderef);
	  transmap.get(lb).add(fn);
	  convset.addAll(transSet);
	} else {
	  ncannotdelay.addAll(transSet);
	}
	if (!lwmap.containsKey(lb))
	  lwmap.put(lb, new HashSet<FlatNode>());
	lwmap.get(lb).add(fn);
      } else {
	if (locality.getAtomic(lb).get(fn).intValue()==0)
	  ncannotdelay.add(fn);
      }
    }
  }

  Hashtable<LocalityBinding, Set<FlatNode>> lwmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
  Hashtable<LocalityBinding, Set<FlatNode>> optmap=new Hashtable<LocalityBinding, Set<FlatNode>>();

  public boolean lightweightTrans(LocalityBinding lb, FlatNode fn) {
    return lwmap.get(lb).contains(fn);
  }

  public boolean optimizeTrans(LocalityBinding lb, FlatNode fn) {
    return optmap.get(lb).contains(fn);
  }

  private boolean checkSet(FlatNode faen, Set<FlatNode> checkset, Set<FlatNode> recordset, LocalityBinding lb) {
    if (!optmap.containsKey(lb)) {
      optmap.put(lb, new HashSet<FlatNode>());
    }

    if (state.HYBRID&&recordset.size()>6)
      return false;

    DiscoverConflicts dc=delaycomp.getConflicts();
    for(Iterator<FlatNode> fnit=checkset.iterator(); fnit.hasNext(); ) {
      FlatNode fn=fnit.next();
      //needs transread
      if (!state.READSET&&dc.getNeedTrans(lb, fn)||state.READSET&&dc.getNeedWriteTrans(lb, fn)||fn.kind()==FKind.FlatCall) {
	System.out.println("False because"+fn);
	if (!state.HYBRID)
	  return true;
	return false;
      }
    }
    optmap.get(lb).add(faen);
    return true;
  }

  private Set<FlatNode> computeTrans(LocalityBinding lb, FlatNode faen) {
    HashSet<FlatNode> transSet=new HashSet<FlatNode>();
    HashSet<FlatNode> toProcess=new HashSet<FlatNode>();
    toProcess.add(faen);
    while(!toProcess.isEmpty()) {
      FlatNode fn=toProcess.iterator().next();
      toProcess.remove(fn);
      transSet.add(fn);
      if (locality.getAtomic(lb).get(fn).intValue()==0)
	continue;
      for(int i=0; i<fn.numNext(); i++) {
	if (!transSet.contains(fn.getNext(i)))
	  toProcess.add(fn.getNext(i));
      }
    }
    return transSet;
  }
}