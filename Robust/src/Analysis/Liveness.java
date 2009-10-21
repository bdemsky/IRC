package Analysis;

import IR.Flat.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.util.Hashtable;
import Analysis.Locality.*;

public class Liveness {
  /* This methods takes in a FlatMethod and returns a map from a
   * FlatNode to the set of temps that are live into the FlatNode.*/

  public static Hashtable<FlatNode, Set<TempDescriptor>> computeLiveTemps(FlatMethod fm) {
    return computeLiveTemps(fm, null);
  }
  public static Hashtable<FlatNode, Set<TempDescriptor>> computeLiveTemps(FlatMethod fm, LocalityBinding lb) {
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
      if ((lb==null)||(!(fn instanceof FlatGlobalConvNode))||
	  ((FlatGlobalConvNode)fn).getLocality()==lb) {
	tempset.removeAll(writes);
	tempset.addAll(reads);
      }
      if (!nodetotemps.containsKey(fn)||
          !nodetotemps.get(fn).equals(tempset)) {
	nodetotemps.put(fn, tempset);
	for(int i=0; i<fn.numPrev(); i++)
	  toprocess.add(fn.getPrev(i));
      }
    }
    return nodetotemps;
  }
  
  public static Hashtable<FlatNode, Set<TempDescriptor>> computeLiveOut(FlatMethod fm) {
    return computeLiveOut(fm, null);
  }

  public static Hashtable<FlatNode, Set<TempDescriptor>> computeLiveOut(FlatMethod fm, LocalityBinding lb) {
    Hashtable<FlatNode, Set<TempDescriptor>> liveinmap=computeLiveTemps(fm, lb);
    Hashtable<FlatNode, Set<TempDescriptor>> liveoutmap=new Hashtable<FlatNode, Set<TempDescriptor>>();
    
    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator(); fnit.hasNext();) {
      FlatNode fn=fnit.next();
      liveoutmap.put(fn, new HashSet<TempDescriptor>());
      for(int i=0;i<fn.numNext();i++) {
	FlatNode fn2=fn.getNext(i);
	liveoutmap.get(fn).addAll(liveinmap.get(fn2));
      }
    }
    return liveoutmap;
  }


  // Also allow an instantiation of this object that memoizes results
  protected Hashtable< FlatMethod, Hashtable< FlatNode, Set<TempDescriptor> > > fm2liveMap;

  public Liveness() {
    fm2liveMap = new Hashtable< FlatMethod, Hashtable< FlatNode, Set<TempDescriptor> > >();
  }

  public Set<TempDescriptor> getLiveInTemps( FlatMethod fm, FlatNode fn ) {
    if( !fm2liveMap.containsKey( fm ) ) {
      fm2liveMap.put( fm, Liveness.computeLiveTemps( fm ) );
    }
    return fm2liveMap.get( fm ).get( fn );
  }
}