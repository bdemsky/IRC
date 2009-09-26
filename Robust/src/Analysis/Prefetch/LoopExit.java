package Analysis.Prefetch;

import java.util.*;
import IR.SymbolTable;
import IR.State;
import IR.TypeUtil;
import IR.MethodDescriptor;
import IR.Flat.*;
import IR.*;
import IR.ClassDescriptor;


public class LoopExit {
  State state;
  Hashtable<MethodDescriptor, Set<FlatCondBranch>> results;

  public LoopExit(State state) {
    this.state=state;
    this.results=new Hashtable<MethodDescriptor, Set<FlatCondBranch>>();
  }

  public Set<FlatCondBranch> getLoopBranches(MethodDescriptor md) {
    if (!results.containsKey(md))
      doAnalysis(md);
    return results.get(md);
  }

  public boolean isLoopingBranch(MethodDescriptor md, FlatCondBranch fcb) {
    return getLoopBranches(md).contains(fcb);
  }

  private void doAnalysis(MethodDescriptor md) {
    FlatMethod fm=state.getMethodFlat(md);
    HashSet<FlatNode> nodeset=new HashSet<FlatNode>();
    nodeset.addAll(fm.getNodeSet());
    Hashtable<FlatNode, Set<FlatCondBranch>> table=new Hashtable<FlatNode, Set<FlatCondBranch>>();

    HashSet<FlatCondBranch> loopbranchset=new HashSet<FlatCondBranch>();
    HashSet<FlatCondBranch> exitset=new HashSet<FlatCondBranch>();

    while(!nodeset.isEmpty()) {
      FlatNode fn=nodeset.iterator().next();
      nodeset.remove(fn);
      if (fn.kind()==FKind.FlatCondBranch&&((FlatCondBranch)fn).isLoopBranch()) {
	FlatCondBranch fcb=(FlatCondBranch)fn;
	loopbranchset.add(fcb);
	//True edge
	propagateset(nodeset, table, fcb, fcb.getNext(0), fcb);
	//False edge
	propagateset(nodeset, table, fcb, fcb.getNext(1), null);
	loopbranchset.add(fcb);
      } else if (fn.kind()==FKind.FlatReturnNode) {
	if (table.containsKey(fn))
	  exitset.addAll(table.get(fn));
      } else {
	for(int i=0; i<fn.numNext(); i++)
	  propagateset(nodeset, table, fn, fn.getNext(i), null);
      }
    }
    loopbranchset.removeAll(exitset);
    results.put(md, loopbranchset);
  }

  void propagateset(Set<FlatNode> tovisit, Hashtable<FlatNode, Set<FlatCondBranch>> table, FlatNode fn, FlatNode fnnext, FlatCondBranch fcb) {
    boolean enqueuechange=false;
    if (!table.containsKey(fnnext))
      table.put(fnnext, new HashSet<FlatCondBranch>());

    if (table.containsKey(fn)) {
      HashSet<FlatCondBranch> toadd=new HashSet<FlatCondBranch>();
      toadd.addAll(table.get(fn));
      if (toadd.contains(fnnext))       //can't propagate back to node
	toadd.remove(fnnext);
      if(!table.get(fnnext).containsAll(toadd)) {
	table.get(fnnext).addAll(toadd);
	enqueuechange=true;
      }
    }
    if (fcb!=null&&!table.get(fnnext).contains(fcb)) {
      table.get(fnnext).add(fcb);
      enqueuechange=true;
    }
    if (enqueuechange)
      tovisit.add(fnnext);
  }
}
