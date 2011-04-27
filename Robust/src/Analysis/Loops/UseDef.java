package Analysis.Loops;

import IR.Flat.*;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Iterator;
import Analysis.Liveness;

public class UseDef {
  Hashtable<TempFlatPair, Set<FlatNode>> defs;
  Hashtable<TempFlatPair, Set<FlatNode>> uses;

  public UseDef() {
  }

  public UseDef(FlatMethod fm) {
    analyze(fm);
  }

  /* Return FlatNodes that define Temp */
  public Set<FlatNode> defMap(FlatNode fn, TempDescriptor t) {
    Set<FlatNode> s=defs.get(new TempFlatPair(t,fn));
    if (s==null)
      return new HashSet<FlatNode>();
    else
      return s;
  }

  /* Return FlatNodes that use Temp */
  public Set<FlatNode> useMap(FlatNode fn, TempDescriptor t) {
    Set<FlatNode> s=uses.get(new TempFlatPair(t,fn));
    if (s==null)
      return new HashSet<FlatNode>();
    else
      return s;
  }

  public void analyze(FlatMethod fm) {
    Hashtable<FlatNode, Set<TempFlatPair>> tmp=new Hashtable<FlatNode, Set<TempFlatPair>>();
    HashSet<FlatNode> toanalyze=new HashSet<FlatNode>();
    Hashtable<FlatNode, Set<TempDescriptor>> livemap=Liveness.computeLiveTemps(fm);
    toanalyze.addAll(fm.getNodeSet());
    while(!toanalyze.isEmpty()) {
      FlatNode fn=toanalyze.iterator().next();
      TempDescriptor[] fnwrites=fn.writesTemps();

      toanalyze.remove(fn);
      HashSet<TempFlatPair> s=new HashSet<TempFlatPair>();
      Set<TempDescriptor> liveset=livemap.get(fn);
      for(int i=0; i<fn.numPrev(); i++) {
        FlatNode prev=fn.getPrev(i);
        Set<TempFlatPair> prevs=tmp.get(prev);
        if (prevs!=null) {
nexttfp:
          for(Iterator<TempFlatPair> tfit=prevs.iterator(); tfit.hasNext(); ) {
            TempFlatPair tfp=tfit.next();
            if (!liveset.contains(tfp.t))
              continue;
            for(int j=0; j<fnwrites.length; j++) {
              if (tfp.t==fnwrites[j])
                continue nexttfp;
            }
            s.add(tfp);
          }
        }
        for(int j=0; j<fnwrites.length; j++) {
          TempFlatPair tfp=new TempFlatPair(fnwrites[j], fn);
          s.add(tfp);
        }
      }
      if (!tmp.containsKey(fn)||
          !tmp.get(fn).equals(s)) {
        tmp.put(fn,s);
        for(int i=0; i<fn.numNext(); i++)
          toanalyze.add(fn.getNext(i));
      }
    }
    Set<FlatNode> fset=fm.getNodeSet();
    defs=new Hashtable<TempFlatPair, Set<FlatNode>>();
    uses=new Hashtable<TempFlatPair, Set<FlatNode>>();
    for(Iterator<FlatNode> fnit=fset.iterator(); fnit.hasNext(); ) {
      FlatNode fn=fnit.next();
      TempDescriptor[] fnreads=fn.readsTemps();
      Set<TempFlatPair> tfpset=tmp.get(fn);

      for(int i=0; i<fnreads.length; i++) {
        TempDescriptor readt=fnreads[i];
        for(Iterator<TempFlatPair> tfpit=tfpset.iterator(); tfpit.hasNext(); ) {
          TempFlatPair tfp=tfpit.next();
          if (tfp.t==readt) {
            //have use
            if (!uses.containsKey(tfp))
              uses.put(tfp,new HashSet<FlatNode>());
            uses.get(tfp).add(fn);
            TempFlatPair readtfp=new TempFlatPair(readt,fn);
            if (!defs.containsKey(readtfp))
              defs.put(readtfp,new HashSet<FlatNode>());
            defs.get(readtfp).add(tfp.f);
          }
        }
      }
    }
  }
}

class TempFlatPair {
  FlatNode f;
  TempDescriptor t;
  TempFlatPair(TempDescriptor t, FlatNode f) {
    this.t=t;
    this.f=f;
  }

  public int hashCode() {
    return f.hashCode()^t.hashCode();
  }
  public boolean equals(Object o) {
    TempFlatPair tf=(TempFlatPair)o;
    return t.equals(tf.t)&&f.equals(tf.f);
  }
}