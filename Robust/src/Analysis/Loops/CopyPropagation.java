package Analysis.Loops;
import IR.Flat.*;
import IR.Operation;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Map;

public class CopyPropagation {
  public CopyPropagation() {
  }

  public void optimize(FlatMethod fm) {
    Hashtable<FlatNode, Hashtable<TempDescriptor, TempDescriptor>> table
      =new Hashtable<FlatNode, Hashtable<TempDescriptor, TempDescriptor>>();
    boolean changed=false;
    do {
      changed=false;
      HashSet tovisit=new HashSet();
      HashSet discovered=new HashSet();
      tovisit.add(fm);
      discovered.add(fm);
      while(!tovisit.isEmpty()) {
	FlatNode fn=(FlatNode) tovisit.iterator().next();
	tovisit.remove(fn);
	for(int i=0;i<fn.numNext();i++) {
	  FlatNode nnext=fn.getNext(i);
	  if (!discovered.contains(nnext)) {
	    discovered.add(nnext);
	    tovisit.add(nnext);
	  }
	}
	Hashtable<TempDescriptor, TempDescriptor> tab;
	if (fn.numPrev()>=1&&table.containsKey(fn.getPrev(0)))
	  tab=new Hashtable<TempDescriptor, TempDescriptor>(table.get(fn.getPrev(0)));
	else
	  tab=new Hashtable<TempDescriptor, TempDescriptor>();
	//Compute intersection
	for(int i=1;i<fn.numPrev();i++) {
	  Hashtable<TempDescriptor, TempDescriptor> tp=table.get(fn.getPrev(i));
	  for(Iterator tmpit=tab.entrySet().iterator();tmpit.hasNext();) {
	    Map.Entry t=(Map.Entry)tmpit.next();
	    TempDescriptor tmp=(TempDescriptor)t.getKey();
	    if (tp!=null&&(!tp.containsKey(tmp)||tp.get(tmp)!=tab.get(tmp))) {
	      tmpit.remove();
	    }
	  }
	}
	TempDescriptor[]writes=fn.writesTemps();
	for(int i=0;i<writes.length;i++) {
	  TempDescriptor tmp=writes[i];
	  for(Iterator<TempDescriptor> tmpit=tab.keySet().iterator();tmpit.hasNext();) {	
	    TempDescriptor tmp2=tmpit.next();
	    if (tmp==tab.get(tmp2))
	      tmpit.remove();
	  }
	}
	if (fn.kind()==FKind.FlatOpNode) {
	  FlatOpNode fon=(FlatOpNode)fn;
	  if (fon.getOp().getOp()==Operation.ASSIGN) {
	    tab.put(fon.getDest(), fon.getLeft());
	  }
	}
	if (!table.containsKey(fn)||!table.get(fn).equals(tab)) {
	  table.put(fn,tab);
	  changed=true;
	  for(int i=0;i<fn.numNext();i++) {
	    FlatNode nnext=fn.getNext(i);
	    tovisit.add(nnext);
	  }
	}
      }
      for(Iterator<FlatNode> it=fm.getNodeSet().iterator();it.hasNext();) {
	FlatNode fn=it.next();
	Hashtable<TempDescriptor, TempDescriptor> tab=table.get(fn);
	TempMap tmap=null;
	TempDescriptor[]reads=fn.readsTemps();
	for(int i=0;i<reads.length;i++) {
	  TempDescriptor tmp=reads[i];
	  if (tab.containsKey(tmp)) {
	    if (tmap==null)
	      tmap=new TempMap();
	    tmap.addPair(tmp, tab.get(tmp));
	  }
	}
	if (tmap!=null)
	  fn.rewriteUse(tmap);
      }
    } while(changed);
  }
}