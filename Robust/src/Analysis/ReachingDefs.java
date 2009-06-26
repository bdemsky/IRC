package Analysis;

import IR.Flat.*;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.Hashtable;

public class ReachingDefs {
  /* This methods takes in a FlatMethod and returns a map from a
   * FlatNode to the set of live temps for that FlatNode.*/

  public static Hashtable<FlatNode, Hashtable<TempDescriptor, Set<FlatNode>>> computeReachingDefs(FlatMethod fm, Hashtable<FlatNode, Set<TempDescriptor>> livemap) {
    Hashtable<FlatNode, Hashtable<TempDescriptor, Set<FlatNode>>> nodetotemps=new Hashtable<FlatNode, Hashtable<TempDescriptor,Set<FlatNode>>>();
    
    Set<FlatNode> toprocess=fm.getNodeSet();
    
    while(!toprocess.isEmpty()) {
      FlatNode fn=toprocess.iterator().next();
      toprocess.remove(fn);
      
      Hashtable<TempDescriptor, Set<FlatNode>> tempset=new Hashtable<TempDescriptor, Set<FlatNode>>();

      Set<TempDescriptor> livetempset=livemap.get(fn);

      for(int i=0; i<fn.numPrev(); i++) {
	FlatNode fnprev=fn.getPrev(i);
	if (nodetotemps.containsKey(fnprev)) {
	  Hashtable<TempDescriptor,Set<FlatNode>> prevtable=nodetotemps.get(fnprev);
	  for(Iterator<TempDescriptor> tmpit=prevtable.keySet().iterator();tmpit.hasNext();) {
	    TempDescriptor tmp=tmpit.next();
	    if (!livetempset.contains(tmp))
	      continue;
	    if (!tempset.containsKey(tmp))
	      tempset.put(tmp, new HashSet<FlatNode>());
	    tempset.get(tmp).addAll(prevtable.get(tmp));
	  }
	}
      }
      
      TempDescriptor writes[]=fn.writesTemps();
      for(int i=0;i<writes.length;i++) {
	HashSet<FlatNode> s=new HashSet<FlatNode>();
	s.add(fn);
	tempset.put(writes[i],s);
      }

      if (!nodetotemps.containsKey(fn)||
          !nodetotemps.get(fn).equals(tempset)) {
	nodetotemps.put(fn, tempset);
	for(int i=0; i<fn.numNext(); i++)
	  toprocess.add(fn.getNext(i));
      }
    }
    return nodetotemps;
  }
  
}