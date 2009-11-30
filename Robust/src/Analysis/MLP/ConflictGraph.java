package Analysis.MLP;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import Analysis.OwnershipAnalysis.HeapRegionNode;
import Analysis.OwnershipAnalysis.TokenTuple;
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
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			String nodeID = (String) iterator.next();
			ConflictNode node = id2cn.get(nodeID);
			analyzePossibleConflicts(node);
		}
	}

	public void analyzePossibleConflicts(ConflictNode node) {

		// compare with all nodes

		Set<Set> nodeReachabilitySet = node.getReachabilitySet();

		Set<Entry<String, ConflictNode>> set = id2cn.entrySet();
		for (Iterator iterator = set.iterator(); iterator.hasNext();) {
			Entry<String, ConflictNode> entry = (Entry<String, ConflictNode>) iterator
					.next();

			String currentNodeID = entry.getKey();
			ConflictNode currentNode = entry.getValue();

			if ((node instanceof StallSiteNode)
					&& (currentNode instanceof StallSiteNode)) {
				continue;
			}

			if (currentNodeID.equals(node.getID())) {
				continue;
			}

			Set<Set> currentNodeReachabilitySet = currentNode
					.getReachabilitySet();

			Set<GloballyUniqueTokenTuple> overlapSet = calculateOverlappedReachableRegion(
					nodeReachabilitySet, currentNodeReachabilitySet);
			if (overlapSet.size() > 0) {

				// System.out.println("OVERLAPPED=" + overlapSet);

				if (node instanceof StallSiteNode
						&& currentNode instanceof LiveInNode) {
					int edgeType = decideConflictEdgeType(overlapSet,
							(StallSiteNode) node, (LiveInNode) currentNode);
					addConflictEdge(edgeType, node, currentNode);
				} else if (node instanceof LiveInNode
						&& currentNode instanceof LiveInNode) {
					int edgeType = decideConflictEdgeType(overlapSet,
							(LiveInNode) node, (LiveInNode) currentNode);
					addConflictEdge(edgeType, node, currentNode);
				}

			} else {
				// System.out.println("DOSE NOT OVERLAPPED " + node + " <-> "
				// + currentNode);
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

	private int decideConflictEdgeType(
			Set<GloballyUniqueTokenTuple> overlapSet,
			StallSiteNode stallSiteNode, LiveInNode liveInNode) {

		Set<SESEEffectsKey> liveInWriteEffectSet = liveInNode
				.getWriteEffectsSet();

		if (liveInWriteEffectSet != null) {
			for (Iterator iterator = liveInWriteEffectSet.iterator(); iterator
					.hasNext();) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) iterator
						.next();
				String hrnUniqueID = seseEffectsKey.getHRNUniqueId();

				if (containsTokenTuple(overlapSet, hrnUniqueID)) {
					return ConflictEdge.COARSE_GRAIN_EDGE;
				}
			}
		}

		return ConflictEdge.FINE_GRAIN_EDGE;
	}

	private int decideConflictEdgeType(
			Set<GloballyUniqueTokenTuple> overlapSet, LiveInNode liveInNodeA,
			LiveInNode liveInNodeB) {

		Set<SESEEffectsKey> liveInWriteEffectSetA = liveInNodeA
				.getWriteEffectsSet();

		if (liveInWriteEffectSetA != null) {
			for (Iterator iterator = liveInWriteEffectSetA.iterator(); iterator
					.hasNext();) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) iterator
						.next();
				String hrnUniqueID = seseEffectsKey.getHRNUniqueId();

				if (containsTokenTuple(overlapSet, hrnUniqueID)) {
					return ConflictEdge.COARSE_GRAIN_EDGE;
				}
			}
		}

		Set<SESEEffectsKey> liveInWriteEffectSetB = liveInNodeB
				.getWriteEffectsSet();

		if (liveInWriteEffectSetB != null) {
			for (Iterator iterator = liveInWriteEffectSetB.iterator(); iterator
					.hasNext();) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) iterator
						.next();
				String hrnUniqueID = seseEffectsKey.getHRNUniqueId();

				if (containsTokenTuple(overlapSet, hrnUniqueID)) {
					return ConflictEdge.COARSE_GRAIN_EDGE;
				}
			}
		}

		return 0;
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

	public void addLiveInNode(TempDescriptor td, FlatSESEEnterNode fsen,
			Set<SESEEffectsKey> readEffectsSet,
			Set<SESEEffectsKey> writeEffectsSet, Set<Set> reachabilitySet) {

		String liveinNodeID = td + "_" + fsen.getIdentifier();

		LiveInNode liveInNode = (LiveInNode) id2cn.get(liveinNodeID);
		if (liveInNode != null) {
			liveInNode.addReadEffectsSet(readEffectsSet);
			liveInNode.addWriteEffectsSet(writeEffectsSet);
			liveInNode.addReachabilitySet(reachabilitySet);
			id2cn.put(liveinNodeID, liveInNode);
		} else {
			LiveInNode newNode = new LiveInNode(liveinNodeID, td,
					readEffectsSet, writeEffectsSet, reachabilitySet);
			id2cn.put(liveinNodeID, newNode);
		}

	}

	public void addConflictEdge(int type, ConflictNode nodeU, ConflictNode nodeV) {

		if (!nodeU.isConflictConnectedTo(nodeV)) {
			ConflictEdge newEdge = new ConflictEdge(nodeU, nodeV, type);
			nodeU.addEdge(newEdge);
			nodeV.addEdge(newEdge);
		}

	}

	public void addWriteConflictEdge(StallSiteNode stallNode,
			LiveInNode liveInNode) {
		ConflictEdge newEdge = new ConflictEdge(stallNode, liveInNode,
				ConflictEdge.WRITE_CONFLICT);
		stallNode.addEdge(newEdge);
		liveInNode.addEdge(newEdge);
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

	public void writeGraph(String graphName) throws java.io.IOException {

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

			// if (node.getID().startsWith("___dst")
			// || node.getID().startsWith("___srctmp")
			// || node.getID().startsWith("___neverused")
			// || node.getID().startsWith("___temp")) {
			//				
			// continue;
			// }

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

				// String uID=u.getID();
				// String vID=v.getID();
				// if (uID.startsWith("___dst")
				// || uID.startsWith("___srctmp")
				// || uID.startsWith("___neverused")
				// || uID.startsWith("___temp")
				// || vID.startsWith("___dst")
				// || vID.startsWith("___srctmp")
				// || vID.startsWith("___neverused")
				// || vID.startsWith("___temp")) {
				// continue;
				// }

				if (conflictEdge.getType() == ConflictEdge.WRITE_CONFLICT) {
					if (!addedSet.contains(conflictEdge)) {
						bw.write(" " + u.getID() + "--" + v.getID()
								+ "[label=\""
								+ conflictEdge.toGraphEdgeString()
								+ "\",decorate];\n");
						addedSet.add(conflictEdge);
					}
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

	public static final int WRITE_CONFLICT = 0;
	public static final int FINE_GRAIN_EDGE = 1;
	public static final int COARSE_GRAIN_EDGE = 2;

	public ConflictEdge(ConflictNode u, ConflictNode v, int type) {
		this.u = u;
		this.v = v;
		this.type = type;
	}

	public String toGraphEdgeString() {
		if (type == WRITE_CONFLICT) {
			return "W_CONFLICT";
		} else if (type == FINE_GRAIN_EDGE) {
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