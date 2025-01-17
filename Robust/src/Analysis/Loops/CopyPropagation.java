package Analysis.Loops;
import IR.Flat.*;
import IR.Operation;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import Analysis.Liveness;

public class CopyPropagation {
  public CopyPropagation() {
  }

  public void optimize(FlatMethod fm) {
    Hashtable<FlatNode, Hashtable<TempDescriptor, TempDescriptor>> table
      =new Hashtable<FlatNode, Hashtable<TempDescriptor, TempDescriptor>>();
    boolean changed=false;
    TempDescriptor bogustd=new TempDescriptor("bogus");
    do {
      Hashtable<FlatNode, Set<TempDescriptor>> livetemps=Liveness.computeLiveTemps(fm,1000);
      if(livetemps==null){
        System.out.println("Skipping CopyPropagation of "+fm.getMethod()+" due to size.");
        return;
      }
      changed=false;
      HashSet tovisit=new HashSet();
      tovisit.add(fm);
      while(!tovisit.isEmpty()) {
        FlatNode fn=(FlatNode) tovisit.iterator().next();
        tovisit.remove(fn);
        Hashtable<TempDescriptor, TempDescriptor> tab;
        Set<TempDescriptor> liveset=livetemps.get(fn);

        if (fn.numPrev()>=1&&table.containsKey(fn.getPrev(0))) {
          tab=new Hashtable<TempDescriptor, TempDescriptor>();
          for(Map.Entry<TempDescriptor, TempDescriptor> entry:table.get(fn.getPrev(0)).entrySet()) {
              if (liveset.contains(entry.getKey())) {
                  tab.put(entry.getKey(), entry.getValue());
              }
          }
        } else
          tab=new Hashtable<TempDescriptor, TempDescriptor>();
        //Compute intersection

        for(int i=1; i<fn.numPrev(); i++) {
          Hashtable<TempDescriptor, TempDescriptor> tp=table.get(fn.getPrev(i));
          if (tp==null)
            continue;
          for(Iterator tmpit=tp.entrySet().iterator(); tmpit.hasNext(); ) {
            Map.Entry t=(Map.Entry)tmpit.next();
            TempDescriptor tmp=(TempDescriptor)t.getKey();
            if (!liveset.contains(tmp))
              continue;
            TempDescriptor dsttmp=tp.get(tmp);
            if (!tab.containsKey(tmp)) {
                tab.put(tmp, dsttmp);
            } else if (tab.get(tmp)!=dsttmp) {
              tab.put(tmp, bogustd);
            }
          }
        }
        
        HashSet<TempDescriptor> toremove=new HashSet<TempDescriptor>();
        TempDescriptor[] writes=fn.writesTemps();
        for(int i=0; i<writes.length; i++) {
          TempDescriptor tmp=writes[i];
          toremove.add(tmp);
          for(Iterator<TempDescriptor> tmpit=tab.keySet().iterator(); tmpit.hasNext(); ) {
            TempDescriptor tmp2=tmpit.next();
            if (tmp==tab.get(tmp2))
              toremove.add(tmp2);
          }
        }

        for(Iterator<TempDescriptor> tmpit=toremove.iterator(); tmpit.hasNext(); ) {
          TempDescriptor tmp=tmpit.next();
          tab.put(tmp, bogustd);
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
          for(int i=0; i<fn.numNext(); i++) {
            FlatNode nnext=fn.getNext(i);
            tovisit.add(nnext);
          }
        }
      } //end of dataflow while loop

      Set<FlatNode> nodeset=fm.getNodeSet();

      for(Iterator<FlatNode> it=fm.getNodeSet().iterator(); it.hasNext(); ) {
        FlatNode fn=it.next();
        if (fn.numPrev()==0)
          continue;

        Hashtable<TempDescriptor, TempDescriptor> tab=new Hashtable<TempDescriptor, TempDescriptor>();

        for(int i=0; i<fn.numPrev(); i++) {
          Hashtable<TempDescriptor, TempDescriptor> tp=table.get(fn.getPrev(i));
          for(Iterator tmpit=tp.entrySet().iterator(); tmpit.hasNext(); ) {
            Map.Entry t=(Map.Entry)tmpit.next();
            TempDescriptor tmp=(TempDescriptor)t.getKey();

            if (!tab.containsKey(tmp))
              tab.put(tmp, tp.get(tmp));
            else if (tab.get(tmp)!=tp.get(tmp)) {
              tab.put(tmp, bogustd);
            }
          }
        }

        TempMap tmap=null;
        TempDescriptor[] reads=fn.readsTemps();
        for(int i=0; i<reads.length; i++) {
          TempDescriptor tmp=reads[i];
          if (tab.containsKey(tmp)&&tab.get(tmp)!=bogustd) {
            if (tmap==null)
              tmap=new TempMap();
            tmap.addPair(tmp, tab.get(tmp));
          }
        }
        if (tmap!=null)
          fn.rewriteUse(tmap);
      } //end of remapping for loop
    } while(changed);
  }
}
