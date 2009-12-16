package Analysis.MLP;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import Analysis.OwnershipAnalysis.AllocationSite;
import Analysis.OwnershipAnalysis.HeapRegionNode;
import Analysis.OwnershipAnalysis.ReferenceEdge;
import Analysis.OwnershipAnalysis.TokenTupleSet;
import IR.Flat.TempDescriptor;

public class ParentChildConflictsMap {

	public static final Integer ACCESSIBLE = new Integer(1);
	public static final Integer INACCESSIBLE = new Integer(2);

	private Hashtable<TempDescriptor, Integer> accessibleMap;
	private Hashtable<TempDescriptor, StallSite> stallMap;
	private Hashtable < ReferenceEdge, HashSet<StallTag> > stallEdgeMap;

	public ParentChildConflictsMap() {

		accessibleMap = new Hashtable<TempDescriptor, Integer>();
		stallMap = new Hashtable<TempDescriptor, StallSite>();
		stallEdgeMap= new Hashtable < ReferenceEdge, HashSet<StallTag> >();
	}
	
	public void makeAllInaccessible(){
		
		Set<TempDescriptor> keySet=accessibleMap.keySet();
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			TempDescriptor key = (TempDescriptor) iterator.next();
			accessibleMap.put(key, INACCESSIBLE);	
		}
		
	}
	
	public Hashtable < ReferenceEdge, HashSet<StallTag> > getStallEdgeMap(){
		return stallEdgeMap;
	}
	
	public void addStallEdge(ReferenceEdge edge, StallTag sTag){
		
		HashSet<StallTag> tagSet=stallEdgeMap.get(edge);
		if(tagSet==null){
			tagSet=new HashSet<StallTag>();
		}
		tagSet.add(sTag);
		stallEdgeMap.put(edge, tagSet);
	}
	
	public HashSet<StallTag> getStallTagByEdge(ReferenceEdge edge){
		return stallEdgeMap.get(edge);
	}
	
	public Hashtable<TempDescriptor, Integer> getAccessibleMap() {
		return accessibleMap;
	}

	public Hashtable<TempDescriptor, StallSite> getStallMap() {
		return stallMap;
	}

	public void addAccessibleVar(TempDescriptor td) {
		accessibleMap.put(td, ACCESSIBLE);
	}

	public void addInaccessibleVar(TempDescriptor td) {
		accessibleMap.put(td, INACCESSIBLE);
	}

	public void addStallSite(TempDescriptor td, HashSet<HeapRegionNode> heapSet, StallTag sTag) {
		StallSite stallSite=new StallSite(heapSet,sTag);
		stallMap.put(td, stallSite);
	}
	
	public void addStallSite(TempDescriptor td, StallSite stallSite) {
		stallMap.put(td, stallSite);
	}

	public boolean hasStallSite(TempDescriptor td){
		return stallMap.containsKey(td);
	}

	public boolean isAccessible(TempDescriptor td) {
		if (accessibleMap.containsKey(td)
				&& accessibleMap.get(td).equals(ACCESSIBLE)) {
			return true;
		}
		return false;
	}

	public void contributeEffect(TempDescriptor td, String type, String field,
			int effect) {

		StallSite stallSite = stallMap.get(td);
		if (stallSite != null) {
			stallSite.addEffect(type, field, effect);
		}

	}

	public void merge(ParentChildConflictsMap newConflictsMap) {
		
		Hashtable<TempDescriptor, Integer> newAccessibleMap = newConflictsMap
				.getAccessibleMap();
		Hashtable<TempDescriptor, StallSite> newStallMap = newConflictsMap
				.getStallMap();

		Set<TempDescriptor> keySet = newAccessibleMap.keySet();
		for (Iterator<TempDescriptor> iterator = keySet.iterator(); iterator
				.hasNext();) {
			TempDescriptor key = iterator.next();

			Integer newStatus = newAccessibleMap.get(key);

			// inaccessible is prior to accessible
			Integer currentStatus = getAccessibleMap().get(key);
			if (currentStatus != null && currentStatus == ACCESSIBLE
					&& newStatus == INACCESSIBLE) {
				getAccessibleMap().put(key, INACCESSIBLE);
			}else if(currentStatus == null && newStatus == ACCESSIBLE){
				getAccessibleMap().put(key, ACCESSIBLE);
			} 
		}

		keySet = newStallMap.keySet();
		for (Iterator<TempDescriptor> iterator = keySet.iterator(); iterator
				.hasNext();) {
			TempDescriptor key = iterator.next();

			StallSite newStallSite = newStallMap.get(key);
			StallSite currentStallSite = getStallMap().get(key);
			
			if(currentStallSite==null){
				currentStallSite=new StallSite();
			}

			// handle effects
			HashSet<Effect> currentEffectSet = currentStallSite.getEffectSet();
			HashSet<Effect> newEffectSet = newStallSite.getEffectSet();
			for (Iterator iterator2 = newEffectSet.iterator(); iterator2
					.hasNext();) {
				Effect effect = (Effect) iterator2.next();
				if (!currentEffectSet.contains(effect)) {
					currentEffectSet.add(effect);
				}
			}

			// handle heap region
			HashSet<HeapRegionNode> currentHRNSet = currentStallSite.getHRNSet();
			HashSet<HeapRegionNode> newHRNSet = newStallSite.getHRNSet();
			for (Iterator iterator2 = newHRNSet.iterator(); iterator2.hasNext();) {
				HeapRegionNode hrnID = (HeapRegionNode) iterator2.next();
				if (!currentHRNSet.contains(hrnID)) {
					currentHRNSet.add(hrnID);
				}
			}

			// handle reachabilitySet
			HashSet<TokenTupleSet> currentRSet=currentStallSite.getReachabilitySet();
			HashSet<TokenTupleSet> newRSet=newStallSite.getReachabilitySet();
			Iterator<TokenTupleSet> ttsIter=newRSet.iterator();
			while(ttsIter.hasNext()){
				TokenTupleSet tokenTupleSet=(TokenTupleSet) ttsIter.next();
				currentRSet.add(tokenTupleSet);
			}
//			ReachabilitySet currentRSet = currentStallSite.getReachabilitySet();
//			ReachabilitySet newRSet = newStallSite.getReachabilitySet();
//			Iterator<TokenTupleSet> ttsIter = newRSet.iterator();
//			while (ttsIter.hasNext()) {
//				TokenTupleSet tokenTupleSet = (TokenTupleSet) ttsIter.next();
//				currentRSet.add(tokenTupleSet);
//			}
			
			//handle allocationsite
			HashSet<AllocationSite> currentAloc=currentStallSite.getAllocationSiteSet();
			HashSet<AllocationSite> newAloc=newStallSite.getAllocationSiteSet();
			currentAloc.addAll(newAloc);			
			
			// handle related stall tags
			HashSet<StallTag> currentStallTagSet=currentStallSite.getStallTagSet();
			HashSet<StallTag> newStallTagSet=newStallSite.getStallTagSet();
			currentStallTagSet.addAll(newStallTagSet);
			
			// reaching param idxs
			HashSet<Integer> currentParamIdx=currentStallSite.getCallerParamIdxSet();
			HashSet<Integer> newParamIdx=newStallSite.getCallerParamIdxSet();
			currentParamIdx.addAll(newParamIdx);
			
			StallSite merged=new StallSite(currentEffectSet, currentHRNSet,
					currentRSet, currentAloc, currentStallTagSet,currentParamIdx);
			

			getStallMap()
					.put(
							key,
							merged);

		}
		
		// merge edge mapping
		
		Hashtable<ReferenceEdge, HashSet<StallTag>> newStallEdgeMapping=newConflictsMap.getStallEdgeMap();
		Set<ReferenceEdge> edgeSet=newStallEdgeMapping.keySet();
		
		for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
			ReferenceEdge stallEdge = (ReferenceEdge) iterator.next();
			HashSet<StallTag> newStallTagSet=newStallEdgeMapping.get(stallEdge);
			HashSet<StallTag>currentStallTagSet=getStallEdgeMap().get(stallEdge);
			
			if(currentStallTagSet==null){
				currentStallTagSet=new 	HashSet<StallTag>();
			}
			currentStallTagSet.addAll(newStallTagSet);
			getStallEdgeMap().put(stallEdge,currentStallTagSet);
		}

	}
	
	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof ParentChildConflictsMap)) {
			return false;
		}

		ParentChildConflictsMap in = (ParentChildConflictsMap) o;

		if ( accessibleMap.equals(in.getAccessibleMap())
				&& stallMap.equals(in.getStallMap())) {
			return true;
		} else {
			return false;
		}

	}

	public String toString() {
		return "ParentChildConflictsMap [accessibleMap=" + accessibleMap
				+ ", stallMap="
				+ stallMap + "]";
	}

}
