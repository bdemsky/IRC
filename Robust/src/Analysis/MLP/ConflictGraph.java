package Analysis.MLP;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import Analysis.OwnershipAnalysis.HeapRegionNode;
import IR.Flat.FlatMethod;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.TempDescriptor;

public class ConflictGraph {

	static private int uniqueCliqueIDcount = 100;

	public Hashtable<String, ConflictNode> id2cn;

	public ConflictGraph() {
		id2cn = new Hashtable<String, ConflictNode>();
	}

	public void addPseudoEdgeBetweenReadOnlyNode() {

		Collection<ConflictNode> conflictNodes = id2cn.values();
		Set<String> analyzedIDSet = new HashSet<String>();

		for (Iterator iterator = conflictNodes.iterator(); iterator.hasNext();) {
			ConflictNode conflictNode = (ConflictNode) iterator.next();
			if (isReadOnly(conflictNode)
					&& conflictNode.getEdgeSet().size() > 0) {

				for (Iterator<ConflictNode> iter = id2cn.values().iterator(); iter
						.hasNext();) {
					ConflictNode nextNode = iter.next();

					if (!conflictNode.equals(nextNode)
							&& nextNode.getEdgeSet().size() > 0
							&& !analyzedIDSet.contains(conflictNode.getID()
									+ nextNode.getID())
							&& !analyzedIDSet.contains(nextNode.getID()
									+ conflictNode.getID())
							&& isReadOnly(nextNode)) {
						if (hasSameReadEffects(conflictNode, nextNode)) {
							addConflictEdge(ConflictEdge.PSEUDO_EDGE,
									conflictNode, nextNode);
						}
					}
					analyzedIDSet.add(conflictNode.getID() + nextNode.getID());
				}
			}
		}
	}

	private boolean hasSameReadEffects(ConflictNode nodeA, ConflictNode nodeB) {

		StallSiteNode stallSiteNode = null;
		LiveInNode liveInNode = null;

		if (nodeA instanceof StallSiteNode && nodeB instanceof StallSiteNode) {
			return hasSameReadEffects((StallSiteNode) nodeA,
					(StallSiteNode) nodeB);
		}

		if (nodeA instanceof StallSiteNode) {
			stallSiteNode = (StallSiteNode) nodeA;
		} else {
			liveInNode = (LiveInNode) nodeA;
		}

		if (nodeB instanceof StallSiteNode) {
			stallSiteNode = (StallSiteNode) nodeB;
		} else {
			liveInNode = (LiveInNode) nodeB;
		}

		if (stallSiteNode != null) {
			return hasSameReadEffects(stallSiteNode, liveInNode);
		} else {
			return hasSameReadEffects((LiveInNode) nodeA, (LiveInNode) nodeB);
		}

	}

	private boolean hasSameReadEffects(LiveInNode linA, LiveInNode linB) {

		Set<SESEEffectsKey> readSetA = linA.getReadEffectsSet();
		Set<SESEEffectsKey> readSetB = linB.getReadEffectsSet();

		boolean returnValue = true;
		for (Iterator iterator = readSetA.iterator(); iterator.hasNext();) {
			SESEEffectsKey seseEffectsKey = (SESEEffectsKey) iterator.next();

			for (Iterator iterator2 = readSetB.iterator(); iterator2.hasNext();) {
				SESEEffectsKey opr = (SESEEffectsKey) iterator2.next();
				if (!(seseEffectsKey.getHRNUniqueId().equals(
						opr.getHRNUniqueId()) && seseEffectsKey
						.getFieldDescriptor().equals(opr.getFieldDescriptor()))) {
					returnValue = false;
				}
			}
		}
		return returnValue;
	}

	private boolean hasSameReadEffects(StallSiteNode ssnA, StallSiteNode ssnB) {

		HashSet<Effect> effectSetA = ssnA.getStallSite().getEffectSet();
		HashSet<HeapRegionNode> nodeSetA = ssnA.getStallSite().getHRNSet();
		HashSet<Effect> effectSetB = ssnB.getStallSite().getEffectSet();
		HashSet<HeapRegionNode> nodeSetB = ssnA.getStallSite().getHRNSet();
		boolean returnValue = true;

		for (Iterator iteratorA = effectSetA.iterator(); iteratorA.hasNext();) {
			Effect effectKeyA = (Effect) iteratorA.next();
			for (Iterator iterator2A = nodeSetA.iterator(); iterator2A
					.hasNext();) {
				HeapRegionNode hrnA = (HeapRegionNode) iterator2A.next();

				for (Iterator iteratorB = effectSetB.iterator(); iteratorB
						.hasNext();) {
					Effect effectKeyB = (Effect) iteratorB.next();
					for (Iterator iterator2B = nodeSetB.iterator(); iterator2B
							.hasNext();) {
						HeapRegionNode hrnB = (HeapRegionNode) iterator2B
								.next();

						if (!(hrnA.getGloballyUniqueIdentifier().equals(
								hrnB.getGloballyUniqueIdentifier()) && effectKeyA
								.getField().equals(effectKeyB.getField()))) {
							returnValue = false;
						}
					}
				}

			}

		}

		return returnValue;
	}

