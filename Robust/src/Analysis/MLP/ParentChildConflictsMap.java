package Analysis.MLP;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import Analysis.OwnershipAnalysis.HeapRegionNode;
import Analysis.OwnershipAnalysis.ReachabilitySet;
import Analysis.OwnershipAnalysis.ReferenceEdge;
import Analysis.OwnershipAnalysis.TokenTupleSet;
import IR.Flat.TempDescriptor;

public class ParentChildConflictsMap {

	public static final Integer ACCESSIBLE = new Integer(1);
	public static final Integer INACCESSIBLE = new Integer(2);

	private Hashtable<TempDescriptor, Integer> accessibleMap;
	private Hashtable<TempDescriptor, StallSite> stallMap;
	private Hashtable < ReferenceEdge, StallSite > stallEdgeMap;

	private boolean afterChildSESE;

	public ParentChildConflictsMap() {

		accessibleMap = new Hashtable<TempDescriptor, Integer>();
		stallMap = new Hashtable<TempDescriptor, StallSite>();
		stallEdgeMap= new Hashtable < ReferenceEdge, StallSite >();
		afterChildSESE=false;

	}
	
	public Hashtable < ReferenceEdge, StallSite > getStallEdgeMap(){
		return stallEdgeMap;
	}
	
	public void addStallEdge(ReferenceEdge edge, StallSite site){
		stallEdgeMap.put(edge, site);
	}
	
	public StallSite getStallSiteByEdge(ReferenceEdge edge){
		return stallEdgeMap.get(edge);
	}
	
	public void setAfterChildSESE(boolean b){
		this.afterChildSESE=b;
	}
	
	public boolean isAfterChildSESE(){
		return afterChildSESE;
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

	public void addStallSite(TempDescriptor td, HashSet<HeapRegionNode> heapSet) {
		StallSite stallSite=new StallSite(heapSet);
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
		
		if(afterChildSESE==false && newConflictsMap.isAfterChildSESE()){
			this.afterChildSESE=true;
		}

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
			ReachabilitySet currentRSet = currentStallSite.getReachabilitySet();
			ReachabilitySet newRSet = newStallSite.getReachabilitySet();
			Iterator<TokenTupleSet> ttsIter = newRSet.iterator();
			while (ttsIter.hasNext()) {
				TokenTupleSet tokenTupleSet = (TokenTupleSet) ttsIter.next();
				currentRSet.add(tokenTupleSet);
			}
			
			StallSite merged=new StallSite(currentEffectSet, currentHRNSet,
					currentRSet);

			getStallMap()
					.put(
							key,
							merged);

		}
		
		// merge edge mapping
		
		Hashtable<ReferenceEdge, StallSite> newStallEdgeMapping=newConflictsMap.getStallEdgeMap();
		Set<ReferenceEdge> edgeSet=newStallEdgeMapping.keySet();
		for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
			ReferenceEdge stallEdge = (ReferenceEdge) iterator.next();
			StallSite newStallSite=newStallEdgeMapping.get(stallEdge);
			getStallEdgeMap().put(stallEdge, newStallSite);
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

		if (afterChildSESE==in.isAfterChildSESE() && accessibleMap.equals(in.getAccessibleMap())
				&& stallMap.equals(in.getStallMap())) {
			return true;
		} else {
			return false;
		}

	}

	public String toString() {
		return "ParentChildConflictsMap [accessibleMap=" + accessibleMap
				+ ", afterChildSESE=" + afterChildSESE + ", stallMap="
				+ stallMap + "]";
	}

}
