package Analysis.Loops;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import IR.Operation;
import IR.TypeUtil;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.TempDescriptor;
import IR.Flat.TempMap;

public class LoopOptimize {
  private LoopInvariant loopinv;
  private GlobalFieldType gft;
  private TypeUtil typeutil;
  private Map<FlatMethod, LoopInvariant> fm2loopinv;
  
  private Hashtable<FlatNode, FlatNode> ntoomap;
  private Hashtable<FlatNode, FlatNode> clonemap;
  private Hashtable<FlatNode, FlatNode> map;
  
  public LoopOptimize(GlobalFieldType gft, TypeUtil typeutil) {
    this.gft = gft;
    this.typeutil = typeutil;
    fm2loopinv = new HashMap<FlatMethod, LoopInvariant>();
  }

  public void optimize(FlatMethod fm) {
    loopinv = new LoopInvariant(typeutil, gft);
    loopinv.analyze(fm);
    fm2loopinv.put(fm, loopinv);
    
    ntoomap=new Hashtable<FlatNode, FlatNode>();
    map=new Hashtable<FlatNode, FlatNode>();
    clonemap=new Hashtable<FlatNode, FlatNode>();
    dooptimize(fm);
  }

  private FlatNode ntooremap(FlatNode fn) {
    while(ntoomap.containsKey(fn)) {
      fn=ntoomap.get(fn);
    }
    return fn;
  }

  private FlatNode otonremap(FlatNode fn) {
    while(map.containsKey(fn)) {
      fn=map.get(fn);
    }
    return fn;
  }

