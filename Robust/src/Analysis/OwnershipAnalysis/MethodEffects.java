package Analysis.OwnershipAnalysis;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import IR.FieldDescriptor;
import IR.Flat.FlatCall;
import IR.Flat.TempDescriptor;

public class MethodEffects {

	private EffectsSet effectsSet;

	public MethodEffects() {
		effectsSet = new EffectsSet();
	}

	public EffectsSet getEffects() {
		return effectsSet;
	}

	public void analyzeFlatFieldNode(OwnershipGraph og, TempDescriptor srcDesc,
			FieldDescriptor fieldDesc) {

		LabelNode ln = getLabelNodeFromTemp(og, srcDesc);
		if (ln != null) {
			Iterator<ReferenceEdge> heapRegionsItr = ln.iteratorToReferencees();

			while (heapRegionsItr.hasNext()) {
				ReferenceEdge edge = heapRegionsItr.next();
				HeapRegionNode hrn = edge.getDst();

				if (hrn.isParameter()) {
					Set<Integer> paramSet = og.idPrimary2paramIndexSet.get(hrn
							.getID());

					if (paramSet != null) {
						Iterator<Integer> paramIter = paramSet.iterator();
						while (paramIter.hasNext()) {
							Integer paramID = paramIter.next();
							effectsSet.addReadingVar(paramID, new EffectsKey(
									fieldDesc.getSymbol(), srcDesc.getType()));

						}
					}

					// check weather this heap region is parameter
					// reachable...

					paramSet = og.idSecondary2paramIndexSet.get(hrn.getID());
					if (paramSet != null) {
						Iterator<Integer> paramIter = paramSet.iterator();

						while (paramIter.hasNext()) {
							Integer paramID = paramIter.next();
							effectsSet.addReadingVar(paramID, new EffectsKey(
									fieldDesc.getSymbol(), srcDesc.getType()));

						}
					}

				}
			}
		}

	}

	public void analyzeFlatSetFieldNode(OwnershipGraph og,
			TempDescriptor dstDesc, FieldDescriptor fieldDesc) {

		LabelNode ln = getLabelNodeFromTemp(og, dstDesc);
		if (ln != null) {
			Iterator<ReferenceEdge> heapRegionsItr = ln.iteratorToReferencees();

			while (heapRegionsItr.hasNext()) {
				ReferenceEdge edge = heapRegionsItr.next();
				HeapRegionNode hrn = edge.getDst();

				if (hrn.isParameter()) {

					Set<Integer> paramSet = og.idPrimary2paramIndexSet.get(hrn
							.getID());

					if (paramSet != null) {
						Iterator<Integer> paramIter = paramSet.iterator();
						while (paramIter.hasNext()) {
							Integer paramID = paramIter.next();
							effectsSet.addWritingVar(paramID, new EffectsKey(
									fieldDesc.getSymbol(), dstDesc.getType()));

						}
					}

					// check weather this heap region is parameter
					// reachable...

					paramSet = og.idSecondary2paramIndexSet.get(hrn.getID());
					if (paramSet != null) {
						Iterator<Integer> paramIter = paramSet.iterator();

						while (paramIter.hasNext()) {
							Integer paramID = paramIter.next();
							effectsSet.addWritingVar(paramID, new EffectsKey(
									fieldDesc.getSymbol(), dstDesc.getType()));

						}
					}

				}
			}
		}

	}

	private Set<Integer> getReachableParamIndexSet(OwnershipGraph og,
			TempDescriptor paramDesc) {

		HashSet<Integer> resultSet = new HashSet<Integer>();

		LabelNode ln = getLabelNodeFromTemp(og, paramDesc);
		if (ln != null) {

			Iterator<ReferenceEdge> heapRegionsItr = ln.iteratorToReferencees();

			while (heapRegionsItr.hasNext()) {
				ReferenceEdge edge = heapRegionsItr.next();
				HeapRegionNode hrn = edge.getDst();

				if (hrn.isParameter()) {

					Set<Integer> paramSet = og.idPrimary2paramIndexSet.get(hrn
							.getID());

					if (paramSet != null) {
						Iterator<Integer> paramIter = paramSet.iterator();
						while (paramIter.hasNext()) {
							Integer paramID = paramIter.next();

							resultSet.add(paramID);

						}
					}

					// check weather this heap region is parameter
					// reachable...

					paramSet = og.idSecondary2paramIndexSet.get(hrn.getID());
					if (paramSet != null) {
						Iterator<Integer> paramIter = paramSet.iterator();

						while (paramIter.hasNext()) {
							Integer paramID = paramIter.next();

							resultSet.add(paramID);

						}
					}

				}
			}

		}

		return resultSet;

	}

	public void analyzeFlatCall(OwnershipGraph og, FlatCall fc,
			MethodContext mc, MethodEffects callee) {

		TempDescriptor[] tdArray = fc.readsTemps();

		for (int callerIdx = 0; callerIdx < tdArray.length; callerIdx++) {
			TempDescriptor paramDesc = tdArray[callerIdx];

			Set<Integer> paramIDs = getReachableParamIndexSet(og, paramDesc);

			// handle read effects
			Iterator<Integer> paramIter = paramIDs.iterator();
			while (paramIter.hasNext()) {
				Integer paramIdx = paramIter.next();
				HashSet<EffectsKey> newSet = callee.getEffects().getReadTable()
						.get(paramIdx);
				effectsSet.addReadingEffectsSet(callerIdx, newSet);
			}

			// handle write effects
			paramIter = paramIDs.iterator();
			while (paramIter.hasNext()) {
				Integer paramIdx = paramIter.next();
				HashSet<EffectsKey> newSet = callee.getEffects()
						.getWriteTable().get(paramIdx);
				effectsSet.addWritingEffectsSet(callerIdx, newSet);
			}

		}

	}

	protected LabelNode getLabelNodeFromTemp(OwnershipGraph og,
			TempDescriptor td) {
		assert td != null;

		if (!og.td2ln.containsKey(td)) {
			og.td2ln.put(td, new LabelNode(td));
		}

		return og.td2ln.get(td);
	}

	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}

		if (!(o instanceof MethodEffects)) {
			return false;
		}

		MethodEffects in = (MethodEffects) o;

		if (getEffects().equals(in.getEffects())) {
			return true;
		} else {
			return false;
		}

	}

	public int hashCode() {
		int hash = 1;

		hash += getEffects().hashCode() * 37;

		return hash;
	}

}
