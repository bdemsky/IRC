package Analysis.MLP;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import Analysis.OwnershipAnalysis.ReachabilitySet;
import Analysis.OwnershipAnalysis.TokenTupleSet;
import IR.Flat.TempDescriptor;

public class ParentChildConflictsMap {

	public static final Integer ACCESSIBLE = new Integer(1);
	public static final Integer INACCESSIBLE = new Integer(2);

	private Hashtable<TempDescriptor, Integer> accessibleMap;
	private Hashtable<TempDescriptor, StallSite> stallMap;

	public ParentChildConflictsMap() {

		accessibleMap = new Hashtable<TempDescriptor, Integer>();
		stallMap = new Hashtable<TempDescriptor, StallSite>();

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

	public void addStallSite(TempDescriptor td) {
		StallSite stallSite = new StallSite();
		stallMap.put(td, stallSite);
	}

	public boolean isAccessible(TempDescriptor td) {
		if (accessibleMap.contains(td)
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
			}
		}

		keySet = newStallMap.keySet();
		for (Iterator<TempDescriptor> iterator = keySet.iterator(); iterator
				.hasNext();) {
			TempDescriptor key = iterator.next();

			StallSite newStallSite = newStallMap.get(key);
			StallSite currentStallSite = getStallMap().get(key);

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
			HashSet<Integer> currentHRNSet = currentStallSite.getHRNIDSet();
			HashSet<Integer> newHRNSet = newStallSite.getHRNIDSet();
			for (Iterator iterator2 = newHRNSet.iterator(); iterator2.hasNext();) {
				Integer hrnID = (Integer) iterator2.next();
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

			getStallMap()
					.put(
							key,
							new StallSite(currentEffectSet, currentHRNSet,
									currentRSet));

		}

	}

}