	private boolean hasSameReadEffects(StallSiteNode ssnA, LiveInNode linB) {

		HashSet<Effect> effectSetA = ssnA.getStallSite().getEffectSet();
		HashSet<HeapRegionNode> nodeSetA = ssnA.getStallSite().getHRNSet();

		Set<SESEEffectsKey> readSetB = linB.getReadEffectsSet();
		if (readSetB == null) {
			return false;
		}

		boolean returnValue = true;

		for (Iterator iterator = effectSetA.iterator(); iterator.hasNext();) {
			Effect effectKey = (Effect) iterator.next();
			for (Iterator iterator2 = nodeSetA.iterator(); iterator2.hasNext();) {
				HeapRegionNode hrn = (HeapRegionNode) iterator2.next();

				for (Iterator iterator3 = readSetB.iterator(); iterator3
						.hasNext();) {
					SESEEffectsKey seseEffectsKey = (SESEEffectsKey) iterator3
							.next();

					if (!(seseEffectsKey.getHRNUniqueId().equals(
							hrn.getGloballyUniqueIdentifier()) && seseEffectsKey
							.getFieldDescriptor().equals(effectKey.getField()))) {
						returnValue = false;
					}

				}

			}
		}

		return returnValue;
	}

	private boolean isReadOnly(ConflictNode node) {

		if (node instanceof StallSiteNode) {
			StallSiteNode stallSiteNode = (StallSiteNode) node;
			HashSet<Effect> effectSet = stallSiteNode.getStallSite()
					.getEffectSet();
			for (Iterator iterator = effectSet.iterator(); iterator.hasNext();) {
				Effect effect = (Effect) iterator.next();
				if (effect.getEffectType().equals(StallSite.WRITE_EFFECT)) {
					return false;
				}
			}
		} else {
			LiveInNode liveInNode = (LiveInNode) node;
			Set<SESEEffectsKey> writeEffectSet = liveInNode
					.getWriteEffectsSet();
			if (writeEffectSet != null && writeEffectSet.size() > 0) {
				return false;
			}
		}

		return true;
	}

