package Analysis.MLP;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import Analysis.OwnershipAnalysis.HeapRegionNode;
import IR.Flat.TempDescriptor;

public class LiveInNode extends ConflictNode {

	Set<SESEEffectsKey> readEffectsSet;
	Set<SESEEffectsKey> writeEffectsSet;
	Set<HeapRegionNode> hrnSet;

	public LiveInNode(String id, TempDescriptor td, Set<HeapRegionNode> hrnSet,
			Set<SESEEffectsKey> readEffectsSet,
			Set<SESEEffectsKey> writeEffectsSet, Set<Set> reachabilitySet) {
		this.hrnSet = hrnSet;
		this.id = id;
		this.td = td;
		this.readEffectsSet = readEffectsSet;
		this.writeEffectsSet = writeEffectsSet;
		this.reachabilitySet = reachabilitySet;
	}

	public Set<HeapRegionNode> getHRNSet() {
		return hrnSet;
	}

	public Set<SESEEffectsKey> getReadEffectsSet() {
		return readEffectsSet;
	}

	public Set<SESEEffectsKey> getWriteEffectsSet() {
		return writeEffectsSet;
	}

	public void addReadEffectsSet(Set<SESEEffectsKey> newReadEffectsSet) {
		if (newReadEffectsSet != null) {
			readEffectsSet.addAll(newReadEffectsSet);
		}
	}

	public void addWriteEffectsSet(Set<SESEEffectsKey> newWriteEffectsSet) {
		if (newWriteEffectsSet != null) {
			writeEffectsSet.addAll(newWriteEffectsSet);
		}
	}

	public void addReachabilitySet(Set<Set> newReachabilitySet) {
		if (newReachabilitySet != null) {
			reachabilitySet.addAll(newReachabilitySet);
		}
	}

	public boolean isWriteConflictWith(StallSiteNode stallNode) {

		// if live-in var has write-effects on heap region node of stall site,
		// it is write conflict

		boolean result = false;
		StallSite stallSite = stallNode.getStallSite();

		if (writeEffectsSet != null) {
			Iterator<SESEEffectsKey> writeIter = writeEffectsSet.iterator();
			while (writeIter.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) writeIter
						.next();
				String writeHeapRegionID = seseEffectsKey.getHRNUniqueId();
				String writeFieldName = seseEffectsKey.getFieldDescriptor();

				HashSet<HeapRegionNode> stallSiteHRNSet = stallNode.getHRNSet();
				for (Iterator iterator = stallSiteHRNSet.iterator(); iterator
						.hasNext();) {
					HeapRegionNode stallHRN = (HeapRegionNode) iterator.next();
					if (stallHRN.getGloballyUniqueIdentifier().equals(
							writeHeapRegionID)) {

						// check whether there are read or write effects of
						// stall sites

						HashSet<Effect> effectSet = stallSite.getEffectSet();
						for (Iterator iterator2 = effectSet.iterator(); iterator2
								.hasNext();) {
							Effect effect = (Effect) iterator2.next();
							String stallEffectfieldName = effect.getField();

							if (stallEffectfieldName.equals(writeFieldName)) {
								result = result | true;
							}
						}

					}
				}

			}
		}

		if (readEffectsSet != null) {
			Iterator<SESEEffectsKey> readIter = readEffectsSet.iterator();
			while (readIter.hasNext()) {

				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) readIter
						.next();
				String readHeapRegionID = seseEffectsKey.getHRNUniqueId();
				String readFieldName = seseEffectsKey.getFieldDescriptor();

				HashSet<HeapRegionNode> stallSiteHRNSet = stallNode.getHRNSet();
				for (Iterator iterator = stallSiteHRNSet.iterator(); iterator
						.hasNext();) {
					HeapRegionNode stallHRN = (HeapRegionNode) iterator.next();
					if (stallHRN.getGloballyUniqueIdentifier().equals(
							readHeapRegionID)) {

						HashSet<Effect> effectSet = stallSite.getEffectSet();
						for (Iterator iterator2 = effectSet.iterator(); iterator2
								.hasNext();) {
							Effect effect = (Effect) iterator2.next();
							String stallEffectfieldName = effect.getField();

							if (effect.getEffectType().equals(
									StallSite.WRITE_EFFECT)) {
								if (stallEffectfieldName.equals(readFieldName)) {
									result = result | true;
								}
							}

						}

					}

				}

			}
		}

		return result;
	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof LiveInNode)) {
			return false;
		}

		LiveInNode in = (LiveInNode) o;

		if (id.equals(in.id)) {
			return true;
		} else {
			return false;
		}

	}

	public String toString() {
		String str = "LiveInNode " + id;
		return str;
	}

}