  private void dooptimize(FlatMethod fm) {
    Loops root=loopinv.root;
    recurse(fm, root);
  }
  private void recurse(FlatMethod fm, Loops parent) {
    for(Iterator lpit=parent.nestedLoops().iterator(); lpit.hasNext(); ) {
      Loops child=(Loops)lpit.next();
      processLoop(fm, child);
      recurse(fm, child);
    }
  }
  public void processLoop(FlatMethod fm, Loops l) {
    Set entrances=l.loopEntrances();
    assert entrances.size()==1;
    FlatNode entrance=(FlatNode)entrances.iterator().next();
    if (loopinv.tounroll.contains(entrance)) {
      unrollLoop(l, fm);
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

    for(int i=0; i<tohoist.size(); i++) {
      FlatNode fn=tohoist.elementAt(i);
      TempDescriptor[] writes=fn.writesTemps();

      //deal with the possiblity we already hoisted this node
      if (clonemap.containsKey(fn)) {
        FlatNode fnnew=clonemap.get(fn);
        TempDescriptor writenew[]=fnnew.writesTemps();
        t.addPair(writes[0],writenew[0]);
        if (fn==entrance)
          entrance=map.get(fn);
        continue;
      }

      //build hoisted version
      FlatNode fnnew=fn.clone(tnone);
      fnnew.rewriteUse(t);

      for(int j=0; j<writes.length; j++) {
        if (writes[j]!=null) {
          TempDescriptor cp=writes[j].createNew();
          t.addPair(writes[j],cp);
        }
      }
      fnnew.rewriteDef(t);

      //store mapping
      clonemap.put(fn, fnnew);

      //add hoisted version to chain
      if (first==null)
        first=fnnew;
      else
        last.addNext(fnnew);
      last=fnnew;

      /* Splice out old node */
      if (writes.length==1) {
        FlatOpNode fon=new FlatOpNode(writes[0], t.tempMap(writes[0]), null, new Operation(Operation.ASSIGN));
        fn.replace(fon);
        ntoomap.put(fon, fn);
        map.put(fn, fon);
        if (fn==entrance)
          entrance=fon;
      } else if (writes.length>1) {
        throw new Error();
      }
    }
    /* If the chain is empty, we can exit now */
    if (first==null)
      return;

    /* The chain is built at this point. */
    FlatNode[] prevarray=new FlatNode[entrance.numPrev()];
    for(int i=0; i<entrance.numPrev(); i++) {
      prevarray[i]=entrance.getPrev(i);
    }
    for(int i=0; i<prevarray.length; i++) {
      FlatNode prev=prevarray[i];

      if (!lelements.contains(ntooremap(prev))) {
        //need to fix this edge
        for(int j=0; j<prev.numNext(); j++) {
          if (prev.getNext(j)==entrance)
            prev.setNext(j, first);
        }
      }
    }
    last.addNext(entrance);
  }

  public void unrollLoop(Loops l, FlatMethod fm) {
    assert l.loopEntrances().size()==1;
    //deal with possibility that entrance has been hoisted
    FlatNode entrance=(FlatNode)l.loopEntrances().iterator().next();
    entrance=otonremap(entrance);

    Set lelements=l.loopIncElements();

    Set<FlatNode> tohoist=loopinv.hoisted;
    Hashtable<FlatNode, TempDescriptor> temptable=new Hashtable<FlatNode, TempDescriptor>();
    Hashtable<FlatNode, FlatNode> copytable=new Hashtable<FlatNode, FlatNode>();
    Hashtable<FlatNode, FlatNode> copyendtable=new Hashtable<FlatNode, FlatNode>();

    TempMap t=new TempMap();
    /* Copy the nodes */
    for(Iterator it=lelements.iterator(); it.hasNext(); ) {
      FlatNode fn=(FlatNode)it.next();
      FlatNode nfn=otonremap(fn);

      FlatNode copy=nfn.clone(t);
      FlatNode copyend=copy;
      if (tohoist.contains(fn)) {
        //deal with the possiblity we already hoisted this node
        if (clonemap.containsKey(fn)) {
          FlatNode fnnew=clonemap.get(fn);
          TempDescriptor writenew[]=fnnew.writesTemps();
          temptable.put(nfn, writenew[0]);
        } else {
          TempDescriptor[] writes=nfn.writesTemps();
          TempDescriptor tmp=writes[0];
          TempDescriptor ntmp=tmp.createNew();
          temptable.put(nfn, ntmp);
          copyend=new FlatOpNode(ntmp, tmp, null, new Operation(Operation.ASSIGN));
          copy.addNext(copyend);
        }
      }
      copytable.put(nfn, copy);
      copyendtable.put(nfn, copyend);
    }

    /* Store initial in set for loop header */
    FlatNode[] prevarray=new FlatNode[entrance.numPrev()];
    for(int i=0; i<entrance.numPrev(); i++) {
      prevarray[i]=entrance.getPrev(i);
    }
    FlatNode first=copytable.get(entrance);

    /* Copy the internal edges */
    for(Iterator it=lelements.iterator(); it.hasNext(); ) {
      FlatNode fn=(FlatNode)it.next();
      fn=otonremap(fn);
      FlatNode copyend=copyendtable.get(fn);
      for(int i=0; i<fn.numNext(); i++) {
        FlatNode nnext=fn.getNext(i);
        if (nnext==entrance) {
          /* Back to loop header...point to old graph */
          copyend.setNewNext(i,nnext);
        } else if (lelements.contains(ntooremap(nnext))) {
          /* In graph...point to first graph */
          copyend.setNewNext(i,copytable.get(nnext));
        } else {
          /* Outside loop */
          /* Just goto same place as before */
          copyend.setNewNext(i,nnext);
        }
      }
    }

    /* Splice header in using original in set */
    for(int i=0; i<prevarray.length; i++) {
      FlatNode prev=prevarray[i];

      if (!lelements.contains(ntooremap(prev))) {
        //need to fix this edge
        for(int j=0; j<prev.numNext(); j++) {
          if (prev.getNext(j)==entrance) {
            prev.setNext(j, first);
          }
        }
      }
    }

    /* Splice out loop invariant stuff */
    for(Iterator it=lelements.iterator(); it.hasNext(); ) {
      FlatNode fn=(FlatNode)it.next();
      FlatNode nfn=otonremap(fn);
      if (tohoist.contains(fn)) {
        TempDescriptor[] writes=nfn.writesTemps();
        TempDescriptor tmp=writes[0];
        FlatOpNode fon=new FlatOpNode(tmp, temptable.get(nfn), null, new Operation(Operation.ASSIGN));
        nfn.replace(fon);
      }
    }
  }
  
  public LoopInvariant getLoopInvariant(FlatMethod fm){
    return fm2loopinv.get(fm);
  }
}
