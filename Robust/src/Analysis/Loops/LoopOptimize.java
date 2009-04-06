package Analysis.Loops;

import IR.Flat.*;
import IR.TypeUtil;
import IR.MethodDescriptor;
import IR.Operation;
import java.util.Set;
import java.util.Vector;
import java.util.Iterator;
import java.util.Hashtable;

public class LoopOptimize {
  LoopInvariant loopinv;
  public LoopOptimize(GlobalFieldType gft, TypeUtil typeutil) {
    loopinv=new LoopInvariant(typeutil,gft);
  }
  public void optimize(FlatMethod fm) {
    loopinv.analyze(fm);
    dooptimize(fm);
  } 
  private void dooptimize(FlatMethod fm) {
    Loops root=loopinv.root;
    recurse(root);
  }
  private void recurse(Loops parent) {
    for(Iterator lpit=parent.nestedLoops().iterator();lpit.hasNext();) {
      Loops child=(Loops)lpit.next();
      processLoop(child);
      recurse(child);
    }
  }
  public void processLoop(Loops l) {
    if (loopinv.tounroll.contains(l)) {
      unrollLoop(l);
    } else {
      hoistOps(l);
    }
  }
  public void hoistOps(Loops l) {
    Set entrances=l.loopEntrances();
    assert entrances.size()==1;
    FlatNode entrance=(FlatNode)entrances.iterator().next();
    Vector<FlatNode> tohoist=loopinv.table.get(entrance);
    Set lelements=l.loopIncElements();
    TempMap t=new TempMap();
    TempMap tnone=new TempMap();
    FlatNode first=null;
    FlatNode last=null;
    if (tohoist.size()==0)
      return;

    for(int i=0;i<tohoist.size();i++) {
      FlatNode fn=tohoist.elementAt(i);
      TempDescriptor[] writes=fn.writesTemps();
      FlatNode fnnew=fn.clone(tnone);

      fnnew.rewriteUse(t);

      for(int j=0;j<writes.length;j++) {
	if (writes[j]!=null) {
	  TempDescriptor cp=writes[j].createNew();
	  t.addPair(writes[j],cp);
	}
      }
      fnnew.rewriteDef(t);

      if (first==null)
	first=fnnew;
      else
	last.addNext(fnnew);
      last=fnnew;
      /* Splice out old node */
      if (writes.length==1) {
	FlatOpNode fon=new FlatOpNode(writes[0], t.tempMap(writes[0]), null, new Operation(Operation.ASSIGN));
	fn.replace(fon);
	if (fn==entrance)
	  entrance=fon;
      } else if (writes.length>1) {
	throw new Error();
      }
    }
    /* The chain is built at this point. */
    
    FlatNode[] prevarray=new FlatNode[entrance.numPrev()];
    for(int i=0;i<entrance.numPrev();i++) {
      prevarray[i]=entrance.getPrev(i);
    }
    for(int i=0;i<prevarray.length;i++) {
      FlatNode prev=prevarray[i];

      if (!lelements.contains(prev)) {
	//need to fix this edge
	for(int j=0;j<prev.numNext();j++) {
	  if (prev.getNext(j)==entrance)
	    prev.setNext(j, first);
	}
      }
    }
    last.addNext(entrance);
  }
  public void unrollLoop(Loops l) {
    assert l.loopEntrances().size()==1;
    FlatNode entrance=(FlatNode)l.loopEntrances().iterator().next();
    Set lelements=l.loopIncElements();
    Set<FlatNode> tohoist=loopinv.hoisted;
    Hashtable<FlatNode, TempDescriptor> temptable=new Hashtable<FlatNode, TempDescriptor>();
    Hashtable<FlatNode, FlatNode> copytable=new Hashtable<FlatNode, FlatNode>();
    Hashtable<FlatNode, FlatNode> copyendtable=new Hashtable<FlatNode, FlatNode>();
    
    TempMap t=new TempMap();
    /* Copy the nodes */
    for(Iterator it=lelements.iterator();it.hasNext();) {
      FlatNode fn=(FlatNode)it.next();
      FlatNode copy=fn.clone(t);
      FlatNode copyend=copy;
      if (tohoist.contains(fn)) {
	TempDescriptor[] writes=fn.writesTemps();
	TempDescriptor tmp=writes[0];
	TempDescriptor ntmp=tmp.createNew();
	temptable.put(fn, ntmp);
	copyend=new FlatOpNode(ntmp, tmp, null, new Operation(Operation.ASSIGN));
	copy.addNext(copyend);
      }
      copytable.put(fn, copy);
      copyendtable.put(fn, copyend);
    }
    /* Copy the edges */
    for(Iterator it=lelements.iterator();it.hasNext();) {
      FlatNode fn=(FlatNode)it.next();
      FlatNode copyend=copyendtable.get(fn);
      for(int i=0;i<fn.numNext();i++) {
	FlatNode nnext=fn.getNext(i);
	if (nnext==entrance) {
	  /* Back to loop header...point to old graph */
	  copyend.addNext(nnext);
	} else if (lelements.contains(nnext)) {
	  /* In graph...point to first graph */
	  copyend.addNext(copytable.get(nnext));
	} else {
	  /* Outside loop */
	  /* Just goto same place as before */
	  copyend.addNext(nnext);
	}
      }
    }
    /* Splice out loop invariant stuff */
    for(Iterator it=lelements.iterator();it.hasNext();) {
      FlatNode fn=(FlatNode)it.next();
      if (tohoist.contains(fn)) {
	TempDescriptor[] writes=fn.writesTemps();
	TempDescriptor tmp=writes[0];
	FlatOpNode fon=new FlatOpNode(temptable.get(fn),tmp, null, new Operation(Operation.ASSIGN));
	fn.replace(fon);
      }
    }
  }
}
