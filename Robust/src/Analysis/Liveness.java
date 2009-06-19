package Analysis;

import IR.Flat.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Hashtable;

public class Liveness {
  /* This methods takes in a FlatMethod and returns a map from a
   * FlatNode to the set of live temps for that FlatNode.*/

  public static Hashtable<FlatNode, Set<TempDescriptor>> computeLiveTemps(FlatMethod fm) {
    Hashtable<FlatNode, Set<TempDescriptor>> nodetotemps=new Hashtable<FlatNode, Set<TempDescriptor>>();
    
    Set<FlatNode> toprocess=fm.getNodeSet();
    
    while(!toprocess.isEmpty()) {
      FlatNode fn=toprocess.iterator().next();
      toprocess.remove(fn);
      
      List<TempDescriptor> reads=Arrays.asList(fn.readsTemps());
      List<TempDescriptor> writes=Arrays.asList(fn.writesTemps());
      
      HashSet<TempDescriptor> tempset=new HashSet<TempDescriptor>();
      for(int i=0; i<fn.numNext(); i++) {
	FlatNode fnnext=fn.getNext(i);
	if (nodetotemps.containsKey(fnnext))
	  tempset.addAll(nodetotemps.get(fnnext));
      }
      tempset.removeAll(writes);
      tempset.addAll(reads);
      if (!nodetotemps.containsKey(fn)||
          !nodetotemps.get(fn).equals(tempset)) {
	nodetotemps.put(fn, tempset);
	for(int i=0; i<fn.numPrev(); i++)
	  toprocess.add(fn.getPrev(i));
      }
    }
    return nodetotemps;
  }
  
}