	public void analyzeConflicts() {

		Set<String> keySet = id2cn.keySet();
		Set<String> analyzedIDSet = new HashSet<String>();

		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			String nodeID = (String) iterator.next();
			ConflictNode node = id2cn.get(nodeID);
			analyzePossibleConflicts(analyzedIDSet, node);
		}
	}

	private boolean isWriteConflicts(StallSiteNode nodeA, LiveInNode nodeB) {

		boolean result = false;
		StallSite stallSite = nodeA.getStallSite();

		Set<SESEEffectsKey> writeEffectsSet = nodeB.getWriteEffectsSet();
		Set<SESEEffectsKey> readEffectsSet = nodeB.getReadEffectsSet();

		if (writeEffectsSet != null) {
			Iterator<SESEEffectsKey> writeIter = writeEffectsSet.iterator();
			while (writeIter.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) writeIter
						.next();
				String writeHeapRegionID = seseEffectsKey.getHRNUniqueId();
				String writeFieldName = seseEffectsKey.getFieldDescriptor();
				
				if(writeFieldName.length()>0){
					HashSet<HeapRegionNode> stallSiteHRNSet = nodeA.getHRNSet();
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
				}else{
					// no field name
					
					HashSet<Effect> effectSet = stallSite.getEffectSet();
					for (Iterator iterator2 = effectSet.iterator(); iterator2
							.hasNext();) {
						Effect effect = (Effect) iterator2.next();
						String stallEffectfieldName = effect.getField();

						if (stallEffectfieldName.length()==0 && nodeB.getTempDescriptor().equals(nodeA.getStallSite().getTdA())) {
							result = result | true;
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

				if(readFieldName.length()>0){
					HashSet<HeapRegionNode> stallSiteHRNSet = nodeA.getHRNSet();
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
				}else{
					//no field
					HashSet<Effect> effectSet = stallSite.getEffectSet();
					for (Iterator iterator2 = effectSet.iterator(); iterator2
							.hasNext();) {
						Effect effect = (Effect) iterator2.next();
						String stallEffectfieldName = effect.getField();

						if (effect.getEffectType().equals(
								StallSite.WRITE_EFFECT)) {
							if (stallEffectfieldName.length()==0 && nodeB.getTempDescriptor().equals(nodeA.getStallSite().getTdA())) {
								result = result | true;
							}
						}

					}
				}


			}

		}

		return result;
	}

	private int determineWriteConflictsType(LiveInNode liveInNodeA,
			LiveInNode liveInNodeB) {

		Set<HeapRegionNode> liveInHrnSetA = liveInNodeA.getHRNSet();
		Set<HeapRegionNode> liveInHrnSetB = liveInNodeB.getHRNSet();

		boolean isPointingToSameRegion = compareHRNSet(liveInHrnSetA,
				liveInHrnSetB);

		boolean isSharingReachability = false;

		Set<Set> liveInNodeReachabilitySetA = liveInNodeA.getReachabilitySet();
		Set<Set> liveInNodeReachabilitySetB = liveInNodeB.getReachabilitySet();

		Set<GloballyUniqueTokenTuple> overlappedReachableRegionSet = calculateOverlappedReachableRegion(
				liveInNodeReachabilitySetA, liveInNodeReachabilitySetB);
		if (overlappedReachableRegionSet.size() > 0) {
			isSharingReachability = true;
		}

		if (isPointingToSameRegion && isSharingReachability) {
			// two node share same reachability and points to same region, then
			// it is fine grain conflicts
			return ConflictEdge.FINE_GRAIN_EDGE;
		} else if (isSharingReachability) {
			// two node share same reachability but points to different region,
			// then it is coarse grain conflicts
			return ConflictEdge.COARSE_GRAIN_EDGE;
		} else {
			// otherwise, it is not write conflicts
			return ConflictEdge.NON_WRITE_CONFLICT;
		}

	}

	private boolean compareHRNSet(Set<HeapRegionNode> setA,
			Set<HeapRegionNode> setB) {

		for (Iterator iterator = setA.iterator(); iterator.hasNext();) {
			HeapRegionNode heapRegionNode = (HeapRegionNode) iterator.next();
			String gID = heapRegionNode.getGloballyUniqueIdentifier();
			boolean found = false;
			for (Iterator iterator2 = setB.iterator(); iterator2.hasNext();) {
				HeapRegionNode heapRegionNode2 = (HeapRegionNode) iterator2
						.next();
				if (heapRegionNode2.getGloballyUniqueIdentifier().equals(gID)) {
					found = true;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private int determineWriteConflictsType(StallSiteNode stallNode,
			LiveInNode liveInNode) {

		Set<HeapRegionNode> stallHrnSet = stallNode.getHRNSet();
		Set<HeapRegionNode> liveInHrnSet = liveInNode.getHRNSet();

		boolean isPointingToSameRegion = compareHRNSet(stallHrnSet,
				liveInHrnSet);

		boolean isSharingReachability = false;

		Set<Set> stallNodeReachabilitySet = stallNode.getReachabilitySet();
		Set<Set> liveInNodeReachabilitySet = liveInNode.getReachabilitySet();

		Set<GloballyUniqueTokenTuple> overlappedReachableRegionSet = calculateOverlappedReachableRegion(
				stallNodeReachabilitySet, liveInNodeReachabilitySet);
		if (overlappedReachableRegionSet.size() > 0) {
			isSharingReachability = true;
		}

		if (isPointingToSameRegion && isSharingReachability) {
			// two node share same reachability and points to same region, then
			// it is fine grain conflicts
			return ConflictEdge.FINE_GRAIN_EDGE;
		} else if (isSharingReachability) {
			// two node share same reachability but points to different region,
			// then it is coarse grain conflicts
			return ConflictEdge.COARSE_GRAIN_EDGE;
		} else {
			// otherwise, it is not write conflicts
			return ConflictEdge.NON_WRITE_CONFLICT;
		}

	}

	private boolean hasStrongUpdate(SESEEffectsKey writeEffect,
			Set<SESEEffectsKey> strongUpdateSet) {
		
		if(strongUpdateSet!=null){
			Iterator<SESEEffectsKey> strongUpdateIter = strongUpdateSet.iterator();
			while (strongUpdateIter.hasNext()) {
				SESEEffectsKey strongEffect = (SESEEffectsKey) strongUpdateIter
						.next();

				if (strongEffect.getHRNUniqueId().equals(
						writeEffect.getHRNUniqueId())
						&& strongEffect.getFieldDescriptor().equals(
								writeEffect.getFieldDescriptor())) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean isWriteConflicts(LiveInNode nodeA, LiveInNode nodeB) {

		Set<SESEEffectsKey> readEffectsSetA = nodeA.getReadEffectsSet();
		Set<SESEEffectsKey> writeEffectsSetA = nodeA.getWriteEffectsSet();
		Set<SESEEffectsKey> strongUpdateSetA = nodeA.getStrongUpdateSet();

		Set<SESEEffectsKey> readEffectsSetB = nodeB.getReadEffectsSet();
		Set<SESEEffectsKey> writeEffectsSetB = nodeB.getWriteEffectsSet();
		Set<SESEEffectsKey> strongUpdateSetB = nodeB.getStrongUpdateSet();
		/*
		System.out.println("nodeA="+nodeA);
		System.out.println("readEffectsSetA="+readEffectsSetA);
		System.out.println("writeEffectsSetA="+writeEffectsSetA);
		System.out.println("strongUpdateSetA="+strongUpdateSetA);
		System.out.println("nodeB="+nodeB);
		System.out.println("readEffectsSetB="+readEffectsSetB);
		System.out.println("writeEffectsSetB="+writeEffectsSetB);
		System.out.println("strongUpdateSetB="+strongUpdateSetB);
		System.out.println("--");
		*/
		
		// if node A has write effects on reading/writing regions of node B
		if (writeEffectsSetA != null) {
			Iterator<SESEEffectsKey> writeIterA = writeEffectsSetA.iterator();
			while (writeIterA.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) writeIterA
						.next();

//				if (!hasStrongUpdate(seseEffectsKey, strongUpdateSetA)) {

					String writeHeapRegionID = seseEffectsKey.getHRNUniqueId();
					String writeFieldName = seseEffectsKey.getFieldDescriptor();

					if (readEffectsSetB != null) {
						Iterator<SESEEffectsKey> readIterB = readEffectsSetB
								.iterator();
						while (readIterB.hasNext()) {
							SESEEffectsKey readingEffect = (SESEEffectsKey) readIterB
									.next();

							if (readingEffect.getHRNUniqueId().equals(
									writeHeapRegionID)
									&& readingEffect.getFieldDescriptor()
											.equals(writeFieldName)) {
								return true;
							}
						}
					}

					if (writeEffectsSetB != null) {
						Iterator<SESEEffectsKey> writeIterB = writeEffectsSetB
								.iterator();
						while (writeIterB.hasNext()) {
							SESEEffectsKey writingEffect = (SESEEffectsKey) writeIterB
									.next();

							if (writingEffect.getHRNUniqueId().equals(
									writeHeapRegionID)
									&& writingEffect.getFieldDescriptor()
											.equals(writeFieldName)) {
								return true;
							}
						}
					}

//				} // end of if(hasStrong)

			}
		}

		// if node B has write effects on reading regions of node A
		if (writeEffectsSetB != null) {
			Iterator<SESEEffectsKey> writeIterB = writeEffectsSetB.iterator();
			while (writeIterB.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) writeIterB
						.next();

				//if (!hasStrongUpdate(seseEffectsKey, strongUpdateSetB)) {

					String writeHeapRegionID = seseEffectsKey.getHRNUniqueId();
					String writeFieldName = seseEffectsKey.getFieldDescriptor();

					if (readEffectsSetA != null) {
						Iterator<SESEEffectsKey> readIterA = readEffectsSetA
								.iterator();
						while (readIterA.hasNext()) {
							SESEEffectsKey readingEffect = (SESEEffectsKey) readIterA
									.next();
							if (readingEffect.getHRNUniqueId().equals(
									writeHeapRegionID)
									&& readingEffect.getFieldDescriptor()
											.equals(writeFieldName)) {
								return true;
							}
						}
					}

					if (writeEffectsSetA != null) {
						Iterator<SESEEffectsKey> writeIterA = writeEffectsSetA
								.iterator();
						while (writeIterA.hasNext()) {
							SESEEffectsKey writingEffect = (SESEEffectsKey) writeIterA
									.next();
							if (writingEffect.getHRNUniqueId().equals(
									writeHeapRegionID)
									&& writingEffect.getFieldDescriptor()
											.equals(writeFieldName)) {
								return true;
							}
						}
					}
				//} // if(hasStrong)

			}
		}
		return false;
	}

	private boolean isSelfConflicted(LiveInNode liveInNode) {

		int strongUpdateCount = 0;

		if (liveInNode.getWriteEffectsSet() != null
				&& liveInNode.getWriteEffectsSet().size() > 0) {

			Set<SESEEffectsKey> strongUpdateSet = liveInNode
					.getStrongUpdateSet();

			Iterator<SESEEffectsKey> writeIter = liveInNode
					.getWriteEffectsSet().iterator();
			while (writeIter.hasNext()) {
				SESEEffectsKey writeEffect = (SESEEffectsKey) writeIter.next();
				if (hasStrongUpdate(writeEffect, strongUpdateSet)) {
					strongUpdateCount++;
				}
				
				if(writeEffect.isStrong()){
					return false;
				}
			}


			if (liveInNode.getWriteEffectsSet().size() == strongUpdateCount) {
				return false;
			}else{
				return true;
			}

		}

		return false;
	}

	public void analyzePossibleConflicts(Set<String> analyzedIDSet,
			ConflictNode currentNode) {

		// compare with all nodes

		// examine the case where self-edge exists
		if (currentNode instanceof LiveInNode) {
			LiveInNode liveInNode = (LiveInNode) currentNode;
//			if (liveInNode.getWriteEffectsSet() != null
//					&& liveInNode.getWriteEffectsSet().size() > 0) {
//				addConflictEdge(ConflictEdge.FINE_GRAIN_EDGE, currentNode,
//						currentNode);
//			}
			if(isSelfConflicted(liveInNode)){				
				addConflictEdge(ConflictEdge.FINE_GRAIN_EDGE, currentNode,
						currentNode);
			}
			
		}

		Set<Entry<String, ConflictNode>> set = id2cn.entrySet();
		for (Iterator iterator = set.iterator(); iterator.hasNext();) {
			Entry<String, ConflictNode> entry = (Entry<String, ConflictNode>) iterator
					.next();

			String entryNodeID = entry.getKey();
			ConflictNode entryNode = entry.getValue();

			if ((!currentNode.getID().equals(entryNodeID))
					&& !(analyzedIDSet.contains(currentNode.getID()
							+ entryNodeID) || analyzedIDSet
							.contains(entryNodeID + currentNode.getID()))) {

				if (currentNode instanceof StallSiteNode
						&& entryNode instanceof LiveInNode) {
					if (isWriteConflicts((StallSiteNode) currentNode,
							(LiveInNode) entryNode)) {
						int conflictType = determineWriteConflictsType(
								(StallSiteNode) currentNode,
								(LiveInNode) entryNode);
						if (conflictType > 0) {
							addConflictEdge(conflictType, currentNode,
									entryNode);
						}
					}
					analyzedIDSet.add(currentNode.getID() + entryNodeID);

				} else if (currentNode instanceof LiveInNode
						&& entryNode instanceof LiveInNode) {
					if (isWriteConflicts((LiveInNode) currentNode,
							(LiveInNode) entryNode)) {

						int conflictType = determineWriteConflictsType(
								(LiveInNode) currentNode,
								(LiveInNode) entryNode);
						if (conflictType > 0) {
							addConflictEdge(conflictType, currentNode,
									entryNode);
						}

					}
					analyzedIDSet.add(currentNode.getID() + entryNodeID);
				}

			}

		}

	}

	public boolean containsTokenTuple(Set<GloballyUniqueTokenTuple> overlapSet,
			String uniqueID) {

		for (Iterator iterator = overlapSet.iterator(); iterator.hasNext();) {
			GloballyUniqueTokenTuple globallyUniqueTokenTuple = (GloballyUniqueTokenTuple) iterator
					.next();
			if (globallyUniqueTokenTuple.getID().equals(uniqueID)) {
				return true;
			}
		}

		return false;

	}

	private Set<GloballyUniqueTokenTuple> calculateOverlappedReachableRegion(
			Set<Set> setA, Set<Set> setB) {

		Set<GloballyUniqueTokenTuple> returnSet = new HashSet<GloballyUniqueTokenTuple>();

		for (Iterator iterator2 = setA.iterator(); iterator2.hasNext();) {
			Set tokenTupleSetA = (Set) iterator2.next();

			for (Iterator iterator3 = setB.iterator(); iterator3.hasNext();) {
				Set tokenTupleSetB = (Set) iterator3.next();

				if (tokenTupleSetA.equals(tokenTupleSetB)) {
					// reachability states are overlapped
					Iterator ttsIter = tokenTupleSetA.iterator();
					while (ttsIter.hasNext()) {
						GloballyUniqueTokenTuple tt = (GloballyUniqueTokenTuple) ttsIter
								.next();
						returnSet.add(tt);
					}
				}
			}
		}
		return returnSet;
	}

	public String addStallNode(TempDescriptor td, FlatMethod fm,
			StallSite stallSite, Set<Set> reachabilitySet) {

		String stallNodeID = td + "_" + fm.getMethod().getSymbol();

		if (!id2cn.containsKey(stallNodeID)) {
			StallSiteNode newNode = new StallSiteNode(stallNodeID, td,
					stallSite, reachabilitySet);
			id2cn.put(stallNodeID, newNode);
			// it add new new stall node to conflict graph
			return stallNodeID;
		}
		// it doesn't add new stall node because stall node has already been
		// added.
		return null;
	}

	public StallSiteNode getStallNode(String stallNodeID) {
		ConflictNode node = id2cn.get(stallNodeID);
		if (node instanceof StallSiteNode) {
			return (StallSiteNode) node;
		} else {
			return null;
		}
	}

	public void addLiveInNode(TempDescriptor td, Set<HeapRegionNode> hrnSet,
			FlatSESEEnterNode fsen, Set<SESEEffectsKey> readEffectsSet,
			Set<SESEEffectsKey> writeEffectsSet,
			Set<SESEEffectsKey> strongUpdateSet, Set<Set> reachabilitySet) {

		String liveinNodeID = td + "_" + fsen.getIdentifier();

		LiveInNode newNode = new LiveInNode(liveinNodeID, td, hrnSet,
				readEffectsSet, writeEffectsSet, strongUpdateSet,
				reachabilitySet, fsen.getIdentifier());
		id2cn.put(liveinNodeID, newNode);

	}

	public void addConflictEdge(int type, ConflictNode nodeU, ConflictNode nodeV) {

		ConflictEdge newEdge = new ConflictEdge(nodeU, nodeV, type);
		nodeU.addEdge(newEdge);
		nodeV.addEdge(newEdge);

	}

	public HashSet<LiveInNode> getLiveInNodeSet() {
		HashSet<LiveInNode> resultSet = new HashSet<LiveInNode>();

		Set<String> keySet = id2cn.keySet();
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			ConflictNode node = id2cn.get(key);

			if (node instanceof LiveInNode) {
				resultSet.add((LiveInNode) node);
			}
		}

		return resultSet;
	}

	public Set<WaitingElement> getStallSiteWaitingElementSet(
			ParentChildConflictsMap conflictsMap, HashSet<SESELock> seseLockSet) {

		HashSet<WaitingElement> waitingElementSet = new HashSet<WaitingElement>();
		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Collection<StallSite> stallSites = conflictsMap.getStallMap().values();

		for (Iterator iterator = stallSites.iterator(); iterator.hasNext();) {

			StallSite stallSite = (StallSite) iterator.next();
			Iterator<Entry<String, ConflictNode>> i = s.iterator();
			while (i.hasNext()) {
				Entry<String, ConflictNode> entry = i.next();
				ConflictNode node = entry.getValue();

				if (node instanceof StallSiteNode) {
					StallSiteNode stallSiteNode = (StallSiteNode) node;
					if (stallSiteNode.getStallSite().equals(stallSite)) {
						HashSet<ConflictEdge> edgeSet = stallSiteNode
								.getEdgeSet();
						for (Iterator iter2 = edgeSet.iterator(); iter2
								.hasNext();) {
							ConflictEdge conflictEdge = (ConflictEdge) iter2
									.next();

							int type = -1;
							HashSet<Integer> allocSet = new HashSet<Integer>();

							if (conflictEdge.getType() == ConflictEdge.COARSE_GRAIN_EDGE) {
								if (isReadOnly(node)) {
									type = 2; // coarse read
								} else {
									type = 3; // coarse write
								}

								allocSet.addAll(getAllocSet(conflictEdge
										.getVertexU()));
								allocSet.addAll(getAllocSet(conflictEdge
										.getVertexV()));

							} else if (conflictEdge.getType() == ConflictEdge.FINE_GRAIN_EDGE) {// it
																								// is
																								// fine-grain
																								// edge
								allocSet.addAll(getAllocSet(node));
								if (isReadOnly(node)) {
									// fine-grain read
									type = 0;
								} else {
									// fine-grain write
									type = 1;
								}
							}

							if (type > -1) {
								for (Iterator<SESELock> seseLockIter = seseLockSet
										.iterator(); seseLockIter.hasNext();) {
									SESELock seseLock = seseLockIter.next();
									if (seseLock
											.containsConflictNode(stallSiteNode)) {
										WaitingElement newElement = new WaitingElement();
										newElement.setAllocList(allocSet);
										newElement.setWaitingID(seseLock
												.getID());
										newElement.setStatus(type);
										waitingElementSet.add(newElement);
									}
								}
							}

						}
					}
				}
			}

		}

		return waitingElementSet;
	}

	public Set<Integer> getConnectedConflictNodeSet(
			ParentChildConflictsMap conflictsMap) {

		HashSet<Integer> nodeIDSet = new HashSet<Integer>();

		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();

		Collection<StallSite> stallSites = conflictsMap.getStallMap().values();
		for (Iterator iterator = stallSites.iterator(); iterator.hasNext();) {
			StallSite stallSite = (StallSite) iterator.next();
			Iterator<Entry<String, ConflictNode>> i = s.iterator();
			while (i.hasNext()) {
				Entry<String, ConflictNode> entry = i.next();
				ConflictNode node = entry.getValue();

				if (node instanceof StallSiteNode) {
					StallSiteNode stallSiteNode = (StallSiteNode) node;
					if (stallSiteNode.getStallSite().equals(stallSite)) {
						HashSet<ConflictEdge> edgeSet = stallSiteNode
								.getEdgeSet();
						for (Iterator iter2 = edgeSet.iterator(); iter2
								.hasNext();) {
							ConflictEdge conflictEdge = (ConflictEdge) iter2
									.next();
							nodeIDSet
									.addAll(getConnectedConflictNode(conflictEdge));
						}
					}
				}
			}
		}

		return nodeIDSet;

	}

	private Set<Integer> getConnectedConflictNode(ConflictEdge conflictEdge) {

		HashSet<Integer> nodeIDSet = new HashSet<Integer>();

		if (conflictEdge.getVertexU() instanceof LiveInNode) {
			LiveInNode lin = (LiveInNode) conflictEdge.getVertexU();
			nodeIDSet.add(new Integer(lin.getSESEIdentifier()));
		}
		if (conflictEdge.getVertexV() instanceof LiveInNode) {
			LiveInNode lin = (LiveInNode) conflictEdge.getVertexV();
			nodeIDSet.add(new Integer(lin.getSESEIdentifier()));
		}

		return nodeIDSet;
	}

	private Set<Integer> getConnectedConflictNode(ConflictEdge conflictEdge,
			int seseID) {

		HashSet<Integer> nodeIDSet = new HashSet<Integer>();

		if (conflictEdge.getVertexU() instanceof LiveInNode) {
			LiveInNode lin = (LiveInNode) conflictEdge.getVertexU();
			if (lin.getSESEIdentifier() != seseID) {
				nodeIDSet.add(new Integer(lin.getSESEIdentifier()));
			}
		} else {
			// it is stall site
			nodeIDSet.add(new Integer(-1));
		}
		if (conflictEdge.getVertexV() instanceof LiveInNode) {
			LiveInNode lin = (LiveInNode) conflictEdge.getVertexV();
			if (lin.getSESEIdentifier() != seseID) {
				nodeIDSet.add(new Integer(lin.getSESEIdentifier()));
			}
		} else {
			// it is stall site
			nodeIDSet.add(new Integer(-1));
		}

		// self-edge case
		if (conflictEdge.getVertexU() instanceof LiveInNode
				&& conflictEdge.getVertexV() instanceof LiveInNode) {
			if (((LiveInNode) conflictEdge.getVertexU()).getSESEIdentifier() == seseID
					&& ((LiveInNode) conflictEdge.getVertexV())
							.getSESEIdentifier() == seseID) {
				nodeIDSet.add(seseID);
			}
		}

		return nodeIDSet;
	}

	public Set<Integer> getConnectedConflictNodeSet(int seseID) {

		HashSet<Integer> nodeIDSet = new HashSet<Integer>();

		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();

		while (i.hasNext()) {
			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();

			if (node instanceof LiveInNode) {
				LiveInNode liveInNode = (LiveInNode) node;
				if (liveInNode.getSESEIdentifier() == seseID) {
					HashSet<ConflictEdge> edgeSet = liveInNode.getEdgeSet();
					for (Iterator iterator = edgeSet.iterator(); iterator
							.hasNext();) {
						ConflictEdge conflictEdge = (ConflictEdge) iterator
								.next();
						//
						nodeIDSet.addAll(getConnectedConflictNode(conflictEdge,
								seseID));
						//
					}
				}
			}
		}

		return nodeIDSet;

	}

	public Set<WaitingElement> getWaitingElementSetBySESEID(int seseID,
			HashSet<SESELock> seseLockSet) {

		HashSet<WaitingElement> waitingElementSet = new HashSet<WaitingElement>();

		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();

		while (i.hasNext()) {
			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();

			if (node instanceof LiveInNode) {
				LiveInNode liveInNode = (LiveInNode) node;
				if (liveInNode.getSESEIdentifier() == seseID) {

					HashSet<ConflictEdge> edgeSet = liveInNode.getEdgeSet();

					for (Iterator iterator = edgeSet.iterator(); iterator
							.hasNext();) {
						ConflictEdge conflictEdge = (ConflictEdge) iterator
								.next();
						int type = -1;
						HashSet<Integer> allocSet = new HashSet<Integer>();

						if (conflictEdge.getType() == ConflictEdge.COARSE_GRAIN_EDGE) {
							if (isReadOnly(node)) {
								type = 2; // coarse read
							} else {
								type = 3; // coarse write
							}

							allocSet.addAll(getAllocSet(conflictEdge
									.getVertexU()));
							allocSet.addAll(getAllocSet(conflictEdge
									.getVertexV()));

						} else if (conflictEdge.getType() == ConflictEdge.FINE_GRAIN_EDGE) {// it
																							// is
																							// fine-grain
																							// edge
							allocSet.addAll(getAllocSet(node));
							if (isReadOnly(node)) {
								// fine-grain read
								type = 0;
							} else {
								// fine-grain write
								type = 1;
							}
						}

						if (type > -1) {

							for (Iterator<SESELock> seseLockIter = seseLockSet
									.iterator(); seseLockIter.hasNext();) {
								SESELock seseLock = seseLockIter.next();
								if (seseLock.containsConflictNode(liveInNode)) {
									WaitingElement newElement = new WaitingElement();
									newElement.setAllocList(allocSet);
									newElement.setWaitingID(seseLock.getID());
									newElement.setStatus(type);
									if(!waitingElementSet.contains(newElement)){
										waitingElementSet.add(newElement);
									}else{
									}
									
									
								}
							}
						}
					}

				}
			}

		}

		return waitingElementSet;
	}

	public Set<Long> getAllocationSiteIDSetBySESEID(int seseID) {
		// deprecated
		HashSet<Long> allocSiteIDSet = new HashSet<Long>();

		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();

		while (i.hasNext()) {
			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();

			if (node instanceof LiveInNode) {
				LiveInNode liveInNode = (LiveInNode) node;
				if (liveInNode.getSESEIdentifier() == seseID) {
					HashSet<ConflictEdge> edgeSet = liveInNode.getEdgeSet();
					for (Iterator iterator = edgeSet.iterator(); iterator
							.hasNext();) {
						ConflictEdge conflictEdge = (ConflictEdge) iterator
								.next();
						//
						getConnectedConflictNode(conflictEdge, seseID);
						//
						if (conflictEdge.getType() == ConflictEdge.COARSE_GRAIN_EDGE) {
							allocSiteIDSet
									.addAll(getHRNIdentifierSet(conflictEdge
											.getVertexU()));
							allocSiteIDSet
									.addAll(getHRNIdentifierSet(conflictEdge
											.getVertexV()));
						} else {// it is fine-grain edge
							allocSiteIDSet.addAll(getHRNIdentifierSet(node));
						}
					}

				}
			}
		}

		return allocSiteIDSet;

	}

	public Set<Long> getAllocationSiteIDSetofStallSite() {

		HashSet<Long> allocSiteIDSet = new HashSet<Long>();

		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();

		while (i.hasNext()) {

			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();

			if (node instanceof StallSiteNode) {
				allocSiteIDSet.addAll(getHRNIdentifierSet(node));
			}

		}

		return allocSiteIDSet;

	}

	public Set<Long> getAllocationSiteIDSet() {

		HashSet<Long> allocSiteIDSet = new HashSet<Long>();

		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();

		while (i.hasNext()) {
			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();

			HashSet<ConflictEdge> edgeSet = node.getEdgeSet();
			for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
				ConflictEdge conflictEdge = (ConflictEdge) iterator.next();
				if (conflictEdge.getType() == ConflictEdge.COARSE_GRAIN_EDGE) {
					allocSiteIDSet.addAll(getHRNIdentifierSet(conflictEdge
							.getVertexU()));
					allocSiteIDSet.addAll(getHRNIdentifierSet(conflictEdge
							.getVertexV()));
				} else {// it is fine-grain edge
					allocSiteIDSet.addAll(getHRNIdentifierSet(node));
				}
			}

		}

		return allocSiteIDSet;

	}

	private HashSet<Integer> getAllocSet(ConflictNode node) {

		HashSet<Integer> returnSet = new HashSet<Integer>();

		if (node instanceof StallSiteNode) {
			StallSiteNode stallSiteNode = (StallSiteNode) node;
			Set<HeapRegionNode> hrnSet = stallSiteNode.getHRNSet();
			for (Iterator iterator = hrnSet.iterator(); iterator.hasNext();) {
				HeapRegionNode hrn = (HeapRegionNode) iterator.next();
				// allocSiteIDSet.add(hrn.getGloballyUniqueIdentifier());
				if (hrn.getAllocationSite() != null) {
					returnSet.add(new Integer(hrn.getAllocationSite().getID()));
				}
			}
		} else {
			LiveInNode liveInNode = (LiveInNode) node;
			Set<HeapRegionNode> hrnSet = liveInNode.getHRNSet();
			for (Iterator iterator = hrnSet.iterator(); iterator.hasNext();) {
				HeapRegionNode hrn = (HeapRegionNode) iterator.next();
				// allocSiteIDSet.add(hrn.getGloballyUniqueIdentifier());
				if (hrn.getAllocationSite() != null) {
					returnSet.add(new Integer(hrn.getAllocationSite().getID()));
				}else{
					returnSet.add(new Integer(hrn.getID()));
				}
			}
		}

		return returnSet;
	}

	private HashSet<Long> getHRNIdentifierSet(ConflictNode node) {

		HashSet<Long> returnSet = new HashSet<Long>();

		if (node instanceof StallSiteNode) {
			StallSiteNode stallSiteNode = (StallSiteNode) node;
			Set<HeapRegionNode> hrnSet = stallSiteNode.getHRNSet();
			for (Iterator iterator = hrnSet.iterator(); iterator.hasNext();) {
				HeapRegionNode hrn = (HeapRegionNode) iterator.next();
				// allocSiteIDSet.add(hrn.getGloballyUniqueIdentifier());
				returnSet
						.add(new Long(hrn.getGloballyUniqueIntegerIdentifier()));
			}
		} else {
			LiveInNode liveInNode = (LiveInNode) node;
			Set<HeapRegionNode> hrnSet = liveInNode.getHRNSet();
			for (Iterator iterator = hrnSet.iterator(); iterator.hasNext();) {
				HeapRegionNode hrn = (HeapRegionNode) iterator.next();
				// allocSiteIDSet.add(hrn.getGloballyUniqueIdentifier());
				returnSet
						.add(new Long(hrn.getGloballyUniqueIntegerIdentifier()));
			}
		}

		return returnSet;

	}

	public HashSet<ConflictEdge> getEdgeSet() {

		HashSet<ConflictEdge> returnSet = new HashSet<ConflictEdge>();

		Collection<ConflictNode> nodes = id2cn.values();
		for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
			ConflictNode conflictNode = (ConflictNode) iterator.next();
			returnSet.addAll(conflictNode.getEdgeSet());
		}

		return returnSet;
	}

	static public int generateUniqueCliqueID() {
		++uniqueCliqueIDcount;
		return uniqueCliqueIDcount;
	}

	public void writeGraph(String graphName, boolean filter)
			throws java.io.IOException {

		graphName = graphName.replaceAll("[\\W]", "");

		BufferedWriter bw = new BufferedWriter(new FileWriter(graphName
				+ ".dot"));
		bw.write("graph " + graphName + " {\n");

		HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();
		// then visit every heap region node
		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();

		HashSet<ConflictEdge> addedSet = new HashSet<ConflictEdge>();

		while (i.hasNext()) {
			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();

			if (filter) {
				if (node.getID().startsWith("___dst")
						|| node.getID().startsWith("___srctmp")
						|| node.getID().startsWith("___neverused")
						|| node.getID().startsWith("___temp")) {

					continue;
				}
			}

			String attributes = "[";

			attributes += "label=\"ID" + node.getID() + "\\n";

			if (node instanceof StallSiteNode) {
				attributes += "STALL SITE" + "\\n" + "\"]";
			} else {
				attributes += "LIVE-IN" + "\\n" + "\"]";
			}
			bw.write(entry.getKey() + attributes + ";\n");

			HashSet<ConflictEdge> edgeSet = node.getEdgeSet();
			for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
				ConflictEdge conflictEdge = (ConflictEdge) iterator.next();

				ConflictNode u = conflictEdge.getVertexU();
				ConflictNode v = conflictEdge.getVertexV();

				if (filter) {
					String uID = u.getID();
					String vID = v.getID();
					if (uID.startsWith("___dst") || uID.startsWith("___srctmp")
							|| uID.startsWith("___neverused")
							|| uID.startsWith("___temp")
							|| vID.startsWith("___dst")
							|| vID.startsWith("___srctmp")
							|| vID.startsWith("___neverused")
							|| vID.startsWith("___temp")) {
						continue;
					}
				}

				if (!addedSet.contains(conflictEdge)) {
					bw.write(" " + u.getID() + "--" + v.getID() + "[label="
							+ conflictEdge.toGraphEdgeString()
							+ ",decorate];\n");
					addedSet.add(conflictEdge);
				}

			}
		}

		bw.write("  graphTitle[label=\"" + graphName + "\",shape=box];\n");

		bw.write("}\n");
		bw.close();

	}

}

class ConflictEdge {

	private ConflictNode u;
	private ConflictNode v;
	private int type;

	public static final int NON_WRITE_CONFLICT = 0;
	public static final int FINE_GRAIN_EDGE = 1;
	public static final int COARSE_GRAIN_EDGE = 2;
	public static final int PSEUDO_EDGE = 3;

	public ConflictEdge(ConflictNode u, ConflictNode v, int type) {
		this.u = u;
		this.v = v;
		this.type = type;
	}

	public String toGraphEdgeString() {
		if (type == FINE_GRAIN_EDGE) {
			return "\"F_CONFLICT\"";
		} else if (type == COARSE_GRAIN_EDGE) {
			return "\"C_CONFLICT\"";
		} else if (type == PSEUDO_EDGE) {
			return "\"P_CONFLICT\", style=dotted";
		} else {
			return "CONFLICT\"";
		}
	}

	public ConflictNode getVertexU() {
		return u;
	}

	public ConflictNode getVertexV() {
		return v;
	}

	public int getType() {
		return type;
	}

}