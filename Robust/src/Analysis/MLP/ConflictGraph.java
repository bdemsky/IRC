package Analysis.MLP;

import java.io.BufferedWriter;
import java.io.FileWriter;
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

	public Hashtable<String, ConflictNode> id2cn;

	public ConflictGraph() {
		id2cn = new Hashtable<String, ConflictNode>();
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

			}
		}

		if (readEffectsSet != null) {
			Iterator<SESEEffectsKey> readIter = readEffectsSet.iterator();
			while (readIter.hasNext()) {

				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) readIter
						.next();
				String readHeapRegionID = seseEffectsKey.getHRNUniqueId();
				String readFieldName = seseEffectsKey.getFieldDescriptor();

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

	private boolean isWriteConflicts(LiveInNode nodeA, LiveInNode nodeB) {

		Set<SESEEffectsKey> readEffectsSetA = nodeA.getReadEffectsSet();
		Set<SESEEffectsKey> writeEffectsSetA = nodeA.getWriteEffectsSet();
		Set<SESEEffectsKey> readEffectsSetB = nodeB.getReadEffectsSet();
		Set<SESEEffectsKey> writeEffectsSetB = nodeB.getWriteEffectsSet();

		// if node A has write effects on reading/writing regions of node B
		if (writeEffectsSetA != null) {
			Iterator<SESEEffectsKey> writeIterA = writeEffectsSetA.iterator();
			while (writeIterA.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) writeIterA
						.next();
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
								&& readingEffect.getFieldDescriptor().equals(
										writeFieldName)) {
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
								&& writingEffect.getFieldDescriptor().equals(
										writeFieldName)) {
							return true;
						}
					}
				}

			}
		}

		// if node B has write effects on reading regions of node A
		if (writeEffectsSetB != null) {
			Iterator<SESEEffectsKey> writeIterB = writeEffectsSetB.iterator();
			while (writeIterB.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) writeIterB
						.next();
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
								&& readingEffect.getFieldDescriptor().equals(
										writeFieldName)) {
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
								&& writingEffect.getFieldDescriptor().equals(
										writeFieldName)) {
							return true;
						}
					}
				}

			}
		}
		return false;
	}

	public void analyzePossibleConflicts(Set<String> analyzedIDSet,
			ConflictNode currentNode) {

		// compare with all nodes

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
			Set<SESEEffectsKey> writeEffectsSet, Set<Set> reachabilitySet) {

		String liveinNodeID = td + "_" + fsen.getIdentifier();

		LiveInNode newNode = new LiveInNode(liveinNodeID, td, hrnSet,
				readEffectsSet, writeEffectsSet, reachabilitySet);
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
					bw.write(" " + u.getID() + "--" + v.getID() + "[label=\""
							+ conflictEdge.toGraphEdgeString()
							+ "\",decorate];\n");
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

	public ConflictEdge(ConflictNode u, ConflictNode v, int type) {
		this.u = u;
		this.v = v;
		this.type = type;
	}

	public String toGraphEdgeString() {
		if (type == FINE_GRAIN_EDGE) {
			return "F_CONFLICT";
		} else {
			return "C_CONFLICT";
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